"""
协作节点处理器 - 支持多智能体协作的 DSL v2 节点
"""
import asyncio
import json
from typing import Any, Dict, List, Optional, TYPE_CHECKING
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler

if TYPE_CHECKING:
    from app.engine.executor import GraphExecutor


class PlannerNodeHandler(BaseNodeHandler):
    """规划节点 - 负责任务分解和计划生成"""

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}
        intent = node_data.get("intent", context.get("intent", ""))
        task_type = node_data.get("taskType", "general")
        execution_mode = node_data.get("executionMode", "serial")  # serial or parallel

        # 根据任务类型生成计划
        subtasks = self._decompose_task(intent, task_type, execution_mode, context)

        # 存储到上下文中供后续使用
        context["__planner_subtasks"] = subtasks
        context["__planner_completed_subtasks"] = []  # Track completed subtask IDs
        context["__planner_execution_mode"] = execution_mode

        # Pre-execute: 立即标记所有没有依赖的子任务为就绪
        ready_subtasks = [st for st in subtasks if not st.get("dependsOn")]
        context["__planner_ready_subtasks"] = [st["id"] for st in ready_subtasks]

        return NodeExecutionOutput(
            outputs={
                "plan": subtasks,
                "currentIndex": 0,
                "total": len(subtasks),
                "executionMode": execution_mode,
                "readySubtasks": [st["id"] for st in ready_subtasks]
            },
            selected_handle="next"
        )

    def _decompose_task(self, intent: str, task_type: str, execution_mode: str, context: Dict[str, Any]) -> List[Dict[str, Any]]:
        """根据任务类型分解任务，支持串行和并行协作"""
        # 模板变量：可以用 inputs 中的值
        input_vars = context.get("inputs", {})

        if task_type in ["analysis", "research"]:
            # 串行：收集 -> 分析 -> 总结
            return [
                {
                    "id": "1",
                    "description": "收集信息",
                    "role": "collector",
                    "dependsOn": [],
                    "inputData": {"intent": intent, "query": "{{inputs.query}}" if input_vars.get("query") else intent},
                    "promptTemplate": "请收集与以下主题相关的信息：{intent}"
                },
                {
                    "id": "2",
                    "description": "分析数据",
                    "role": "analyst",
                    "dependsOn": ["1"],
                    "inputData": {"intent": intent, "collectedData": "{{task_1_result}}"},
                    "promptTemplate": "基于收集的信息：{collectedData}，请进行分析。"
                },
                {
                    "id": "3",
                    "description": "总结结论",
                    "role": "reviewer",
                    "dependsOn": ["2"],
                    "inputData": {"intent": intent, "analysisResult": "{{task_2_result}}"},
                    "promptTemplate": "基于分析结果：{analysisResult}，请给出最终结论和建议。"
                }
            ]
        elif task_type in ["generation", "coding"]:
            return [
                {
                    "id": "1",
                    "description": "制定计划",
                    "role": "planner",
                    "dependsOn": [],
                    "inputData": {"intent": intent},
                    "promptTemplate": "请为以下任务制定执行计划：{intent}"
                },
                {
                    "id": "2",
                    "description": "执行生成",
                    "role": "generator",
                    "dependsOn": ["1"],
                    "inputData": {"intent": intent, "plan": "{{task_1_result}}"},
                    "promptTemplate": "根据计划：{plan}，请执行任务：{intent}"
                },
                {
                    "id": "3",
                    "description": "审查结果",
                    "role": "reviewer",
                    "dependsOn": ["2"],
                    "inputData": {"intent": intent, "generatedResult": "{{task_2_result}}"},
                    "promptTemplate": "请审查生成的结果：{generatedResult}"
                }
            ]
        elif task_type == "testing":
            return [
                {"id": "1", "description": "执行测试", "role": "tester", "dependsOn": [], "inputData": {"intent": intent}, "promptTemplate": "请执行测试：{intent}"},
                {"id": "2", "description": "分析结果", "role": "analyst", "dependsOn": ["1"], "inputData": {"testResult": "{{task_1_result}}"}, "promptTemplate": "分析测试结果：{testResult}"},
                {"id": "3", "description": "修复问题", "role": "developer", "dependsOn": ["2"], "inputData": {"analysis": "{{task_2_result}}"}, "promptTemplate": "根据分析修复问题：{analysis}"},
                {"id": "4", "description": "验证修复", "role": "reviewer", "dependsOn": ["3"], "inputData": {"fix": "{{task_3_result}}"}, "promptTemplate": "验证修复结果：{fix}"}
            ]
        else:
            # 默认计划
            return [
                {"id": "1", "description": "理解需求", "role": "analyst", "dependsOn": [], "inputData": {"intent": intent}, "promptTemplate": "请理解以下需求：{intent}"},
                {"id": "2", "description": "执行任务", "role": "executor", "dependsOn": ["1"], "inputData": {"understanding": "{{task_1_result}}"}, "promptTemplate": "基于需求理解：{understanding}，请执行任务。"},
                {"id": "3", "description": "审查结果", "role": "reviewer", "dependsOn": ["2"], "inputData": {"executionResult": "{{task_2_result}}"}, "promptTemplate": "请审查执行结果：{executionResult}"}
            ]


class DelegateNodeHandler(BaseNodeHandler):
    """委托节点 - 将任务分派给指定角色的智能体，支持串行协作"""

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)
        self._llm_handler = None

    @property
    def llm_handler(self):
        """延迟加载 LLM handler"""
        if self._llm_handler is None:
            from app.engine.handlers.llm import RealLLMNodeHandler
            self._llm_handler = RealLLMNodeHandler()
        return self._llm_handler

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        subtasks = context.get("__planner_subtasks", [])
        completed_subtasks = context.get("__planner_completed_subtasks", [])
        execution_mode = context.get("__planner_execution_mode", "serial")

        # 查找下一个可以执行的子任务（依赖已满足）
        current_task = None
        for st in subtasks:
            st_id = st["id"]
            if st_id in completed_subtasks:
                continue  # 已完成，跳过

            # 检查依赖是否都已完成
            depends_on = st.get("dependsOn", [])
            all_deps_completed = all(dep_id in completed_subtasks for dep_id in depends_on)

            if all_deps_completed:
                current_task = st
                break

        if current_task is None:
            # 所有子任务已完成
            return NodeExecutionOutput(
                outputs={"status": "all_tasks_completed", "completed": completed_subtasks},
                selected_handle="complete"
            )

        # 执行当前子任务
        task_id = current_task["id"]
        role = current_task.get("role", "specialist")
        prompt_template = current_task.get("promptTemplate", "请执行：{description}")
        input_data = current_task.get("inputData", {})

        # 解析 input_data 中的模板变量
        resolved_inputs = self._resolve_input_data(input_data, context)

        # 构造实际 prompt
        description = current_task.get("description", "")
        prompt = prompt_template.format(**resolved_inputs) if resolved_inputs else description

        # 调用 LLM 执行
        try:
            result = await self._execute_delegate(role, prompt, node, context)
        except Exception as e:
            result = f"[ERROR] {str(e)}"

        # 标记完成并存储结果
        completed_subtasks.append(task_id)
        context["__planner_completed_subtasks"] = completed_subtasks
        context[f"task_{task_id}_result"] = result

        # 判断是否还有未完成的任务
        remaining = [st for st in subtasks if st["id"] not in completed_subtasks]
        has_more = len(remaining) > 0

        return NodeExecutionOutput(
            outputs={
                "taskId": task_id,
                "role": role,
                "description": description,
                "result": result,
                "completedSubtasks": list(completed_subtasks),
                "remainingCount": len(remaining),
                "hasMore": has_more
            },
            selected_handle="continue" if has_more else "complete"
        )

    def _resolve_input_data(self, input_data: Dict[str, Any], context: Dict[str, Any]) -> Dict[str, str]:
        """解析 input_data 中的模板变量，引用之前任务的结果"""
        import re
        resolved = {}

        for key, value in input_data.items():
            if isinstance(value, str):
                # 支持 {{task_X_result}} 格式的模板引用
                pattern = r'\{\{task_(\d+)_result\}\}'
                match = re.search(pattern, value)
                if match:
                    task_num = match.group(1)
                    referenced_result = context.get(f"task_{task_num}_result", value)
                    resolved[key] = referenced_result
                else:
                    # 支持简单 {variable} 格式
                    try:
                        resolved[key] = value.format(**context.get("inputs", {}))
                    except (KeyError, TypeError):
                        resolved[key] = value
            else:
                resolved[key] = value

        return resolved

    async def _execute_delegate(self, role: str, prompt: str, node: Node, context: Dict[str, Any]) -> str:
        """委托智能体执行任务，支持三种模式：
        1. agentId - 调用后端 Agent API
        2. workflowId - 调用后端 Workflow API
        3. 默认 - 调用本地 LLM
        """
        node_data = node.data or {}

        # 优先使用节点配置的 agentId/workflowId
        agent_id = node_data.get("agentId")
        workflow_id = node_data.get("workflowId")
        trace_id = context.get("_trace_id")

        # 1. 调用后端 Agent
        if agent_id:
            return await self._call_backend_agent(agent_id, prompt, trace_id)

        # 2. 调用后端 Workflow
        if workflow_id:
            return await self._call_backend_workflow(workflow_id, prompt, trace_id)

        # 3. 默认：调用本地 LLM
        return await self._call_local_llm(role, prompt, node)

    async def _call_backend_agent(self, agent_id: str, message: str, trace_id: Optional[str] = None) -> str:
        """调用后端 Agent API"""
        import httpx
        from app.core.config import settings

        backend_url = settings.ORIN_BACKEND_URL
        url = f"{backend_url}/api/v1/agent/{agent_id}/chat"

        headers = {"Content-Type": "application/json"}
        if trace_id:
            headers["X-Trace-Id"] = trace_id

        payload = {"message": message}
        if trace_id:
            payload["traceId"] = trace_id

        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                response = await client.post(url, json=payload, headers=headers)
                response.raise_for_status()

                result = response.json()
                # 解析响应 - 支持多种格式
                if isinstance(result, dict):
                    return result.get("content") or result.get("text") or result.get("response") or str(result)
                return str(result)

        except httpx.HTTPStatusError as e:
            return f"[Agent API Error {e.response.status_code}]: {e.response.text}"
        except httpx.RequestError as e:
            return f"[Agent API Error]: {str(e)}"
        except Exception as e:
            return f"[Agent Error]: {str(e)}"

    async def _call_backend_workflow(self, workflow_id: str, inputs: str, trace_id: Optional[str] = None) -> str:
        """调用后端 Workflow API"""
        import httpx
        from app.core.config import settings

        backend_url = settings.ORIN_BACKEND_URL
        url = f"{backend_url}/api/v1/workflow/{workflow_id}/execute"

        headers = {"Content-Type": "application/json"}
        if trace_id:
            headers["X-Trace-Id"] = trace_id

        payload = {"inputs": {"query": inputs}}
        if trace_id:
            payload["traceId"] = trace_id

        try:
            async with httpx.AsyncClient(timeout=120.0) as client:
                response = await client.post(url, json=payload, headers=headers)
                response.raise_for_status()

                result = response.json()
                if isinstance(result, dict):
                    return result.get("content") or result.get("output") or result.get("result") or str(result)
                return str(result)

        except httpx.HTTPStatusError as e:
            return f"[Workflow API Error {e.response.status_code}]: {e.response.text}"
        except httpx.RequestError as e:
            return f"[Workflow API Error]: {str(e)}"
        except Exception as e:
            return f"[Workflow Error]: {str(e)}"

    async def _call_local_llm(self, role: str, prompt: str, node: Node) -> str:
        """调用本地 LLM"""
        from app.models.workflow import Node

        delegate_node = Node(
            id=f"delegate_{node.id}_{role}",
            type="llm",
            data={
                "prompt": prompt,
                "model": node.data.get("model") if node.data else None,
                "temperature": node.data.get("temperature", 0.7) if node.data else 0.7,
                "api_key": node.data.get("api_key") if node.data else None,
                "base_url": node.data.get("base_url") if node.data else None,
            }
        )

        # 使用 LLM handler 执行
        llm_output = await self.llm_handler.run(delegate_node, {})

        if llm_output and llm_output.outputs:
            return llm_output.outputs.get("text", str(llm_output.outputs))
        return f"[{role.upper()}] completed"


class ParallelForkNodeHandler(BaseNodeHandler):
    """并行分支节点 - 启动多个并行子任务"""

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)
        self._llm_handler = None

    @property
    def llm_handler(self):
        """延迟加载 LLM handler"""
        if self._llm_handler is None:
            from app.engine.handlers.llm import RealLLMNodeHandler
            self._llm_handler = RealLLMNodeHandler(executor=self.executor)
        return self._llm_handler

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        # 支持两种模式：
        # 1. branches 数组：显式定义每个分支
        # 2. subtasks 引用：从 planner 获取子任务列表并行执行
        branches = node_data.get("branches", [])
        subtasks = node_data.get("subtasks") or context.get("__planner_subtasks", [])
        max_parallel = node_data.get("maxParallel", 5)

        # 如果没有显式 branches，但从 planner 有子任务，则并行执行所有无依赖的子任务
        if not branches and subtasks:
            execution_mode = context.get("__planner_execution_mode", "serial")
            if execution_mode == "parallel":
                # 筛选出没有依赖或依赖已完成的子任务
                completed = context.get("__planner_completed_subtasks", [])
                ready_subtasks = [
                    st for st in subtasks
                    if st["id"] not in completed
                    and all(dep in completed for dep in st.get("dependsOn", []))
                ]
                branches = ready_subtasks[:max_parallel]

        # 限制并行数
        branches = branches[:max_parallel] if branches else []

        if not branches:
            return NodeExecutionOutput(
                outputs={"status": "no_branches", "branches": {}, "count": 0},
                selected_handle="merge"
            )

        # 并行执行所有分支
        tasks = [self._execute_branch(branch, context, node) for branch in branches]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        # 汇总结果
        branch_results = {}
        branch_errors = {}
        for i, branch in enumerate(branches):
            branch_id = branch.get("id", f"branch_{i}")
            result = results[i]
            if isinstance(result, Exception):
                branch_errors[branch_id] = str(result)
                branch_results[branch_id] = None
            else:
                branch_results[branch_id] = result

        context["__parallel_results"] = branch_results
        context["__parallel_branch_ids"] = list(branch_results.keys())

        return NodeExecutionOutput(
            outputs={
                "branches": branch_results,
                "errors": branch_errors,
                "count": len(branches),
                "completedCount": len([r for r in branch_results.values() if r is not None]),
                # Include in outputs so downstream nodes (e.g., Consensus) can access via node_outputs
                "__parallel_results": branch_results,
                "__parallel_branch_ids": list(branch_results.keys())
            },
            selected_handle="merge"
        )

    async def _execute_branch(self, branch: Dict[str, Any], context: Dict[str, Any], parent_node: Node) -> Any:
        """执行单个分支，支持 agent/workflow 调用"""
        branch_type = branch.get("type", "llm")
        branch_id = branch.get("id", "unknown")
        trace_id = context.get("_trace_id")

        try:
            # 优先检查是否指定了 agentId 或 workflowId
            agent_id = branch.get("agentId")
            workflow_id = branch.get("workflowId")

            # 获取 prompt/description
            prompt_template = branch.get("promptTemplate") or branch.get("prompt") or branch.get("description", "")
            input_data = branch.get("inputData", {})
            resolved_inputs = self._resolve_template(prompt_template, input_data, context)
            prompt = prompt_template.format(**resolved_inputs) if resolved_inputs else prompt_template

            # 1. 调用后端 Agent
            if agent_id:
                return await self._call_backend_agent(agent_id, prompt, trace_id)

            # 2. 调用后端 Workflow
            if workflow_id:
                return await self._call_backend_workflow(workflow_id, prompt, trace_id)

            # 3. 本地 LLM 调用
            if branch_type == "llm":
                llm_node = Node(
                    id=f"parallel_branch_{branch_id}",
                    type="llm",
                    data={
                        "prompt": prompt,
                        "model": branch.get("model") or parent_node.data.get("model") if parent_node.data else None,
                        "temperature": branch.get("temperature", 0.7),
                        "api_key": branch.get("api_key") or parent_node.data.get("api_key") if parent_node.data else None,
                    }
                )

                llm_output = await self.llm_handler.run(llm_node, context)
                return llm_output.outputs.get("text", str(llm_output.outputs)) if llm_output.outputs else None

            elif branch_type == "tool":
                # 工具调用分支
                tool_name = branch.get("tool", "unknown")
                return f"Tool {tool_name} executed"

            else:
                # 默认：返回描述
                return branch.get("description", f"Branch {branch_id} done")

        except Exception as e:
            return f"[ERROR in branch {branch_id}]: {str(e)}"

    async def _call_backend_agent(self, agent_id: str, message: str, trace_id: Optional[str] = None) -> str:
        """调用后端 Agent API"""
        import httpx
        from app.core.config import settings

        backend_url = settings.ORIN_BACKEND_URL
        url = f"{backend_url}/api/v1/agent/{agent_id}/chat"

        headers = {"Content-Type": "application/json"}
        if trace_id:
            headers["X-Trace-Id"] = trace_id

        payload = {"message": message}
        if trace_id:
            payload["traceId"] = trace_id

        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                response = await client.post(url, json=payload, headers=headers)
                response.raise_for_status()

                result = response.json()
                if isinstance(result, dict):
                    return result.get("content") or result.get("text") or result.get("response") or str(result)
                return str(result)

        except httpx.HTTPStatusError as e:
            return f"[Agent API Error {e.response.status_code}]: {e.response.text}"
        except httpx.RequestError as e:
            return f"[Agent API Error]: {str(e)}"
        except Exception as e:
            return f"[Agent Error]: {str(e)}"

    async def _call_backend_workflow(self, workflow_id: str, inputs: str, trace_id: Optional[str] = None) -> str:
        """调用后端 Workflow API"""
        import httpx
        from app.core.config import settings

        backend_url = settings.ORIN_BACKEND_URL
        url = f"{backend_url}/api/v1/workflow/{workflow_id}/execute"

        headers = {"Content-Type": "application/json"}
        if trace_id:
            headers["X-Trace-Id"] = trace_id

        payload = {"inputs": {"query": inputs}}
        if trace_id:
            payload["traceId"] = trace_id

        try:
            async with httpx.AsyncClient(timeout=120.0) as client:
                response = await client.post(url, json=payload, headers=headers)
                response.raise_for_status()

                result = response.json()
                if isinstance(result, dict):
                    return result.get("content") or result.get("output") or result.get("result") or str(result)
                return str(result)

        except httpx.HTTPStatusError as e:
            return f"[Workflow API Error {e.response.status_code}]: {e.response.text}"
        except httpx.RequestError as e:
            return f"[Workflow API Error]: {str(e)}"
        except Exception as e:
            return f"[Workflow Error]: {str(e)}"

    def _resolve_template(self, template: str, input_data: Dict[str, Any], context: Dict[str, Any]) -> Dict[str, str]:
        """解析模板变量"""
        import re
        resolved = {}

        # 合并 input_data 和 context 中的变量
        all_vars = {**context.get("inputs", {}), **input_data}

        # 处理 {{task_X_result}} 引用
        for key, value in all_vars.items():
            if isinstance(value, str):
                pattern = r'\{\{task_(\d+)_result\}\}'
                match = re.search(pattern, value)
                if match:
                    task_num = match.group(1)
                    all_vars[key] = context.get(f"task_{task_num}_result", value)

        return all_vars


class ConsensusNodeHandler(BaseNodeHandler):
    """共识节点 - 收集多方意见并达成共识"""

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)
        self._llm_handler = None

    @property
    def llm_handler(self):
        if self._llm_handler is None:
            from app.engine.handlers.llm import RealLLMNodeHandler
            self._llm_handler = RealLLMNodeHandler(executor=self.executor)
        return self._llm_handler

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        strategy = node_data.get("strategy", "synthesis")  # majority, unanimous, synthesis, weighted
        threshold = node_data.get("threshold", 0.5)
        # 支持多个策略依次尝试，格式: ["synthesis", "majority", "unanimous"]
        fallback_strategies = node_data.get("fallbackStrategies", [])

        # 获取并行执行的结果
        parallel_results = context.get("__parallel_results", {})
        branch_ids = context.get("__parallel_branch_ids", list(parallel_results.keys()))

        if not parallel_results:
            return NodeExecutionOutput(
                outputs={"status": "no_results", "consensus": None},
                selected_handle="fallback"
            )

        # 过滤掉 None 结果
        valid_results = {k: v for k, v in parallel_results.items() if v is not None}

        if not valid_results:
            return NodeExecutionOutput(
                outputs={"status": "all_failed", "consensus": None},
                selected_handle="disagree"
            )

        # 尝试主策略
        all_strategies = [strategy] + fallback_strategies if fallback_strategies else [strategy]
        consensus = None
        used_strategy = None
        strategy_results = {}

        for strat in all_strategies:
            result = await self._reach_consensus(valid_results, strat, threshold, node, context)
            strategy_results[strat] = result
            if result is not None:
                consensus = result
                used_strategy = strat
                break

        context["__consensus_result"] = consensus
        context["__consensus_strategy"] = used_strategy

        has_consensus = consensus is not None

        return NodeExecutionOutput(
            outputs={
                "consensus": consensus,
                "strategy": used_strategy,
                "allStrategyResults": strategy_results,  # 记录每个策略的结果
                "inputs": parallel_results,
                "validCount": len(valid_results),
                "totalCount": len(parallel_results),
                "agreed": has_consensus
            },
            selected_handle="agreed" if has_consensus else "disagree"
        )

    async def _reach_consensus(self, results: Dict[str, Any], strategy: str, threshold: float, node: Node, context: Dict[str, Any]) -> Optional[Any]:
        """达成共识"""
        if strategy == "majority":
            return self._majority_consensus(results, threshold)
        elif strategy == "unanimous":
            return self._unanimous_consensus(results)
        elif strategy == "synthesis":
            return await self._synthesis_consensus(results, node, context)
        elif strategy == "weighted":
            return self._weighted_consensus(results, node)
        else:
            # 默认返回第一个
            return list(results.values())[0] if results else None

    def _majority_consensus(self, results: Dict[str, Any], threshold: float) -> Optional[Any]:
        """多数投票共识"""
        from collections import Counter

        # 将结果转为字符串以便比较
        values = [str(v) for v in results.values()]
        counts = Counter(values)

        total = len(values)
        most_common_value, count = counts.most_common(1)[0]

        # 检查是否超过阈值
        if count / total >= threshold:
            # 返回原始值（非字符串版本）
            for v in results.values():
                if str(v) == most_common_value:
                    return v
        return None

    def _unanimous_consensus(self, results: Dict[str, Any]) -> Optional[Any]:
        """全体一致共识"""
        unique_values = set(str(v) for v in results.values())
        if len(unique_values) == 1:
            return list(results.values())[0]
        return None

    async def _synthesis_consensus(self, results: Dict[str, Any], node: Node, context: Dict[str, Any]) -> Optional[Any]:
        """LLM 综合共识 - 用 LLM 综合多个结果"""
        if len(results) < 2:
            return list(results.values())[0] if results else None

        # 构建综合 prompt - 结构化且支持多种结果类型
        branch_entries = []
        for branch_id, result in results.items():
            result_type = type(result).__name__
            if isinstance(result, dict):
                result_repr = f"```json\n{json.dumps(result, ensure_ascii=False, indent=2)}\n```"
            elif isinstance(result, list):
                result_repr = f"```json\n{json.dumps(result, ensure_ascii=False, indent=2)}\n```"
            else:
                result_repr = str(result)
            branch_entries.append(f"## {branch_id} ({result_type}):\n{result_repr}")

        synthesis_prompt = f"""你是一个共识综合专家。你的任务是将多个并行执行分支的结果综合成一个统一的高质量答案。

## 指导原则：
1. 仔细分析每个分支的结果，识别共同点和差异
2. 融合各结果中最准确、最完整的部分
3. 识别并解决冲突的信息
4. 生成一个连贯、一致的综合答案

## 分支结果：
{chr(10).join(branch_entries)}

## 输出要求：
请生成一个综合性的答案，要求：
1. 清晰指出各分支结果的主要观点
2. 说明综合的依据和推理过程
3. 最终给出一个融合了各方优点的统一答案

如果某些分支结果明显错误或质量较低，可以在综合时降低其权重。
"""

        # 调用 LLM 综合
        llm_node = Node(
            id=f"consensus_synthesis",
            type="llm",
            data={
                "prompt": synthesis_prompt,
                "model": node.data.get("model") if node.data else None,
                "temperature": 0.3,  # 低温度保持一致性
            }
        )

        try:
            llm_output = await self.llm_handler.run(llm_node, context)
            if llm_output and llm_output.outputs:
                return llm_output.outputs.get("text")
        except Exception:
            pass

        # 如果 LLM 综合失败，回退到多数投票
        return self._majority_consensus(results, 0.5)

    def _weighted_consensus(self, results: Dict[str, Any], node: Node) -> Optional[Any]:
        """加权投票共识 - 支持权重优先级和加权评分两种模式"""
        # 从节点配置获取权重
        node_data = node.data or {}
        weights = node_data.get("branchWeights", {})
        weight_mode = node_data.get("weightMode", "priority")  # priority 或 score

        if not weights:
            # 无权重配置，返回第一个
            return list(results.values())[0] if results else None

        if weight_mode == "priority":
            # 优先级模式：直接返回最高权重的结果
            scored_results = []
            for branch_id, result in results.items():
                weight = weights.get(branch_id, 1.0)
                scored_results.append((weight, result))
            scored_results.sort(key=lambda x: x[0], reverse=True)
            return scored_results[0][1] if scored_results else None

        elif weight_mode == "score":
            # 加权评分模式：考虑结果的文本相似性和权重综合评分
            # 对所有结果两两比较，计算与每个结果一致的其他结果的加权得分
            result_items = list(results.items())
            total_scores = []

            for i, (bid_i, result_i) in enumerate(result_items):
                weight_i = weights.get(bid_i, 1.0)
                score = 0.0

                for j, (bid_j, result_j) in enumerate(result_items):
                    if i == j:
                        continue
                    weight_j = weights.get(bid_j, 1.0)

                    # 计算相似度（基于字符串相似度）
                    similarity = self._calculate_similarity(str(result_i), str(result_j))
                    # 加权相似度得分
                    score += similarity * weight_j

                # 加上自身权重
                score += weight_i
                total_scores.append((score, result_i))

            total_scores.sort(key=lambda x: x[0], reverse=True)
            return total_scores[0][1] if total_scores else None

        else:
            # 默认返回第一个
            return list(results.values())[0] if results else None

    def _calculate_similarity(self, str1: str, str2: str) -> float:
        """计算两个字符串的相似度 (Jaccard 系数)"""
        if not str1 or not str2:
            return 0.0

        # 基于字符级别的 Jaccard 相似度
        set1 = set(str1.lower())
        set2 = set(str2.lower())

        intersection = len(set1 & set2)
        union = len(set1 | set2)

        if union == 0:
            return 0.0
        return intersection / union


class CriticNodeHandler(BaseNodeHandler):
    """批评节点 - 对结果进行审查和批评"""

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)
        self._llm_handler = None

    @property
    def llm_handler(self):
        if self._llm_handler is None:
            from app.engine.handlers.llm import RealLLMNodeHandler
            self._llm_handler = RealLLMNodeHandler(executor=self.executor)
        return self._llm_handler

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        review_criteria = node_data.get("criteria", ["accuracy", "completeness", "relevance"])
        # 获取要审查的内容来源
        source_key = node_data.get("source", "context")  # context, planner, parallel, consensus
        min_score = node_data.get("minScore", 0.7)  # 最低通过分数

        # 根据 source 获取要审查的内容
        content_to_review = self._get_content_to_review(source_key, context)

        if content_to_review is None:
            return NodeExecutionOutput(
                outputs={
                    "status": "no_content",
                    "approved": False,
                    "needs_revision": True,
                    "issues": ["没有找到需要审查的内容"],
                    "score": 0.0,
                    "criteria_scores": {}
                },
                selected_handle="retry"
            )

        # 使用 LLM 进行真实审查
        criticism = await self._llm_review(content_to_review, review_criteria, node, context)

        # 检查是否需要修订
        needs_revision = (
            criticism.get("needs_revision", False) or
            criticism.get("score", 0.0) < min_score
        )

        context["__criticism"] = criticism

        return NodeExecutionOutput(
            outputs=criticism,
            selected_handle="retry" if needs_revision else "approve"
        )

    def _get_content_to_review(self, source: str, context: Dict[str, Any]) -> Optional[str]:
        """根据来源获取需要审查的内容"""
        if source == "context":
            # 从上下文获取最新的结果
            if "__planner_subtasks" in context:
                subtasks = context["__planner_subtasks"]
                if subtasks:
                    latest = subtasks[-1]
                    return latest.get("description", str(latest))
            if "__consensus_result" in context:
                return str(context["__consensus_result"])
            return None

        elif source == "planner":
            subtasks = context.get("__planner_subtasks", [])
            if subtasks:
                return subtasks[-1].get("description", str(subtasks[-1]))
            return None

        elif source == "parallel":
            results = context.get("__parallel_results", {})
            if results:
                # 合并所有并行结果
                return "\n".join(f"{k}: {v}" for k, v in results.items() if v)
            return None

        elif source == "consensus":
            consensus = context.get("__consensus_result")
            return str(consensus) if consensus else None

        return None

    async def _llm_review(self, content: str, criteria: List[str], node: Node, context: Dict[str, Any]) -> Dict[str, Any]:
        """使用 LLM 进行真实审查"""
        node_data = node.data or {}

        criteria_str = ", ".join(criteria)

        review_prompt = f"""你是一个严格的审查专家。请对以下内容进行深入审查。

## 审查标准：
{criteria_str}

## 待审查内容：
{content}

## 审查要求：
1. 严格按照给定标准进行审查
2. 指出内容中存在的具体问题（如果有）
3. 对每个标准给出 0-1 的评分
4. 给出总体评分
5. 判断是否需要修订（如果有任何标准低于 0.6 或存在严重问题，则需要修订）

## 输出格式（JSON）：
{{
    "approved": true/false,
    "needs_revision": true/false,
    "issues": ["问题1", "问题2", ...],  // 如果没有问题则为空列表
    "score": 总体评分 (0-1),
    "criteria_scores": {{
        "{criteria[0] if criteria else 'criterion'}": 评分,
        ...
    }},
    "reasoning": "审查理由简述"
}}
"""

        llm_node = Node(
            id=f"critic_review",
            type="llm",
            data={
                "prompt": review_prompt,
                "model": node_data.get("model"),
                "temperature": 0.2,  # 低温度保持一致性
            }
        )

        try:
            llm_output = await self.llm_handler.run(llm_node, context)
            if llm_output and llm_output.outputs:
                text_output = llm_output.outputs.get("text", "")
                # 尝试解析 JSON
                import re
                json_match = re.search(r'\{[^{}]*"approved"[^{}]*\}', text_output, re.DOTALL)
                if json_match:
                    import json
                    try:
                        result = json.loads(json_match.group(0))
                        # 确保有所有必要字段
                        return {
                            "approved": result.get("approved", True),
                            "needs_revision": result.get("needs_revision", False),
                            "issues": result.get("issues", []),
                            "score": result.get("score", 0.5),
                            "criteria_scores": result.get("criteria_scores", {}),
                            "reasoning": result.get("reasoning", "")
                        }
                    except json.JSONDecodeError:
                        pass
                # 如果解析失败，返回原始文本
                return {
                    "approved": True,
                    "needs_revision": False,
                    "issues": [],
                    "score": 0.5,
                    "criteria_scores": {c: 0.5 for c in criteria},
                    "reasoning": text_output[:500] if text_output else ""
                }
        except Exception as e:
            pass

        # LLM 审查失败时的回退
        return {
            "approved": True,
            "needs_revision": False,
            "issues": [],
            "score": 0.5,
            "criteria_scores": {c: 0.5 for c in criteria},
            "reasoning": "LLM 审查失败，默认通过"
        }


class MemoryReadNodeHandler(BaseNodeHandler):
    """记忆读取节点 - 从共享记忆存储中读取数据"""

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)
        from app.core.shared_memory import shared_memory
        self._store = shared_memory

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        key = node_data.get("key", "")
        default = node_data.get("default", None)
        namespace = node_data.get("namespace", "orin:memory")
        source_key = node_data.get("sourceKey")  # 可选：从 context 中读取值作为 key

        # 如果指定了 sourceKey，则从 context 中获取实际的 key
        if source_key:
            key = context.get(source_key, key)

        # 从共享存储读取
        value = self._store.get(key, default=default, namespace=namespace)

        # 也写入 context 以保持向后兼容
        context[f"__memory_{key}"] = value

        return NodeExecutionOutput(
            outputs={
                "key": key,
                "value": value,
                "found": value is not None,
                "namespace": namespace
            },
            selected_handle="found" if value is not None else "not_found"
        )


class MemoryWriteNodeHandler(BaseNodeHandler):
    """记忆写入节点 - 将数据写入共享记忆存储"""

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)
        from app.core.shared_memory import shared_memory
        self._store = shared_memory

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        key = node_data.get("key", "")
        value = node_data.get("value")
        merge = node_data.get("merge", False)
        ttl = node_data.get("ttl")  # 过期时间（秒）
        namespace = node_data.get("namespace", "orin:memory")
        source_key = node_data.get("sourceKey")  # 可选：从 context 中读取值作为 key

        # 如果指定了 sourceKey，则从 context 中获取实际的 key
        if source_key:
            key = context.get(source_key, key)

        # 也支持从 context 中获取 value
        if isinstance(value, str) and value.startswith("{{") and value.endswith("}}"):
            # 模板引用格式
            context_key = value[2:-2]
            value = context.get(context_key, value)

        # 写入共享存储
        stored = self._store.set(key, value, ttl=ttl, namespace=namespace, merge=merge)

        # 也写入 context 以保持向后兼容
        context[f"__memory_{key}"] = value

        return NodeExecutionOutput(
            outputs={
                "key": key,
                "stored": stored,
                "value": value,
                "namespace": namespace,
                "ttl": ttl
            },
            selected_handle="done"
        )


class EventEmitNodeHandler(BaseNodeHandler):
    """事件发射节点 - 发送协作事件"""

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        event_type = node_data.get("eventType", "custom")
        event_data = node_data.get("eventData", {})

        # 记录事件
        event = {
            "type": event_type,
            "data": event_data,
            "timestamp": context.get("__execution_start_time")
        }

        # 存储到上下文中（实际应该发送到事件总线）
        context.setdefault("__events", []).append(event)

        return NodeExecutionOutput(
            outputs={
                "event": event,
                "emitted": True
            },
            selected_handle="done"
        )


class EventListenNodeHandler(BaseNodeHandler):
    """事件监听节点 - 等待特定事件"""

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        event_type = node_data.get("eventType", "")
        timeout = node_data.get("timeout", 30)

        # 检查是否有匹配的事件
        events = context.get("__events", [])
        matching_events = [e for e in events if e.get("type") == event_type]

        if matching_events:
            return NodeExecutionOutput(
                outputs={
                    "event": matching_events[-1],
                    "received": True
                },
                selected_handle="triggered"
            )
        else:
            return NodeExecutionOutput(
                outputs={
                    "eventType": event_type,
                    "received": False
                },
                selected_handle="timeout"
            )


class RetryPolicyNodeHandler(BaseNodeHandler):
    """重试策略节点 - 实现协作级重试机制"""

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        max_retries = node_data.get("maxRetries", 3)
        retry_strategy = node_data.get("strategy", "exponential")

        # 获取当前重试次数
        current_retry = context.get("__retry_count", 0)

        if current_retry < max_retries:
            # 计算延迟
            delay = self._calculate_delay(current_retry, retry_strategy)

            context["__retry_count"] = current_retry + 1
            context["__retry_delay"] = delay

            return NodeExecutionOutput(
                outputs={
                    "retry": True,
                    "attempt": current_retry + 1,
                    "maxRetries": max_retries,
                    "delay": delay
                },
                selected_handle="retry"
            )
        else:
            return NodeExecutionOutput(
                outputs={
                    "retry": False,
                    "reason": "max_retries_exceeded",
                    "attempts": current_retry
                },
                selected_handle="exhausted"
            )

    def _calculate_delay(self, attempt: int, strategy: str) -> float:
        """计算重试延迟"""
        if strategy == "exponential":
            return min(2 ** attempt, 30)
        elif strategy == "linear":
            return min(attempt * 2, 30)
        else:
            return 1.0


# 注册所有协作处理器
COLLABORATION_HANDLERS = {
    "planner": PlannerNodeHandler,
    "delegate": DelegateNodeHandler,
    "parallel_fork": ParallelForkNodeHandler,
    "consensus": ConsensusNodeHandler,
    "critic": CriticNodeHandler,
    "memory_read": MemoryReadNodeHandler,
    "memory_write": MemoryWriteNodeHandler,
    "event_emit": EventEmitNodeHandler,
    "event_listen": EventListenNodeHandler,
    "retry_policy": RetryPolicyNodeHandler,
}