import re
import time
import asyncio
from typing import Any, Dict, List, Optional, TYPE_CHECKING
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler
from app.engine.handlers.llm import RealLLMNodeHandler

if TYPE_CHECKING:
    from app.engine.executor import GraphExecutor

class IfElseNodeHandler(BaseNodeHandler):
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Evaluates conditions and returns either 'if' or 'else' handle.
        """
        # conditions: list of {variable, operator, value}
        conditions = node.data.get("conditions", [])
        logical_operator = node.data.get("logical_operator", "and") # and / or
        
        def evaluate_condition(cond):
            var_path = cond.get("variable")
            op = cond.get("operator")
            expected = cond.get("value")
            
            # Resolve variable value from context
            val = self._resolve_variable(var_path, context)
            
            if op == "contains":
                return str(expected) in str(val)
            elif op == "not_contains":
                return str(expected) not in str(val)
            elif op == "equals":
                return str(val) == str(expected)
            elif op == "not_equals":
                return str(val) != str(expected)
            elif op == "is_empty":
                return not val
            elif op == "is_not_empty":
                return bool(val)
            # Add more operators as needed
            return False

        results = [evaluate_condition(c) for c in conditions]
        
        is_true = False
        if logical_operator == "or":
            is_true = any(results) if results else False
        else:
            is_true = all(results) if results else True # Default true if no conditions? Or false? Dify usually requires one.

        selected = "if" if is_true else "else"
        
        return NodeExecutionOutput(
            outputs={"result": is_true, "selected_branch": selected},
            selected_handle=selected
        )

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        if not path: return None
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr

class QuestionClassifierNodeHandler(RealLLMNodeHandler):
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Uses LLM to classify a question into defined categories.
        """
        query_var = node.data.get("query_variable", "inputs.query")
        classes = node.data.get("classes", []) # List of {id, name}
        
        if not classes:
            return NodeExecutionOutput(outputs={"error": "No classes defined"}, selected_handle=None)
            
        query_val = self._resolve_variable(query_var, context) or ""
        
        class_str = "\n".join([f"- {c['id']}: {c.get('name', c['id'])}" for c in classes])
        
        system_prompt = f"You are a question classifier. Categorize the user input into one of the following classes:\n{class_str}\n\nOnly output the class ID."
        
        # We override the run logic of RealLLMNodeHandler slightly or just call its client
        # For simplicity, let's just make a call
        try:
            response = await self.client.chat.completions.create(
                model=node.data.get("model", "gpt-3.5-turbo"),
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": str(query_val)}
                ],
                temperature=0
            )
            selected_id = response.choices[0].message.content.strip()
            
            # Validate selected_id
            valid_ids = [c["id"] for c in classes]
            if selected_id not in valid_ids:
                # Fallback to first if not found? Or None.
                selected_id = valid_ids[0] if valid_ids else None
                
            return NodeExecutionOutput(
                outputs={"class": selected_id},
                selected_handle=selected_id
            )
        except Exception as e:
            raise RuntimeError(f"Question Classifier Error: {str(e)}")

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr

class VariableAggregatorNodeHandler(BaseNodeHandler):
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        """
        Aggregates multiple variables into one output.
        """
        variables = node.data.get("variables", []) # List of variable paths
        output_name = node.data.get("output_variable", "aggregated_value")
        
        # Pick the first non-null value? Or a list?
        # Aggregator usually picks the first one that is present in the context.
        result = None
        for path in variables:
            val = self._resolve_variable(path, context)
            if val is not None:
                result = val
                break
        
        return NodeExecutionOutput(outputs={output_name: result})

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr

class IterationNodeHandler(BaseNodeHandler):
    """
    迭代节点 - 对列表变量进行迭代处理

    节点配置:
        iterator_variable: 要迭代的列表变量路径 (如 "inputs.items")
        max_concurrency: 最大并发数 (默认1，串行迭代)
        item_variable_name: 每个迭代项在上下文中的变量名 (默认 "iteration_item")
        output_variable_name: 输出变量名 (默认 "iteration_results")
        iterate_method: 迭代方式 "subgraph" | "llm" | "transform" (默认 "subgraph")

    处理逻辑:
        1. 从上下文解析迭代列表
        2. 如果有 body 节点（通过边定义），执行子图
        3. 否则使用 transform 或 LLM 模式
        4. 聚合所有结果返回
    """

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        # 解析配置
        iterator_var = node_data.get("iterator_variable", "")
        max_concurrency = node_data.get("max_concurrency", 1)
        item_var_name = node_data.get("item_variable_name", "iteration_item")
        output_var_name = node_data.get("output_variable_name", "iteration_results")
        iterate_method = node_data.get("iterate_method", "subgraph")

        # 解析迭代列表
        if not iterator_var:
            return NodeExecutionOutput(
                outputs={"error": "iterator_variable is required", "status": "failed"},
                selected_handle=None
            )

        items = self._resolve_variable(iterator_var, context)
        if not isinstance(items, list):
            if items is not None:
                return NodeExecutionOutput(
                    outputs={"error": f"iterator_variable must be a list, got {type(items).__name__}", "status": "failed"},
                    selected_handle=None
                )
            items = []

        if not items:
            return NodeExecutionOutput(
                outputs={"results": [], "count": 0, "status": "completed"},
                selected_handle="completed"
            )

        # 执行迭代
        results = []
        errors = []
        max_iterations = node_data.get("max_iterations", len(items))
        items_to_process = items[:max_iterations]

        # 获取 DSL 和 body 节点
        dsl = context.get("_dsl")

        # 首先检查是否有 body 节点（通过 sourceHandle="body" 的边连接）
        body_node_ids = []
        if dsl and self.executor:
            body_node_ids = self.executor.get_body_nodes(dsl, node.id, handle="body")

        if body_node_ids and iterate_method == "subgraph":
            # 子图模式：执行 body 节点子图
            results, errors = await self._iterate_with_subgraph(
                items_to_process, item_var_name, node_data, context, dsl, body_node_ids, max_concurrency
            )
        elif iterate_method == "llm":
            # LLM 迭代模式: 对每个元素调用 LLM 处理
            results, errors = await self._iterate_with_llm(
                items_to_process, item_var_name, node_data, context, max_concurrency
            )
        else:
            # 转换模式: 简单遍历处理
            results, errors = await self._iterate_transform(
                items_to_process, item_var_name, node_data, context
            )

        # 聚合结果
        output_var = node_data.get("output_variable", "result")
        aggregated = {
            output_var: results,
            f"{output_var}_errors": errors,
            f"{output_var}_count": len(results),
            f"{output_var}_error_count": len(errors),
            "status": "completed" if not errors else "completed_with_errors"
        }

        selected_handle = "completed" if len(errors) == 0 else "partial"
        return NodeExecutionOutput(outputs=aggregated, selected_handle=selected_handle)

    async def _iterate_with_subgraph(
        self,
        items: List[Any],
        item_var_name: str,
        node_data: Dict[str, Any],
        context: Dict[str, Any],
        dsl,
        body_node_ids: List[str],
        max_concurrency: int
    ) -> tuple:
        """
        子图迭代模式 - 对每个元素执行 body 子图

        Args:
            items: 要迭代的项列表
            item_var_name: 迭代项变量名
            node_data: 节点配置
            context: 执行上下文
            dsl: 工作流 DSL
            body_node_ids: body 节点 ID 列表
            max_concurrency: 最大并发数
        """
        results = []
        errors = []

        # 创建信号量控制并发
        semaphore = asyncio.Semaphore(max_concurrency)

        async def process_item(index: int, item: Any):
            async with semaphore:
                try:
                    # 构建当前迭代项的上下文
                    iter_context = {
                        **context,
                        item_var_name: item,
                        "iteration_index": index,
                        "iteration_item": item,  # 兼容两种变量名
                    }

                    # 调用 executor 执行子图
                    if self.executor and dsl:
                        sub_results = await self.executor.execute_subgraph(
                            dsl=dsl,
                            node_ids=body_node_ids,
                            initial_context=iter_context
                        )
                        return {
                            "index": index,
                            "input": item,
                            "output": sub_results,
                            "success": True
                        }
                    else:
                        return {
                            "index": index,
                            "input": item,
                            "error": "Executor or DSL not available",
                            "success": False
                        }
                except Exception as e:
                    return {"index": index, "input": item, "error": str(e), "success": False}

        # 并发执行所有迭代项
        tasks = [process_item(i, item) for i, item in enumerate(items)]
        task_results = await asyncio.gather(*tasks, return_exceptions=True)

        for r in task_results:
            if isinstance(r, Exception):
                errors.append({"error": str(r)})
            elif isinstance(r, dict):
                if r.get("success"):
                    results.append(r)
                else:
                    errors.append({"index": r.get("index"), "error": r.get("error")})

        return results, errors

    async def _iterate_transform(
        self,
        items: List[Any],
        item_var_name: str,
        node_data: Dict[str, Any],
        context: Dict[str, Any]
    ) -> tuple:
        """同步转换模式 - 直接处理每个元素"""
        results = []
        errors = []

        for i, item in enumerate(items):
            try:
                # 将当前项放入上下文
                ctx = {**context, item_var_name: item, "iteration_index": i}

                # 应用转换规则
                transform_type = node_data.get("transform_type", "pass_through")
                if transform_type == "pass_through":
                    processed = item
                elif transform_type == "uppercase":
                    processed = str(item).upper()
                elif transform_type == "lowercase":
                    processed = str(item).lower()
                elif transform_type == "length":
                    processed = len(item) if hasattr(item, '__len__') else 1
                elif transform_type == "to_string":
                    processed = str(item)
                else:
                    processed = item

                results.append({
                    "index": i,
                    "input": item,
                    "output": processed,
                    "success": True
                })
            except Exception as e:
                errors.append({"index": i, "input": item, "error": str(e)})

        return results, errors

    async def _iterate_with_llm(
        self,
        items: List[Any],
        item_var_name: str,
        node_data: Dict[str, Any],
        context: Dict[str, Any],
        max_concurrency: int
    ) -> tuple:
        """LLM 迭代模式 - 对每个元素调用 LLM 处理"""
        results = []
        errors = []

        # 获取 LLM 配置
        model = node_data.get("model", "gpt-3.5-turbo")
        system_prompt = node_data.get("system_prompt", "Process the input and return a result.")
        user_template = node_data.get("user_template", "{{" + item_var_name + "}}")

        # 创建信号量控制并发
        semaphore = asyncio.Semaphore(max_concurrency)

        async def process_item(index: int, item: Any):
            async with semaphore:
                try:
                    # 构建消息
                    user_content = user_template.replace("{{" + item_var_name + "}}", str(item))
                    messages = [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_content}
                    ]

                    # 调用 LLM (复用 LLM handler 的客户端)
                    client = self._get_llm_client()
                    if not client:
                        raise RuntimeError("LLM client not available")

                    response = await asyncio.wait_for(
                        client.chat.completions.create(model=model, messages=messages, temperature=0.7),
                        timeout=node_data.get("timeout", 60)
                    )
                    output = response.choices[0].message.content

                    return {
                        "index": index,
                        "input": item,
                        "output": output,
                        "success": True
                    }
                except asyncio.TimeoutError:
                    return {"index": index, "input": item, "error": "LLM call timeout", "success": False}
                except Exception as e:
                    return {"index": index, "input": item, "error": str(e), "success": False}

        # 并发执行
        tasks = [process_item(i, item) for i, item in enumerate(items)]
        task_results = await asyncio.gather(*tasks, return_exceptions=True)

        for r in task_results:
            if isinstance(r, Exception):
                errors.append({"error": str(r)})
            elif isinstance(r, dict):
                if r.get("success"):
                    results.append(r)
                else:
                    errors.append({"index": r.get("index"), "error": r.get("error")})

        return results, errors

    def _get_llm_client(self):
        """获取 LLM 客户端"""
        try:
            # 优先使用已注入的 executor 中的 handler
            if self.executor and "llm" in self.executor.handlers:
                return self.executor.handlers["llm"].client
            # 否则创建新的 handler
            from app.engine.handlers.llm import RealLLMNodeHandler
            handler = RealLLMNodeHandler()
            return handler.client
        except Exception:
            return None

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        if not path:
            return None
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            elif isinstance(curr, list) and p.isdigit():
                idx = int(p)
                curr = curr[idx] if 0 <= idx < len(curr) else None
            else:
                return None
        return curr


class LoopNodeHandler(BaseNodeHandler):
    """
    循环节点 - 基于条件执行循环

    节点配置:
        loop_mode: 循环模式 "while" | "until" | "count" | "subgraph"
        condition_variable: 条件变量路径 (用于 while/until 模式)
        max_iterations: 最大迭代次数 (所有模式)
        max_duration: 最大执行时间(秒)
        counter_variable: 计数器变量名 (默认 "loop_counter")
        output_variable_name: 输出变量名 (默认 "loop_results")

    while 模式: 当 condition_variable 为 true 时继续循环
    until 模式: 当 condition_variable 为 true 时停止循环
    count 模式: 执行固定次数 (max_iterations)
    subgraph 模式: 执行 body 子图直到条件满足或达到最大次数
    """

    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        node_data = node.data or {}

        # 解析配置
        loop_mode = node_data.get("loop_mode", "count")
        condition_var = node_data.get("condition_variable", "")
        max_iterations = node_data.get("max_iterations", 10)
        max_duration = node_data.get("max_duration", 300)  # 5分钟
        counter_var = node_data.get("counter_variable", "loop_counter")
        output_var_name = node_data.get("output_variable_name", "loop_results")

        # 获取起始时间
        start_time = time.time()

        # 初始化计数器
        counter = 0
        results = []
        errors = []

        if loop_mode == "count":
            # 计数模式: 执行固定次数
            for i in range(min(max_iterations, 100)):  # 限制最多100次
                if time.time() - start_time > max_duration:
                    errors.append({"iteration": i, "error": "max_duration exceeded"})
                    break

                counter = i
                result = await self._execute_loop_body(node_data, context, counter, counter_var, node.id)
                if result.get("error"):
                    errors.append({"iteration": i, "error": result["error"]})
                    if node_data.get("stop_on_error", False):
                        break
                else:
                    results.append(result)

        elif loop_mode == "while":
            # while 模式: 条件为真时继续
            while counter < min(max_iterations, 100):
                if time.time() - start_time > max_duration:
                    errors.append({"iteration": counter, "error": "max_duration exceeded"})
                    break

                # 检查条件
                condition = self._resolve_variable(condition_var, context)
                if not condition:
                    break

                counter += 1
                result = await self._execute_loop_body(node_data, context, counter, counter_var, node.id)
                if result.get("error"):
                    errors.append({"iteration": counter, "error": result["error"]})
                    if node_data.get("stop_on_error", False):
                        break
                else:
                    results.append(result)

        elif loop_mode == "until":
            # until 模式: 条件为真时停止
            while counter < min(max_iterations, 100):
                if time.time() - start_time > max_duration:
                    errors.append({"iteration": counter, "error": "max_duration exceeded"})
                    break

                counter += 1
                result = await self._execute_loop_body(node_data, context, counter, counter_var, node.id)
                if result.get("error"):
                    errors.append({"iteration": counter, "error": result["error"]})
                    if node_data.get("stop_on_error", False):
                        break
                else:
                    results.append(result)

                # 检查停止条件
                condition = self._resolve_variable(condition_var, context)
                if condition:
                    break

        else:
            return NodeExecutionOutput(
                outputs={"error": f"Unknown loop_mode: {loop_mode}", "status": "failed"},
                selected_handle=None
            )

        # 聚合结果
        output_var = node_data.get("output_variable", "result")
        aggregated = {
            output_var: results,
            f"{output_var}_errors": errors,
            f"{output_var}_iterations": counter,
            f"{output_var}_complete": len(errors) == 0 or not node_data.get("stop_on_error", False),
            "status": "completed"
        }

        selected_handle = "completed" if len(errors) == 0 else "partial"
        return NodeExecutionOutput(outputs=aggregated, selected_handle=selected_handle)

    async def _execute_loop_body(
        self,
        node_data: Dict[str, Any],
        context: Dict[str, Any],
        counter: int,
        counter_var: str,
        node_id: str
    ) -> Dict[str, Any]:
        """执行循环体"""
        try:
            # 构建带计数器的上下文
            ctx = {**context, counter_var: counter, "loop_counter": counter}

            # 获取 DSL 和 body 节点
            dsl = context.get("_dsl")
            body_node_ids = []
            if dsl and self.executor:
                body_node_ids = self.executor.get_body_nodes(dsl, node_id, handle="body")

            # 循环体类型: "subgraph" | "llm" | "code" | "noop"
            body_type = node_data.get("body_type", "noop")

            if body_type == "subgraph" and body_node_ids:
                # 子图模式：执行 body 节点子图
                return await self._execute_subgraph_body(node_data, ctx, dsl, body_node_ids)
            elif body_type == "llm":
                return await self._execute_llm_body(node_data, ctx)
            elif body_type == "code":
                return await self._execute_code_body(node_data, ctx)
            else:
                # noop 模式: 直接返回成功
                return {
                    "counter": counter,
                    "output": f"Iteration {counter} completed",
                    "success": True
                }

        except Exception as e:
            return {"error": str(e), "counter": counter, "success": False}

    async def _execute_llm_body(self, node_data: Dict[str, Any], context: Dict[str, Any]) -> Dict[str, Any]:
        """通过 LLM 执行循环体"""
        model = node_data.get("model", "gpt-3.5-turbo")
        system_prompt = node_data.get("body_system_prompt", "You are a loop body executor.")
        user_template = node_data.get("body_user_template", "Iteration {{counter}}: Execute this step.")

        user_content = user_template.replace("{{counter}}", str(context.get("loop_counter", 0)))

        try:
            client = self._get_llm_client()
            if not client:
                raise RuntimeError("LLM client not available")

            response = await asyncio.wait_for(
                client.chat.completions.create(
                    model=model,
                    messages=[
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_content}
                    ],
                    temperature=0.7
                ),
                timeout=node_data.get("timeout", 60)
            )

            return {
                "output": response.choices[0].message.content,
                "success": True
            }
        except asyncio.TimeoutError:
            return {"error": "LLM call timeout", "success": False}
        except Exception as e:
            return {"error": str(e), "success": False}

    async def _execute_code_body(self, node_data: Dict[str, Any], context: Dict[str, Any]) -> Dict[str, Any]:
        """通过代码执行循环体"""
        code = node_data.get("body_code", "")
        if not code:
            return {"output": "No code to execute", "success": True}

        try:
            # 安全的代码执行 (仅支持简单的表达式)
            # 注意: 生产环境应该使用更安全的沙箱
            local_vars = {k: v for k, v in context.items() if not k.startswith("_")}
            result = eval(code, {"__builtins__": {}}, local_vars)
            return {"output": result, "success": True}
        except Exception as e:
            return {"error": f"Code execution error: {str(e)}", "success": False}

    async def _execute_subgraph_body(
        self,
        node_data: Dict[str, Any],
        context: Dict[str, Any],
        dsl,
        body_node_ids: List[str]
    ) -> Dict[str, Any]:
        """通过子图执行循环体"""
        try:
            if not self.executor or not dsl:
                return {"error": "Executor or DSL not available", "success": False}

            sub_results = await self.executor.execute_subgraph(
                dsl=dsl,
                node_ids=body_node_ids,
                initial_context=context
            )
            return {"output": sub_results, "success": True}
        except Exception as e:
            return {"error": f"Subgraph execution error: {str(e)}", "success": False}

    def _get_llm_client(self):
        """获取 LLM 客户端"""
        try:
            # 优先使用已注入的 executor 中的 handler
            if self.executor and "llm" in self.executor.handlers:
                return self.executor.handlers["llm"].client
            # 否则创建新的 handler
            from app.engine.handlers.llm import RealLLMNodeHandler
            handler = RealLLMNodeHandler()
            return handler.client
        except Exception:
            return None

    def _resolve_variable(self, path: str, context: Dict[str, Any]) -> Any:
        if not path:
            return None
        parts = path.split(".")
        curr = context
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return None
        return curr
