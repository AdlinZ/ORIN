import re
from typing import Any, Dict, Optional, TYPE_CHECKING
from app.models.workflow import Node, NodeExecutionOutput
from app.engine.handlers.base import BaseNodeHandler

if TYPE_CHECKING:
    from app.engine.executor import GraphExecutor

class StartNodeHandler(BaseNodeHandler):
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        # Start node usually just passes initial inputs or does nothing
        return NodeExecutionOutput(outputs=context.get("inputs", {}))

class EndNodeHandler(BaseNodeHandler):
    def __init__(self, executor: Optional["GraphExecutor"] = None):
        super().__init__(executor)

    async def run(self, node: Node, context: Dict[str, Any]) -> NodeExecutionOutput:
        outputs = {}
        for item in _parse_output_mappings((node.data or {}).get("outputs")):
            name = item.get("name")
            value = item.get("value")
            if name:
                outputs[str(name)] = _resolve_expression(value, context)
        return NodeExecutionOutput(outputs=outputs)


_EXPRESSION_RE = re.compile(r"\{\{\s*([a-zA-Z0-9_.-]+)\s*}}")


def _parse_output_mappings(raw: Any) -> list[dict[str, Any]]:
    if isinstance(raw, list):
        mappings = []
        for item in raw:
            if not isinstance(item, dict):
                continue
            name = item.get("name") or item.get("key") or item.get("variable")
            value = item.get("value")
            if value is None:
                value = item.get("expression") or item.get("source") or item.get("sourceExpression")
            if name and value is not None:
                mappings.append({"name": name, "value": value})
        return mappings
    if isinstance(raw, dict):
        return [{"name": key, "value": value} for key, value in raw.items()]
    return []


def _resolve_expression(raw: Any, context: Dict[str, Any]) -> Any:
    if not isinstance(raw, str):
        return raw
    exact = _EXPRESSION_RE.fullmatch(raw.strip())
    if exact:
        resolved = _resolve_path(exact.group(1), context)
        return "" if resolved is None else resolved

    def replace(match: re.Match) -> str:
        resolved = _resolve_path(match.group(1), context)
        return "" if resolved is None else str(resolved)

    return _EXPRESSION_RE.sub(replace, raw)


def _resolve_path(path: str, context: Dict[str, Any]) -> Any:
    current: Any = context
    for part in path.split("."):
        if isinstance(current, dict) and part in current:
            current = current[part]
        else:
            return None
    return current


def resolve_answer_value(node_data: Dict[str, Any], context: Dict[str, Any]) -> Any:
    configured = (
        node_data.get("answer")
        or node_data.get("text")
        or node_data.get("value")
        or node_data.get("template")
        or node_data.get("source")
        or node_data.get("sourceExpression")
    )
    if configured:
        return _resolve_expression(configured, context)
    return _first_readable_value(context)


def _first_readable_value(value: Any) -> Any:
    if isinstance(value, str) and value.strip():
        return value
    if not isinstance(value, dict):
        return None
    for key in ("answer", "final_answer", "finalAnswer", "text", "content", "message", "output", "result", "value", "body"):
        nested = _first_readable_value(value.get(key))
        if nested is not None:
            return nested
    for nested_value in value.values():
        nested = _first_readable_value(nested_value)
        if nested is not None:
            return nested
    return None
