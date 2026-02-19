import asyncio
import time
from typing import List, Dict, Set, Any
from app.models.workflow import WorkflowDSL, ExecutionResult, Node, NodeStatus, WorkflowStatus, NodeTrace
from app.engine.handlers.base import BaseNodeHandler
from app.engine.handlers.standard import StartNodeHandler, EndNodeHandler
# Replace Mock with Real Handler
from app.engine.handlers.llm import RealLLMNodeHandler
from app.core.config import settings

class GraphExecutor:
    def __init__(self):
        # Statless check: handlers should be stateless too
        self.handlers: Dict[str, BaseNodeHandler] = {
            "start": StartNodeHandler(),
            "end": EndNodeHandler(),
            "llm": RealLLMNodeHandler(),
            "agent": RealLLMNodeHandler(),
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

    async def execute(self, dsl: WorkflowDSL, initial_inputs: Dict[str, Any]) -> ExecutionResult:
        """
        Executes the workflow graph using robust parallel implementation.
        """
        # 1. Initialization
        nodes_map = {node.id: node for node in dsl.nodes}
        adj = self._build_adjacency_list(dsl)
        rev_adj = self._build_reverse_adj(dsl)
        in_degree = self._build_in_degree(dsl)
        
        # State:
        # Context stores outputs: NodeID -> OutputDict
        # We COPY inputs to ensure isolation
        context = {"inputs": initial_inputs.copy()}
        node_outputs = {}
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
                
                # Check Upstream Status
                # If ANY upstream failed -> SKIP current node
                # If ALL upstream skipped -> SKIP current node
                upstream_ids = rev_adj.get(node_id, [])
                upstream_failed = any(node_status[uid] == NodeStatus.FAILED for uid in upstream_ids)
                upstream_skipped = upstream_ids and all(node_status[uid] == NodeStatus.SKIPPED for uid in upstream_ids)
                
                if upstream_failed or upstream_skipped:
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
                    try:
                        if not h:
                            raise ValueError(f"No handler for type {n.type}")
                        
                        # Prepare context with upstream outputs
                        exec_ctx = {**ctx, **node_outputs}
                        
                        # Execute with Timeout
                        output = await asyncio.wait_for(h.run(n, exec_ctx), timeout=t_out)
                        
                        end_t = time.time()
                        return NodeTrace(
                            node_id=n.id,
                            status=NodeStatus.COMPLETED,
                            start_time=start_t,
                            end_time=end_t,
                            duration=end_t - start_t,
                            outputs=output
                        )
                    except asyncio.TimeoutError:
                        end_t = time.time()
                        return NodeTrace(
                            node_id=n.id,
                            status=NodeStatus.FAILED,
                            start_time=start_t,
                            end_time=end_t,
                            duration=end_t - start_t,
                            error=f"Execution timed out after {t_out}s"
                        )
                    except Exception as e:
                        end_t = time.time()
                        return NodeTrace(
                            node_id=n.id,
                            status=NodeStatus.FAILED,
                            start_time=start_t,
                            end_time=end_t,
                            duration=end_t - start_t,
                            error=str(e)
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
                
                # Get Result from Task (Exception handled inside task wrapper)
                trace: NodeTrace = await task
                traces.append(trace)
                node_status[node_id] = trace.status
                
                if trace.status == NodeStatus.COMPLETED:
                    if trace.outputs:
                        node_outputs[node_id] = trace.outputs
                
                # Update Neighbors regardless of success/failure
                # (Failure propagation handled in 'Check Upstream Status' logic above)
                for neighbor_id in adj.get(node_id, []):
                    in_degree[neighbor_id] -= 1
                    if in_degree[neighbor_id] == 0:
                        queue.append(neighbor_id)

        # 3. Finalize Result
        has_failure = any(s == NodeStatus.FAILED for s in node_status.values())
        
        # Determine strict workflow status
        final_status = WorkflowStatus.SUCCESS
        if has_failure:
             final_status = WorkflowStatus.PARTIAL
        
        # If any PENDING nodes remain (e.g. cycle or unreachable), technically incomplete
        if any(s == NodeStatus.PENDING for s in node_status.values()):
             final_status = WorkflowStatus.ERROR 
             # Or Partial? Let's say Partial for now unless graph was invalid.

        return ExecutionResult(
            status=final_status,
            outputs=node_outputs,
            trace=traces
        )
