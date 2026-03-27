import asyncio
import time
from typing import List, Dict, Set, Any, Optional
from app.models.workflow import WorkflowDSL, ExecutionResult, Node, NodeStatus, WorkflowStatus, NodeTrace, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler
from app.engine.handlers.standard import StartNodeHandler, EndNodeHandler
# Replace Mock with Real Handler
from app.engine.handlers.llm import RealLLMNodeHandler
from app.engine.handlers.code import CodeNodeHandler
from app.engine.handlers.variable_assigner import VariableAssignerNodeHandler
from app.engine.handlers.logic import IfElseNodeHandler, QuestionClassifierNodeHandler, VariableAggregatorNodeHandler, IterationNodeHandler, LoopNodeHandler
from app.engine.handlers.tools import HTTPRequestNodeHandler, ListOperatorNodeHandler, ToolNodeHandler
from app.engine.handlers.interaction import AnswerNodeHandler
from app.engine.handlers.data_processing import KnowledgeRetrievalNodeHandler, TemplateTransformNodeHandler, ParameterExtractorNodeHandler, DocumentExtractorNodeHandler
from app.engine.handlers.collaboration import (
    PlannerNodeHandler, DelegateNodeHandler, ParallelForkNodeHandler,
    ConsensusNodeHandler, CriticNodeHandler, MemoryReadNodeHandler,
    MemoryWriteNodeHandler, EventEmitNodeHandler, EventListenNodeHandler,
    RetryPolicyNodeHandler
)
from app.core.config import settings
from app.core.tracing import tracing_client, Span

class GraphExecutor:
    def __init__(self):
        # 初始化 handlers，传递 executor 引用以便迭代/循环节点执行子图
        self.handlers: Dict[str, BaseNodeHandler] = {
            "start": StartNodeHandler(),
            "end": EndNodeHandler(),
            "llm": RealLLMNodeHandler(executor=self),
            "agent": RealLLMNodeHandler(executor=self),
            "code": CodeNodeHandler(executor=self),
            "variable_assigner": VariableAssignerNodeHandler(executor=self),
            "if_else": IfElseNodeHandler(executor=self),
            "question_classifier": QuestionClassifierNodeHandler(executor=self),
            "variable_aggregator": VariableAggregatorNodeHandler(executor=self),
            "iteration": IterationNodeHandler(executor=self),
            "loop": LoopNodeHandler(executor=self),
            "http_request": HTTPRequestNodeHandler(executor=self),
            "list_operator": ListOperatorNodeHandler(executor=self),
            "tool": ToolNodeHandler(executor=self),
            "answer": AnswerNodeHandler(executor=self),
            "knowledge_retrieval": KnowledgeRetrievalNodeHandler(executor=self),
            "template_transform": TemplateTransformNodeHandler(executor=self),
            "parameter_extractor": ParameterExtractorNodeHandler(executor=self),
            "document_extractor": DocumentExtractorNodeHandler(executor=self),
            # DSL v2 协作节点
            "planner": PlannerNodeHandler(executor=self),
            "delegate": DelegateNodeHandler(executor=self),
            "parallel_fork": ParallelForkNodeHandler(executor=self),
            "consensus": ConsensusNodeHandler(executor=self),
            "critic": CriticNodeHandler(executor=self),
            "memory_read": MemoryReadNodeHandler(executor=self),
            "memory_write": MemoryWriteNodeHandler(executor=self),
            "event_emit": EventEmitNodeHandler(executor=self),
            "event_listen": EventListenNodeHandler(executor=self),
            "retry_policy": RetryPolicyNodeHandler(executor=self),
        }

    def _build_adjacency_list(self, dsl: WorkflowDSL) -> Dict[str, List[str]]:
        adj = {node.id: [] for node in dsl.nodes}
        for edge in dsl.edges:
            if edge.source in adj:
                adj[edge.source].append(edge.target)
        return adj

    def _build_reverse_adj(self, dsl: WorkflowDSL) -> Dict[str, List[str]]:
        rev_adj = {node.id: [] for node in dsl.nodes}
        for edge in dsl.edges:
            if edge.target in rev_adj:
                rev_adj[edge.target].append(edge.source)
        return rev_adj

    def _build_in_degree(self, dsl: WorkflowDSL) -> Dict[str, int]:
        in_degree = {node.id: 0 for node in dsl.nodes}
        for edge in dsl.edges:
            if edge.target in in_degree:
                in_degree[edge.target] += 1
        return in_degree

    async def execute_subgraph(
        self,
        dsl: WorkflowDSL,
        node_ids: List[str],
        initial_context: Dict[str, Any],
        parent_trace_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        执行子图（用于迭代/循环节点的 body 执行）

        Args:
            dsl: 完整的工作流 DSL
            node_ids: 要执行的节点 ID 列表（按拓扑序排列）
            initial_context: 初始上下文
            parent_trace_id: 父级 trace ID

        Returns:
            子图执行结果，格式为 {node_id: output}
        """
        if not node_ids:
            return {}

        # 构建只包含指定节点的子图
        sub_nodes = [n for n in dsl.nodes if n.id in node_ids]
        sub_node_ids = set(node_ids)

        # 筛选只涉及这些节点的边
        sub_edges = [
            e for e in dsl.edges
            if e.source in sub_node_ids and e.target in sub_node_ids
        ]

        sub_dsl = WorkflowDSL(nodes=sub_nodes, edges=sub_edges, version=dsl.version)

        # 构建反向边映射（用于识别入口节点）
        in_degree = {node.id: 0 for node in sub_nodes}
        for edge in sub_edges:
            if edge.target in in_degree:
                in_degree[edge.target] += 1

        # 找出入口节点（in_degree == 0 的节点）
        entry_nodes = [nid for nid, deg in in_degree.items() if deg == 0]

        if not entry_nodes:
            return {}

        # 如果有多个入口节点，只保留第一个（通常 iteration body 只有一个入口）
        entry_node_id = entry_nodes[0]

        # 按拓扑序执行
        context = initial_context.copy()
        results = {}

        # 拓扑排序执行
        visited = set()
        queue = [entry_node_id]

        while queue:
            node_id = queue.pop(0)
            if node_id in visited:
                continue

            node = next((n for n in sub_nodes if n.id == node_id), None)
            if not node:
                continue

            handler = self.handlers.get(node.type.lower())
            if not handler:
                continue

            # 准备上下文（合并之前节点的输出）
            exec_ctx = {**context, **results}

            # 执行节点
            try:
                timeout = node.data.get("timeout", settings.NODE_DEFAULT_TIMEOUT) if node.data else settings.NODE_DEFAULT_TIMEOUT
                output = await asyncio.wait_for(handler.run(node, exec_ctx), timeout=timeout)
                if output and output.outputs:
                    results[node_id] = output.outputs
                    # 更新上下文以供后续节点使用
                    context[node_id] = output.outputs
            except Exception as e:
                # 子图执行出错，记录错误但继续
                results[node_id] = {"error": str(e), "_node_error": True}

            visited.add(node_id)

            # 找出后续节点（基于边）
            for edge in sub_edges:
                if edge.source == node_id and edge.target not in visited:
                    # 检查目标节点的入度是否都满足
                    target_in_deg = sum(1 for e in sub_edges if e.target == edge.target)
                    target_visited = sum(1 for e in sub_edges if e.target == edge.target and e.source in visited)
                    if target_visited >= target_in_deg:
                        queue.append(edge.target)

        return results

    def get_body_nodes(self, dsl: WorkflowDSL, iteration_node_id: str, handle: str = "body") -> List[str]:
        """
        获取迭代/循环节点的 body 节点 ID 列表

        通过 sourceHandle="body" 的边来确定哪些节点是 body 节点

        Args:
            dsl: 工作流 DSL
            iteration_node_id: 迭代/循环节点的 ID
            handle: 边的手柄名称，默认为 "body"

        Returns:
            body 节点 ID 列表（按拓扑序排列）
        """
        # 找出所有从该节点发出的 body 边
        body_edges = [
            e for e in dsl.edges
            if e.source == iteration_node_id and e.sourceHandle == handle
        ]

        if not body_edges:
            return []

        body_node_ids = set(e.target for e in body_edges)

        # 构建 body 子图的拓扑序
        # 使用 Kahn 算法
        in_degree = {node.id: 0 for node in dsl.nodes if node.id in body_node_ids}
        for edge in dsl.edges:
            if edge.target in body_node_ids and edge.source in body_node_ids:
                in_degree[edge.target] += 1

        result = []
        queue = [nid for nid, deg in in_degree.items() if deg == 0]

        while queue:
            node_id = queue.pop(0)
            result.append(node_id)
            for edge in dsl.edges:
                if edge.source == node_id and edge.target in body_node_ids:
                    in_degree[edge.target] -= 1
                    if in_degree[edge.target] == 0:
                        queue.append(edge.target)

        return result

    # Supported DSL versions
    SUPPORTED_VERSIONS = ["1.0", "1.1"]
    CURRENT_VERSION = "1.0"

    async def execute(self, dsl: WorkflowDSL, initial_inputs: Dict[str, Any], trace_id: Optional[str] = None) -> ExecutionResult:
        """
        Executes the workflow graph using robust parallel implementation with branching support.

        Args:
            dsl: Workflow DSL definition
            initial_inputs: Initial inputs for the workflow
            trace_id: Optional trace ID for distributed tracing
        """
        # DSL Version Check
        dsl_version = getattr(dsl, 'version', None) or "1.0"
        if dsl_version not in self.SUPPORTED_VERSIONS:
            raise ValueError(
                f"Unsupported DSL version: {dsl_version}. "
                f"Supported versions: {', '.join(self.SUPPORTED_VERSIONS)}"
            )

        # Initialize tracing
        if trace_id:
            tracing_client.clear()
            tracing_client.start_trace(trace_id, f"workflow:execute")

        # 1. Initialization
        nodes_map = {node.id: node for node in dsl.nodes}
        adj = self._build_adjacency_list(dsl)
        rev_adj = self._build_reverse_adj(dsl)
        in_degree = self._build_in_degree(dsl)

        # Mapping for easy edge lookup
        edges_by_target = {node.id: [] for node in dsl.nodes}
        for edge in dsl.edges:
            edges_by_target[edge.target].append(edge)

        # State:
        # Context stores outputs: NodeID -> OutputDict
        context = {"inputs": initial_inputs.copy(), "_trace_id": trace_id}
        node_outputs = {}
        node_selected_handles = {}
        node_status: Dict[str, NodeStatus] = {node.id: NodeStatus.PENDING for node in dsl.nodes}
        traces: List[NodeTrace] = []

        # Queue for nodes ready to execute
        queue = [node_id for node_id, deg in in_degree.items() if deg == 0]

        active_tasks: Set[asyncio.Task] = set()
        task_to_node_id = {}

        # 2. Execution Loop
        while queue or active_tasks:
            # Launch ready nodes
            while queue:
                node_id = queue.pop(0)
                node = nodes_map[node_id]
                
                # Check Upstream Status for branching
                upstream_edges = edges_by_target.get(node_id, [])
                
                # A node is skipped if:
                # 1. ALL incoming paths are inactive
                # 2. ANY upstream node failed (Propagate failure)
                
                is_skipped = False
                has_failed_upstream = False
                all_paths_inactive = len(upstream_edges) > 0 # Start with true if there ARE upstreams
                
                for edge in upstream_edges:
                    u_status = node_status[edge.source]
                    u_handle = node_selected_handles.get(edge.source)
                    
                    if u_status == NodeStatus.FAILED:
                        has_failed_upstream = True
                        break
                    
                    # A path is active if source completed AND matches handle
                    if u_status == NodeStatus.COMPLETED:
                        if not edge.sourceHandle or edge.sourceHandle == u_handle:
                            all_paths_inactive = False
                
                if not upstream_edges: # Start node
                    all_paths_inactive = False
                
                if has_failed_upstream or all_paths_inactive:
                    node_status[node_id] = NodeStatus.SKIPPED
                    trace = NodeTrace(
                        node_id=node_id,
                        status=NodeStatus.SKIPPED,
                        start_time=time.time(),
                        end_time=time.time(),
                        duration=0.0
                    )
                    traces.append(trace)
                    
                    # Propagate to downstream
                    for neighbor_id in adj.get(node_id, []):
                        in_degree[neighbor_id] -= 1
                        if in_degree[neighbor_id] == 0:
                            queue.append(neighbor_id)
                    continue

                # Execute Node
                node_status[node_id] = NodeStatus.RUNNING
                handler = self.handlers.get(node.type.lower())
                
                # Determine Timeout
                timeout = node.data.get("timeout", settings.NODE_DEFAULT_TIMEOUT) if node.data else settings.NODE_DEFAULT_TIMEOUT
                
                async def run_node_task(n: Node, ctx: Dict[str, Any], h: BaseNodeHandler, t_out: float):
                    start_t = time.time()

                    # Start tracing span for this node
                    if trace_id:
                        node_type = n.type.lower() if n.type else "unknown"
                        tracing_client.start_span(
                            f"node:{node_type}:{n.id}",
                            metadata={
                                "node_id": n.id,
                                "node_type": node_type,
                                "node_label": n.data.get("label") if n.data else None
                            }
                        )

                    try:
                        if not h:
                            raise ValueError(f"No handler for type {n.type}")

                        # Prepare context with upstream outputs and DSL reference
                        # DSL is needed by iteration/loop nodes to execute their body subgraphs
                        exec_ctx = {**ctx, **node_outputs, "_dsl": dsl}

                        # Execute with Timeout
                        res: NodeExecutionOutput = await asyncio.wait_for(h.run(n, exec_ctx), timeout=t_out)

                        end_t = time.time()

                        # Record success event in tracing
                        if trace_id:
                            tracing_client.record_event(
                                "node_completed",
                                metadata={
                                    "node_id": n.id,
                                    "node_type": n.type,
                                    "duration": end_t - start_t,
                                    "has_output": bool(res.outputs)
                                }
                            )
                            tracing_client.finish_span(status="success")

                        return NodeTrace(
                            node_id=n.id,
                            status=NodeStatus.COMPLETED,
                            start_time=start_t,
                            end_time=end_t,
                            duration=end_t - start_t,
                            outputs=res.outputs,
                            selected_handle=res.selected_handle
                        )
                    except asyncio.TimeoutError:
                        end_t = time.time()

                        # Record timeout error in tracing
                        if trace_id:
                            tracing_client.record_event(
                                "node_timeout",
                                metadata={"node_id": n.id, "timeout": t_out}
                            )
                            tracing_client.finish_span(status="error", error=f"Execution timed out after {t_out}s")

                        return NodeTrace(
                            node_id=n.id,
                            status=NodeStatus.FAILED,
                            start_time=start_t,
                            end_time=end_t,
                            duration=end_t - start_t,
                            error=f"Execution timed out after {t_out}s"
                        )
                    except Exception as e:
                        import traceback
                        end_t = time.time()

                        # Record error in tracing
                        if trace_id:
                            tracing_client.record_event(
                                "node_error",
                                metadata={"node_id": n.id, "error": str(e)}
                            )
                            tracing_client.finish_span(status="error", error=str(e))

                        return NodeTrace(
                            node_id=n.id,
                            status=NodeStatus.FAILED,
                            start_time=start_t,
                            end_time=end_t,
                            duration=end_t - start_t,
                            error=f"{str(e)}\n{traceback.format_exc()}"
                        )

                task = asyncio.create_task(run_node_task(node, context, handler, timeout))
                active_tasks.add(task)
                task_to_node_id[task] = node_id
            
            if not active_tasks:
                break
                
            # Wait for any task completion
            done, pending = await asyncio.wait(active_tasks, return_when=asyncio.FIRST_COMPLETED)
            
            for task in done:
                active_tasks.remove(task)
                node_id = task_to_node_id.pop(task)
                
                # Get Result from Task
                trace: NodeTrace = await task
                traces.append(trace)
                node_status[node_id] = trace.status
                
                if trace.status == NodeStatus.COMPLETED:
                    if trace.outputs:
                        node_outputs[node_id] = trace.outputs
                    if trace.selected_handle:
                        node_selected_handles[node_id] = trace.selected_handle
                
                # Update Neighbors
                for neighbor_id in adj.get(node_id, []):
                    in_degree[neighbor_id] -= 1
                    if in_degree[neighbor_id] == 0:
                        queue.append(neighbor_id)

        # 3. Finalize Result
        has_failure = any(s == NodeStatus.FAILED for s in node_status.values())

        final_status = WorkflowStatus.SUCCESS
        if has_failure:
             final_status = WorkflowStatus.PARTIAL

        if any(s == NodeStatus.PENDING for s in node_status.values()):
             final_status = WorkflowStatus.ERROR

        # Get tracing summary if trace_id was provided
        trace_summary = None
        if trace_id:
            trace_summary = tracing_client.get_trace_summary()
            # Finish the workflow span
            if tracing_client._current_span:
                tracing_client.finish_span(
                    status="success" if final_status == WorkflowStatus.SUCCESS else "error",
                    error="Partial failure" if final_status == WorkflowStatus.PARTIAL else None
                )

        return ExecutionResult(
            status=final_status,
            outputs=node_outputs,
            trace=traces,
            trace_summary=trace_summary
        )
