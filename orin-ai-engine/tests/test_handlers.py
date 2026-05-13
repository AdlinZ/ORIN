"""
F2.2 核心 Handler 最小可运行测试样例

覆盖以下 Handler:
- VariableAssignerNodeHandler
- CodeNodeHandler
- IfElseNodeHandler
- QuestionClassifierNodeHandler (继承自 LLM，需 mock)
- VariableAggregatorNodeHandler
- IterationNodeHandler
- LoopNodeHandler

运行方式：
    cd orin-ai-engine && python -m pytest tests/test_handlers.py -v
"""

import pytest
import asyncio
from unittest.mock import AsyncMock, MagicMock, patch
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.variable_assigner import VariableAssignerNodeHandler
from app.engine.handlers.code import CodeNodeHandler
from app.engine.handlers.logic import (
    IfElseNodeHandler,
    QuestionClassifierNodeHandler,
    VariableAggregatorNodeHandler,
    IterationNodeHandler,
    LoopNodeHandler,
)
from app.engine.handlers.collaboration import ParallelForkNodeHandler
from app.engine.handlers.tools import HTTPRequestNodeHandler
from app.engine.handlers.data_processing import KnowledgeRetrievalNodeHandler


# =============================================================================
# VariableAssignerNodeHandler Tests
# =============================================================================

class TestVariableAssignerNodeHandler:
    """VariableAssignerNodeHandler 最小测试"""

    @pytest.mark.asyncio
    async def test_assign_variable_success(self):
        """正常赋值场景"""
        handler = VariableAssignerNodeHandler()
        node = Node(
            id="var-1",
            type="variable_assigner",
            data={"target_variable": "output.result", "value": "test_value"}
        )
        context = {}

        result = await handler.run(node, context)

        assert result.outputs["assigned_variable"] == "output.result"
        assert result.outputs["value"] == "test_value"
        assert result.outputs["mode"] == "overwrite"

    @pytest.mark.asyncio
    async def test_assign_with_write_modes(self):
        """不同写模式：overwrite / append / merge"""
        handler = VariableAssignerNodeHandler()

        # Test overwrite
        node_overwrite = Node(
            id="var-2",
            type="variable_assigner",
            data={"target_variable": "x", "value": 1, "write_mode": "overwrite"}
        )
        result = await handler.run(node_overwrite, {})
        assert result.outputs["mode"] == "overwrite"

    @pytest.mark.asyncio
    async def test_no_target_variable(self):
        """未定义目标变量时应跳过"""
        handler = VariableAssignerNodeHandler()
        node = Node(id="var-3", type="variable_assigner", data={})

        result = await handler.run(node, {})

        assert result.outputs["status"] == "skipped"
        assert "No target variable" in result.outputs["reason"]


# =============================================================================
# HTTPRequestNodeHandler Tests
# =============================================================================

class TestHTTPRequestNodeHandler:
    """HTTPRequestNodeHandler request construction tests"""

    @pytest.mark.asyncio
    async def test_get_with_url_query_does_not_send_empty_params(self, monkeypatch):
        captured = {}

        class FakeResponse:
            status_code = 200
            text = '{"ok": true}'
            headers = {"content-type": "application/json"}
            is_success = True

            def json(self):
                return {"ok": True}

        class FakeClient:
            def __init__(self, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, exc_type, exc, tb):
                return False

            async def request(self, **kwargs):
                captured.update(kwargs)
                return FakeResponse()

        monkeypatch.setattr("app.engine.handlers.tools.httpx.AsyncClient", FakeClient)

        handler = HTTPRequestNodeHandler()
        node = Node(
            id="http-1",
            type="http_request",
            data={
                "method": "GET",
                "url": "https://example.com/search?q={{ query }}",
                "headers": {"Accept": "application/json"},
                "params": {},
            },
        )

        result = await handler.run(node, {"query": "hello world"})

        assert captured["url"] == "https://example.com/search?q=hello world"
        assert "params" not in captured
        assert "content" not in captured
        assert "json" not in captured
        assert result.outputs["json"] == {"ok": True}


# =============================================================================
# KnowledgeRetrievalNodeHandler Tests
# =============================================================================

class TestKnowledgeRetrievalNodeHandler:
    """KnowledgeRetrievalNodeHandler response parsing tests"""

    @pytest.mark.asyncio
    async def test_parses_backend_results_field(self, monkeypatch):
        class FakeResponse:
            status_code = 200
            text = '{"results":[]}'

            def json(self):
                return {
                    "results": [
                        {"content": "Dify 提供工作流和应用 API。", "score": 0.92, "id": "chunk-1"}
                    ]
                }

        class FakeClient:
            def __init__(self, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, exc_type, exc, tb):
                return False

            async def post(self, *args, **kwargs):
                return FakeResponse()

        monkeypatch.setattr("app.engine.handlers.data_processing.httpx.AsyncClient", FakeClient)

        handler = KnowledgeRetrievalNodeHandler()
        node = Node(
            id="knowledge-1",
            type="knowledge_retrieval",
            data={
                "knowledge_id": "kb-1",
                "query_variable": "inputs.query",
                "top_k": 3,
            },
        )

        result = await handler.run(node, {"inputs": {"query": "Dify 怎么接入"}})

        assert result.outputs["count"] == 1
        assert result.outputs["text"] == "Dify 提供工作流和应用 API。"
        assert result.outputs["result"][0]["doc_id"] == "chunk-1"


# =============================================================================
# CodeNodeHandler Tests
# =============================================================================

class TestCodeNodeHandler:
    """CodeNodeHandler 最小测试"""

    @pytest.mark.asyncio
    async def test_code_execution_success(self):
        """代码正常执行 - 上下文变量直接可用"""
        handler = CodeNodeHandler()
        node = Node(
            id="code-1",
            type="code",
            data={
                "code": "output['result'] = x + y"
            }
        )
        context = {"x": 10, "y": 5}

        result = await handler.run(node, context)

        assert result.outputs["result"] == 15

    @pytest.mark.asyncio
    async def test_code_with_builtins(self):
        """代码中使用内置函数"""
        handler = CodeNodeHandler()
        node = Node(
            id="code-2",
            type="code",
            data={
                "code": "output['length'] = len(items)"
            }
        )
        context = {"items": [1, 2, 3, 4, 5]}

        result = await handler.run(node, context)

        assert result.outputs["length"] == 5

    @pytest.mark.asyncio
    async def test_code_no_code_provided(self):
        """无代码时返回 skipped"""
        handler = CodeNodeHandler()
        node = Node(id="code-3", type="code", data={})

        result = await handler.run(node, {})

        assert result.outputs["result"] == "No code provided"
        assert result.outputs["status"] == "skipped"

    @pytest.mark.asyncio
    async def test_code_syntax_error(self):
        """语法错误时抛出 RuntimeError"""
        handler = CodeNodeHandler()
        node = Node(
            id="code-4",
            type="code",
            data={"code": "output['x'] = 1 +"}  # 语法错误
        )

        with pytest.raises(RuntimeError, match="Python Execution Error"):
            await handler.run(node, {})


# =============================================================================
# IfElseNodeHandler Tests
# =============================================================================

class TestIfElseNodeHandler:
    """IfElseNodeHandler 最小测试"""

    @pytest.mark.asyncio
    async def test_condition_equals_true(self):
        """equals 条件为真时走 if 分支"""
        handler = IfElseNodeHandler()
        node = Node(
            id="if-1",
            type="if_else",
            data={
                "conditions": [{"variable": "x", "operator": "equals", "value": "yes"}],
                "logical_operator": "and"
            }
        )
        context = {"x": "yes"}

        result = await handler.run(node, context)

        assert result.outputs["result"] is True
        assert result.selected_handle == "if"

    @pytest.mark.asyncio
    async def test_condition_equals_false(self):
        """equals 条件为假时走 else 分支"""
        handler = IfElseNodeHandler()
        node = Node(
            id="if-2",
            type="if_else",
            data={
                "conditions": [{"variable": "x", "operator": "equals", "value": "yes"}],
                "logical_operator": "and"
            }
        )
        context = {"x": "no"}

        result = await handler.run(node, context)

        assert result.outputs["result"] is False
        assert result.selected_handle == "else"

    @pytest.mark.asyncio
    async def test_condition_contains(self):
        """contains 运算符"""
        handler = IfElseNodeHandler()
        node = Node(
            id="if-3",
            type="if_else",
            data={
                "conditions": [{"variable": "text", "operator": "contains", "value": "error"}],
            }
        )
        context = {"text": "This is an error message"}

        result = await handler.run(node, context)

        assert result.outputs["result"] is True
        assert result.selected_handle == "if"

    @pytest.mark.asyncio
    async def test_multiple_conditions_and(self):
        """AND 逻辑：全部为真才走 if"""
        handler = IfElseNodeHandler()
        node = Node(
            id="if-4",
            type="if_else",
            data={
                "conditions": [
                    {"variable": "x", "operator": "equals", "value": "a"},
                    {"variable": "y", "operator": "equals", "value": "b"},
                ],
                "logical_operator": "and"
            }
        )
        # 第一个为真，第二个为假
        context = {"x": "a", "y": "c"}

        result = await handler.run(node, context)

        assert result.outputs["result"] is False
        assert result.selected_handle == "else"

    @pytest.mark.asyncio
    async def test_multiple_conditions_or(self):
        """OR 逻辑：任一为真就走 if"""
        handler = IfElseNodeHandler()
        node = Node(
            id="if-5",
            type="if_else",
            data={
                "conditions": [
                    {"variable": "x", "operator": "equals", "value": "a"},
                    {"variable": "y", "operator": "equals", "value": "b"},
                ],
                "logical_operator": "or"
            }
        )
        # 第一个为真，第二个为假
        context = {"x": "a", "y": "c"}

        result = await handler.run(node, context)

        assert result.outputs["result"] is True
        assert result.selected_handle == "if"


# =============================================================================
# QuestionClassifierNodeHandler Tests
# =============================================================================

class TestQuestionClassifierNodeHandler:
    """QuestionClassifierNodeHandler 最小测试"""

    @pytest.mark.asyncio
    async def test_classifier_no_classes(self):
        """未定义分类时返回错误"""
        handler = QuestionClassifierNodeHandler()
        node = Node(
            id="clf-1",
            type="question_classifier",
            data={}
        )

        result = await handler.run(node, {})

        assert "error" in result.outputs
        assert "No classes defined" in result.outputs["error"]

    @pytest.mark.asyncio
    async def test_classifier_mock_llm_call(self):
        """模拟 LLM 分类调用"""
        # Mock LLM client at the AsyncOpenAI level
        mock_response = MagicMock()
        mock_response.choices = [MagicMock(message=MagicMock(content="category_a"))]

        with patch("app.engine.handlers.llm.AsyncOpenAI") as MockClient:
            mock_instance = MockClient.return_value
            mock_instance.chat.completions.create = AsyncMock(return_value=mock_response)

            # Patch settings to provide API key
            with patch("app.engine.handlers.llm.settings") as mock_settings:
                mock_settings.OPENAI_API_KEY = "sk-test"
                mock_settings.OPENAI_BASE_URL = None

                # Create handler after patching settings
                handler = QuestionClassifierNodeHandler()

                node = Node(
                    id="clf-2",
                    type="question_classifier",
                    data={
                        "query_variable": "input",
                        "classes": [
                            {"id": "category_a", "name": "Category A"},
                            {"id": "category_b", "name": "Category B"},
                        ]
                    }
                )

                result = await handler.run(node, {"input": "What is category A?"})

                assert result.outputs["class"] == "category_a"
                assert result.selected_handle == "category_a"


# =============================================================================
# VariableAggregatorNodeHandler Tests
# =============================================================================

class TestVariableAggregatorNodeHandler:
    """VariableAggregatorNodeHandler 最小测试"""

    @pytest.mark.asyncio
    async def test_aggregate_first_non_null(self):
        """取第一个非空值"""
        handler = VariableAggregatorNodeHandler()
        node = Node(
            id="agg-1",
            type="variable_aggregator",
            data={
                "variables": ["a", "b", "c"],
                "output_variable": "result"
            }
        )
        context = {"a": None, "b": "found", "c": "also there"}

        result = await handler.run(node, context)

        assert result.outputs["result"] == "found"

    @pytest.mark.asyncio
    async def test_aggregate_all_null(self):
        """全部为空时返回 None"""
        handler = VariableAggregatorNodeHandler()
        node = Node(
            id="agg-2",
            type="variable_aggregator",
            data={
                "variables": ["x", "y"],
                "output_variable": "result"
            }
        )
        context = {"x": None, "y": None}

        result = await handler.run(node, context)

        assert result.outputs["result"] is None

    @pytest.mark.asyncio
    async def test_aggregate_with_nested_path(self):
        """支持嵌套路径解析"""
        handler = VariableAggregatorNodeHandler()
        node = Node(
            id="agg-3",
            type="variable_aggregator",
            data={
                "variables": ["data.value"],
                "output_variable": "result"
            }
        )
        context = {"data": {"value": "nested_value"}}

        result = await handler.run(node, context)

        assert result.outputs["result"] == "nested_value"


# =============================================================================
# IterationNodeHandler Tests
# =============================================================================

class TestIterationNodeHandler:
    """IterationNodeHandler 最小测试"""

    @pytest.mark.asyncio
    async def test_iteration_empty_list(self):
        """空列表时直接返回空结果"""
        handler = IterationNodeHandler()
        node = Node(
            id="iter-1",
            type="iteration",
            data={
                "iterator_variable": "items",
                "iterate_method": "transform"
            }
        )
        context = {"items": []}

        result = await handler.run(node, context)

        assert result.outputs["count"] == 0
        assert result.outputs["results"] == []
        assert result.selected_handle == "completed"

    @pytest.mark.asyncio
    async def test_iteration_no_iterator_variable(self):
        """未定义 iterator_variable 时报错"""
        handler = IterationNodeHandler()
        node = Node(
            id="iter-2",
            type="iteration",
            data={}
        )

        result = await handler.run(node, {})

        assert result.outputs["status"] == "failed"
        assert "iterator_variable is required" in result.outputs["error"]

    @pytest.mark.asyncio
    async def test_iteration_non_list_error(self):
        """iterator_variable 非列表时报错"""
        handler = IterationNodeHandler()
        node = Node(
            id="iter-3",
            type="iteration",
            data={
                "iterator_variable": "data",
                "iterate_method": "transform"
            }
        )
        context = {"data": "not a list"}

        result = await handler.run(node, context)

        assert result.outputs["status"] == "failed"
        assert "must be a list" in result.outputs["error"]

    @pytest.mark.asyncio
    async def test_iteration_transform_pass_through(self):
        """transform 模式：passthrough"""
        handler = IterationNodeHandler()
        node = Node(
            id="iter-4",
            type="iteration",
            data={
                "iterator_variable": "items",
                "iterate_method": "transform",
                "transform_type": "pass_through",
                "output_variable_name": "result"
            }
        )
        context = {"items": [1, 2, 3]}

        result = await handler.run(node, context)

        assert result.outputs["result_count"] == 3
        assert result.outputs["status"] == "completed"

    @pytest.mark.asyncio
    async def test_iteration_transform_uppercase(self):
        """transform 模式：uppercase"""
        handler = IterationNodeHandler()
        node = Node(
            id="iter-5",
            type="iteration",
            data={
                "iterator_variable": "items",
                "iterate_method": "transform",
                "transform_type": "uppercase",
                "output_variable_name": "result"
            }
        )
        context = {"items": ["a", "b", "c"]}

        result = await handler.run(node, context)

        assert result.outputs["result_count"] == 3
        # 检查第一个结果的 output
        first_result = result.outputs["result"][0]
        assert first_result["output"] == "A"


# =============================================================================
# LoopNodeHandler Tests
# =============================================================================

class TestLoopNodeHandler:
    """LoopNodeHandler 最小测试"""

    @pytest.mark.asyncio
    async def test_loop_count_mode(self):
        """count 模式：执行固定次数"""
        handler = LoopNodeHandler()
        node = Node(
            id="loop-1",
            type="loop",
            data={
                "loop_mode": "count",
                "max_iterations": 3,
                "body_type": "noop",
                "output_variable": "result"
            }
        )
        context = {}

        result = await handler.run(node, context)

        assert result.outputs["result_iterations"] == 2  # counter = last i value (0,1,2 -> counter=2)
        assert result.outputs["status"] == "completed"
        assert result.selected_handle == "completed"

    @pytest.mark.asyncio
    async def test_loop_while_uses_body_output_for_next_condition(self):
        """while 模式：body 输出应更新下一轮条件变量"""
        handler = LoopNodeHandler()
        node = Node(
            id="loop-while",
            type="loop",
            data={
                "loop_mode": "while",
                "condition_variable": "output",
                "max_iterations": 10,
                "body_type": "code",
                "body_code": "loop_counter < 3",
                "output_variable": "result",
            }
        )

        result = await handler.run(node, {"output": True})

        assert result.outputs["result_iterations"] == 3
        assert len(result.outputs["result"]) == 3
        assert result.outputs["result"][-1]["output"] is False

    @pytest.mark.asyncio
    async def test_loop_until_uses_body_output_for_stop_condition(self):
        """until 模式：body 输出应能触发停止条件"""
        handler = LoopNodeHandler()
        node = Node(
            id="loop-until",
            type="loop",
            data={
                "loop_mode": "until",
                "condition_variable": "output",
                "max_iterations": 10,
                "body_type": "code",
                "body_code": "loop_counter >= 3",
                "output_variable": "result",
            }
        )

        result = await handler.run(node, {})

        assert result.outputs["result_iterations"] == 3
        assert len(result.outputs["result"]) == 3
        assert result.outputs["result"][-1]["output"] is True

    @pytest.mark.asyncio
    async def test_loop_unknown_mode(self):
        """未知循环模式时返回错误"""
        handler = LoopNodeHandler()
        node = Node(
            id="loop-2",
            type="loop",
            data={
                "loop_mode": "unknown_mode"
            }
        )

        result = await handler.run(node, {})

        assert result.outputs["status"] == "failed"
        assert "Unknown loop_mode" in result.outputs["error"]


# =============================================================================
# ParallelForkNodeHandler Tests
# =============================================================================

class TestParallelForkNodeHandler:
    """ParallelForkNodeHandler 最小测试"""

    @pytest.mark.asyncio
    async def test_parallel_collects_branch_errors(self):
        """分支异常应进入 errors，而不是伪装成成功字符串"""
        handler = ParallelForkNodeHandler()

        class MockLLMHandler:
            async def run(self, node, context):
                if node.id.endswith("_bad"):
                    raise RuntimeError("branch failed")
                return NodeExecutionOutput(outputs={"text": "ok"})

        handler._llm_handler = MockLLMHandler()
        node = Node(
            id="parallel",
            type="parallel_fork",
            data={
                "branches": [
                    {"id": "good", "type": "llm", "prompt": "good"},
                    {"id": "bad", "type": "llm", "prompt": "bad"},
                ],
                "maxParallel": 5,
            }
        )

        result = await handler.run(node, {})

        assert result.outputs["completedCount"] == 1
        assert result.outputs["branches"]["good"] == "ok"
        assert result.outputs["branches"]["bad"] is None
        assert "branch failed" in result.outputs["errors"]["bad"]


# =============================================================================
# 运行入口
# =============================================================================

if __name__ == "__main__":
    pytest.main([__file__, "-v"])
