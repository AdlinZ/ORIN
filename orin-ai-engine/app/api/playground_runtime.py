from __future__ import annotations

import asyncio
import json
import re
from datetime import datetime, timezone
from typing import Any

import httpx
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from langgraph.graph import END, StateGraph
from pydantic import BaseModel, Field

from app.core.collab_state import poll_branch_result, read_collab_ctx
from app.core.config import settings
from app.engine.task_runtime import TaskRuntime

router = APIRouter(prefix="/api/playground/runtime")


class PlaygroundRunRequest(BaseModel):
    run_id: str
    workflow: dict[str, Any]
    agents: list[dict[str, Any]] = Field(default_factory=list)
    ephemeral_agents: list[dict[str, Any]] = Field(default_factory=list)
    context_messages: list[dict[str, Any]] = Field(default_factory=list)
    user_input: str
    conversation_id: str | None = None


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def text(value: Any, fallback: str = "") -> str:
    if value is None:
        return fallback
    value = str(value).strip()
    return value or fallback


def normalize_context_messages(messages: list[dict[str, Any]]) -> list[dict[str, str]]:
    normalized: list[dict[str, str]] = []
    if not isinstance(messages, list):
        return normalized
    for item in messages[:4]:
        if not isinstance(item, dict):
            continue
        role = text(item.get("role"), "user").lower()
        if role not in {"user", "assistant"}:
            continue
        content = text(item.get("content"), "")
        if not content:
            continue
        normalized.append({
            "role": role,
            "content": content[:600],
        })
    return normalized


def format_context_messages(messages: list[dict[str, str]]) -> str:
    if not messages:
        return ""
    lines = []
    for item in messages:
        role = "用户" if item.get("role") == "user" else "助手"
        lines.append(f"{role}: {text(item.get('content'), '')}")
    return "\n".join(lines)


def contextual_user_input(user_input: str, messages: list[dict[str, str]]) -> str:
    context_text = format_context_messages(messages)
    if not context_text:
        return user_input
    return (
        "当前用户请求：\n"
        f"{user_input}\n\n"
        "同一会话近期上下文（用于解析“这个/上面/继续”等指代；最终回答必须聚焦当前用户请求）：\n"
        f"{context_text}"
    )


def bounded_int(value: Any, fallback: int, minimum: int = 256, maximum: int = 16000) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        parsed = fallback
    return max(minimum, min(maximum, parsed))


def error_text(exc: BaseException | Any, fallback: str = "Runtime execution failed.") -> str:
    if exc is None:
        return fallback
    detail = getattr(exc, "detail", None)
    if detail is not None:
        resolved = text(detail, "")
        if resolved:
            return resolved
    resolved = text(str(exc), "")
    if resolved:
        return resolved
    args = getattr(exc, "args", None)
    if isinstance(args, tuple):
        for arg in args:
            candidate = text(arg, "")
            if candidate:
                return candidate
    return text(exc.__class__.__name__, fallback)


def runtime_timeout(seconds: float, connect_seconds: float = 5.0) -> httpx.Timeout:
    return httpx.Timeout(timeout=seconds, connect=connect_seconds)


async def post_json_with_context(
    *,
    step: str,
    url: str,
    payload: dict[str, Any],
    headers: dict[str, str] | None = None,
    timeout_seconds: float = 20.0,
) -> httpx.Response:
    try:
        async with httpx.AsyncClient(timeout=runtime_timeout(timeout_seconds)) as client:
            return await client.post(url, json=payload, headers=headers)
    except httpx.ReadTimeout as exc:
        raise RuntimeError(f"{step} timeout after {int(timeout_seconds)}s: POST {url}") from exc
    except httpx.ConnectError as exc:
        raise RuntimeError(f"{step} connect failed: POST {url}") from exc
    except httpx.HTTPError as exc:
        raise RuntimeError(f"{step} http error: {error_text(exc)}") from exc


def node(node_id: str, label: str, kind: str, parent_id: str | None = None) -> dict[str, Any]:
    payload: dict[str, Any] = {"id": node_id, "label": label, "kind": kind}
    if parent_id:
        payload["parent_id"] = parent_id
    return payload


def edge(source: str, target: str, label: str | None = None) -> dict[str, Any]:
    payload: dict[str, Any] = {"source": source, "target": target}
    if label:
        payload["label"] = label
    return payload


def event(event_type: str, title: str, detail: str, node_id: str, run_id: str, status: str, **payload: Any) -> dict[str, Any]:
    event_payload = {
        "node_id": node_id,
        "run_id": run_id,
        "status": status,
        "execution_path": "langgraph_mq",
        **payload,
    }
    return {
        "type": event_type,
        "title": title,
        "detail": detail,
        "status": status,
        "at": now(),
        "payload": event_payload,
    }


def agent_id(agent: dict[str, Any], fallback: str) -> str:
    return text(agent.get("id"), fallback)


def agent_name(agent: dict[str, Any], fallback: str = "Agent") -> str:
    return text(agent.get("name"), fallback)


def normalize_ephemeral_agents(agents: list[dict[str, Any]]) -> list[dict[str, Any]]:
    normalized: list[dict[str, Any]] = []
    for item in agents or []:
        if not isinstance(item, dict):
            continue
        current_id = text(item.get("id"), "")
        current_name = text(item.get("name"), "")
        model = text(item.get("model"), "")
        if not current_id.startswith("ephemeral:") or not current_name or not model:
            continue
        normalized.append({
            "id": current_id,
            "name": current_name,
            "description": text(item.get("description"), text(item.get("system_prompt"), ""))[:300],
            "system_prompt": text(item.get("system_prompt"), ""),
            "model": model,
            "role": text(item.get("role"), "SPECIALIST"),
            "max_tokens": bounded_int(item.get("max_tokens"), int(getattr(settings, "PLAYGROUND_AGENT_MAX_TOKENS", 1200))),
            "temperature": item.get("temperature"),
            "planning_slot": bool(item.get("planning_slot") or item.get("planningSlot")),
            "ephemeral": True,
        })
        if len(normalized) >= 4:
            break
    return normalized


def build_graph(workflow: dict[str, Any], agents: list[dict[str, Any]]) -> dict[str, Any]:
    workflow_type = text(workflow.get("type"), "router_specialists")
    finalizer = bool(workflow.get("finalizer_enabled", True))
    if workflow_type == "single_agent_chat":
        return graph_single_agent(agents, finalizer)
    if workflow_type == "planner_executor":
        return graph_planner_executor(agents, finalizer)
    if workflow_type == "supervisor_dynamic":
        return graph_supervisor_dynamic(agents, finalizer)
    if workflow_type == "peer_handoff":
        return graph_peer_handoff(agents, finalizer)
    return graph_router_specialists(agents, finalizer)


def append_final(nodes: list[dict[str, Any]], edges: list[dict[str, Any]], source: str, finalizer: bool) -> None:
    if finalizer:
        nodes.append(node("finalize", "Merge", "merge"))
        edges.append(edge(source, "finalize"))
        nodes.append(node("end", "End", "end"))
        edges.append(edge("finalize", "end"))
    else:
        nodes.append(node("end", "End", "end"))
        edges.append(edge(source, "end"))


def graph_single_agent(agents: list[dict[str, Any]], finalizer: bool) -> dict[str, Any]:
    selected = agents[0] if agents else {"id": "assistant", "name": "Assistant"}
    selected_id = agent_id(selected, "assistant")
    nodes = [node("start", "Start", "start"), node(selected_id, agent_name(selected), "agent")]
    edges = [edge("start", selected_id)]
    append_final(nodes, edges, selected_id, finalizer)
    return {"nodes": nodes, "edges": edges}


def graph_router_specialists(agents: list[dict[str, Any]], finalizer: bool) -> dict[str, Any]:
    nodes = [node("start", "Start", "start"), node("router", "Router", "logic")]
    edges = [edge("start", "router")]
    for agent in agents:
        selected_id = agent_id(agent, "agent")
        nodes.append(node(selected_id, agent_name(agent), "agent"))
        edges.append(edge("router", selected_id, "route"))
        edges.append(edge(selected_id, "finalize" if finalizer else "end"))
    if finalizer:
        nodes.extend([node("finalize", "Merge", "merge"), node("end", "End", "end")])
        edges.append(edge("finalize", "end"))
    else:
        nodes.append(node("end", "End", "end"))
    return {"nodes": nodes, "edges": edges}


def graph_planner_executor(agents: list[dict[str, Any]], finalizer: bool) -> dict[str, Any]:
    nodes = [
        node("start", "Start", "start"),
        node("planner_core", "Planner Core", "logic"),
        node("planner_validator", "Plan Validator", "logic"),
        node("task_dispatcher", "Task Dispatcher", "logic"),
    ]
    edges = [
        edge("start", "planner_core"),
        edge("planner_core", "planner_validator"),
        edge("planner_validator", "task_dispatcher"),
    ]
    for agent in agents:
        selected_id = agent_id(agent, "agent")
        nodes.append(node(selected_id, agent_name(agent), "agent"))
        edges.append(edge("task_dispatcher", selected_id, "dispatch"))
        edges.append(edge(selected_id, "task_dispatcher", "next"))
    append_final(nodes, edges, "task_dispatcher", finalizer)
    return {"nodes": nodes, "edges": edges}


def graph_supervisor_dynamic(agents: list[dict[str, Any]], finalizer: bool) -> dict[str, Any]:
    nodes = [
        node("start", "Start", "start"),
        node("supervisor_intake", "Supervisor Intake", "logic"),
        node("delegation_policy", "Delegation Policy", "logic"),
        node("supervisor_review", "Supervisor Review", "logic"),
    ]
    edges = [edge("start", "supervisor_intake"), edge("supervisor_intake", "delegation_policy")]
    for agent in agents:
        selected_id = agent_id(agent, "agent")
        nodes.append(node(selected_id, agent_name(agent), "agent"))
        edges.append(edge("delegation_policy", selected_id, "delegate"))
        edges.append(edge(selected_id, "supervisor_review", "report"))
    edges.append(edge("supervisor_review", "delegation_policy", "continue"))
    append_final(nodes, edges, "supervisor_review", finalizer)
    return {"nodes": nodes, "edges": edges}


def graph_peer_handoff(agents: list[dict[str, Any]], finalizer: bool) -> dict[str, Any]:
    nodes = [
        node("start", "Start", "start"),
        node("first_owner_router", "First Owner Router", "logic"),
        node("peer_pool", "Peer Pool", "group"),
        node("handoff_decision", "Handoff Decision", "logic"),
    ]
    edges = [edge("start", "first_owner_router"), edge("first_owner_router", "peer_pool", "owner")]
    for agent in agents:
        selected_id = agent_id(agent, "agent")
        nodes.append(node(selected_id, agent_name(agent), "agent", "peer_pool"))
        edges.append(edge("peer_pool", selected_id, "handoff"))
        edges.append(edge(selected_id, "handoff_decision", "decide"))
    edges.append(edge("handoff_decision", "peer_pool", "continue"))
    append_final(nodes, edges, "handoff_decision", finalizer)
    return {"nodes": nodes, "edges": edges}


def select_agent(user_input: str, agents: list[dict[str, Any]]) -> tuple[dict[str, Any], str]:
    if not agents:
        return {"id": "assistant", "name": "Assistant", "description": ""}, "No specialist configured; using default assistant."
    lowered = user_input.lower()
    best = agents[0]
    best_score = -1
    for agent in agents:
        profile = f"{agent.get('name', '')} {agent.get('description', '')} {agent.get('system_prompt', '')}".lower()
        score = sum(1 for token in lowered.split() if len(token) > 2 and token in profile)
        if score > best_score:
            best = agent
            best_score = score
    reason = "Keyword/profile match selected this specialist." if best_score > 0 else "Defaulted to the first available specialist."
    return best, reason


def _split_by_order_markers(text: str) -> list[str]:
    marker_pattern = re.compile(r"(先|然后|再|接着|最后)")
    matches = list(marker_pattern.finditer(text))
    if not matches:
        return [text.strip()]

    prefix = text[: matches[0].start()].strip("，,：: ")
    parts: list[str] = []
    for idx, match in enumerate(matches):
        start = match.end()
        end = matches[idx + 1].start() if idx + 1 < len(matches) else len(text)
        segment = text[start:end].strip("，,；;。.!?！？:： \n\t")
        if not segment:
            continue
        if prefix and any(token in prefix for token in ("协作", "完成", "执行", "请")):
            parts.append(f"{prefix}：{segment}")
        else:
            parts.append(segment)
    return parts or [text.strip()]


def plan_tasks(user_input: str, max_tasks: int = 4) -> list[str]:
    raw_clauses = [item.strip() for item in re.split(r"[。！？!?\n]+", user_input) if item.strip()]
    role_clauses: list[str] = []
    constraint_clauses: list[str] = []
    content_clauses: list[str] = []

    for clause in raw_clauses:
        normalized = clause.strip()
        if normalized.startswith("你是") and len(normalized) <= 40:
            role_clauses.append(normalized)
            continue
        if normalized.startswith(("要求", "请确保", "务必", "并明确", "注意")):
            constraint_clauses.append(normalized)
            continue
        content_clauses.append(normalized)

    tasks: list[str] = []
    for clause in content_clauses or [user_input.strip()]:
        tasks.extend(_split_by_order_markers(clause))

    # Role clauses like "你是总协调" should not be injected into every worker subtask,
    # otherwise all workers respond in the same coordinator persona.
    global_constraints = "；".join([*constraint_clauses]).strip()
    if global_constraints:
        tasks = [f"{task}（约束：{global_constraints}）" for task in tasks]

    cleaned = [task.strip("，,；;。.!?！？ \n\t") for task in tasks if task.strip()]
    return cleaned[:max_tasks] or [user_input.strip()]


def _normalize_subtask_payload(task: dict[str, Any]) -> dict[str, Any]:
    depends_on = task.get("depends_on")
    if not isinstance(depends_on, list):
        depends_on = task.get("dependsOn")
    depends_on = depends_on if isinstance(depends_on, list) else []

    input_data = task.get("input_data")
    if not isinstance(input_data, dict):
        input_data = task.get("inputData")
    input_data = input_data if isinstance(input_data, dict) else {}

    return {
        "id": text(task.get("id"), ""),
        "description": text(task.get("description"), ""),
        "role": text(task.get("role") or task.get("expectedRole"), "SPECIALIST"),
        "depends_on": depends_on,
        "input_data": input_data,
    }


def _execution_mode(workflow: dict[str, Any]) -> str:
    mode = text(workflow.get("execution_mode"), "DYNAMIC").upper()
    return mode if mode in {"DYNAMIC", "DAG_STRICT"} else "DYNAMIC"


def _workflow_dag_subtasks(workflow: dict[str, Any]) -> list[dict[str, Any]]:
    raw = workflow.get("dag_subtasks")
    if not isinstance(raw, list):
        return []
    normalized: list[dict[str, Any]] = []
    valid_ids: set[str] = set()
    for item in raw:
        if not isinstance(item, dict):
            continue
        task = _normalize_subtask_payload(item)
        task_id = text(task.get("id"), "")
        if not task_id:
            continue
        valid_ids.add(task_id)
        normalized.append(task)
    for task in normalized:
        deps = task.get("depends_on")
        task["depends_on"] = [text(dep, "") for dep in deps if text(dep, "") in valid_ids] if isinstance(deps, list) else []
    return normalized


def _append_trace(state: dict[str, Any], evt: dict[str, Any]) -> None:
    state.setdefault("trace", []).append(evt)
    queue = state.get("_trace_queue")
    if queue is not None:
        try:
            queue.put_nowait({"event": "trace", "data": evt})
        except Exception:
            pass


def _extract_result_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, dict):
        maybe = value.get("result")
        if maybe is not None:
            return str(maybe)
    return str(value)


def _is_failed_branch_result(value: Any) -> tuple[bool, str]:
    if isinstance(value, dict):
        status = text(value.get("status"), "").upper()
        if status in {"FAILED", "TIMEOUT"}:
            return True, text(value.get("errorMessage"), "subtask failed")
    return False, ""


def _workflow_mode(workflow_type: str) -> str:
    if workflow_type in {"router_specialists", "planner_executor"}:
        return "PARALLEL"
    return "SEQUENTIAL"


def _extract_chat_text(data: Any) -> str:
    if isinstance(data, str):
        return data
    if isinstance(data, dict):
        for key in ("answer", "text", "content", "response", "message"):
            value = data.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
            if isinstance(value, dict):
                nested = value.get("content")
                if isinstance(nested, str) and nested.strip():
                    return nested.strip()
        nested_data = data.get("data")
        if isinstance(nested_data, dict):
            for key in ("text", "content", "answer"):
                value = nested_data.get(key)
                if isinstance(value, str) and value.strip():
                    return value.strip()
    return str(data or "")


def _extract_json_object(raw: str) -> dict[str, Any]:
    candidate = (raw or "").strip()
    if not candidate:
        return {}
    if candidate.startswith("```"):
        candidate = re.sub(r"^```(?:json)?\s*", "", candidate)
        candidate = re.sub(r"\s*```$", "", candidate)
    try:
        parsed = json.loads(candidate)
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        pass

    match = re.search(r"\{[\s\S]*\}", candidate)
    if not match:
        return {}
    try:
        parsed = json.loads(match.group(0))
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        return {}


def _catalog_agents(agents: list[dict[str, Any]]) -> list[dict[str, str]]:
    catalog: list[dict[str, str]] = []
    for agent in agents or []:
        catalog.append({
            "id": agent_id(agent, ""),
            "name": agent_name(agent),
            "description": text(agent.get("description"), ""),
        })
    return [item for item in catalog if item["id"]]


def _choose_agent_for_task(task_text: str, role: str, agents: list[dict[str, Any]]) -> str:
    if not agents:
        return ""
    lowered = f"{role} {task_text}".lower()
    ranked: list[tuple[int, dict[str, Any]]] = []
    for index, agent in enumerate(agents):
        profile = f"{agent.get('name', '')} {agent.get('description', '')} {agent.get('system_prompt', '')}".lower()
        score = sum(1 for token in lowered.split() if len(token) > 1 and token in profile)
        ranked.append((score * 10 - index, agent))
    ranked.sort(key=lambda item: item[0], reverse=True)
    return agent_id(ranked[0][1], "") if ranked else agent_id(agents[0], "")


def _wants_parallel_execution(user_input: str) -> bool:
    content = (user_input or "").lower()
    signals = [
        "并行",
        "parallel",
        "同时",
        "并发",
        "concurrently",
    ]
    return any(token in content for token in signals)


def _extract_role_tasks_from_input(user_input: str) -> list[tuple[str, str]]:
    # Supports numbered role specs like:
    # 1) 架构师：输出系统架构图...
    # 2. SRE: 给出告警规则...
    pattern = re.compile(r"(?:^|\n)\s*\d+\s*[).、]\s*([^：:\n]+)\s*[：:]\s*([^\n]+)")
    matches = pattern.findall(user_input or "")
    results: list[tuple[str, str]] = []
    for role, objective in matches:
        r = text(role, "")
        o = text(objective, "")
        if r and o:
            results.append((r, o))
    return results


def _infer_explicit_task_count(user_input: str) -> int | None:
    content = user_input or ""
    number_map = {
        "两": 2, "二": 2,
        "三": 3,
        "四": 4,
        "五": 5,
        "六": 6,
        "七": 7,
        "八": 8,
    }
    m_digit = re.search(r"(\d+)\s*个", content)
    if m_digit:
        try:
            value = int(m_digit.group(1))
            if value > 0:
                return value
        except Exception:
            pass
    for cn, value in number_map.items():
        if f"{cn}个" in content:
            return value
    return None


def _validate_plan_ir(
    plan: dict[str, Any],
    agents: list[dict[str, Any]],
    user_input: str,
    max_tasks: int = 6,
) -> list[dict[str, Any]]:
    tasks_obj = plan.get("tasks")
    explicit_role_tasks = _extract_role_tasks_from_input(user_input)
    explicit_count = _infer_explicit_task_count(user_input)

    if (not isinstance(tasks_obj, list) or not tasks_obj) and explicit_role_tasks:
        tasks_obj = [
            {
                "task_id": f"task_{idx}",
                "objective": objective,
                "role": role,
                "depends_on": [],
            }
            for idx, (role, objective) in enumerate(explicit_role_tasks, start=1)
        ]
    if not isinstance(tasks_obj, list) or not tasks_obj:
        raise RuntimeError("planner output invalid: tasks is empty")

    known_agent_ids = {agent_id(agent, "") for agent in agents if agent_id(agent, "")}
    out: list[dict[str, Any]] = []
    raw_ids: list[str] = []
    force_parallel = _wants_parallel_execution(user_input)

    target_count = explicit_count or len(explicit_role_tasks) or min(len(tasks_obj), max_tasks)
    target_count = max(2, min(max_tasks, target_count))

    for idx, item in enumerate(tasks_obj[:target_count], start=1):
        if not isinstance(item, dict):
            continue
        raw_id = text(item.get("task_id"), f"task_{idx}")
        raw_ids.append(raw_id)
        role = text(item.get("role"), "SPECIALIST").upper()
        objective = text(item.get("objective"), text(item.get("description"), ""))
        if not objective:
            raise RuntimeError(f"planner output invalid: task {raw_id} missing objective")

        assigned_agent_id = text(item.get("assigned_agent_id"), "")
        if assigned_agent_id and known_agent_ids and assigned_agent_id not in known_agent_ids:
            assigned_agent_id = ""
        if not assigned_agent_id:
            assigned_agent_id = _choose_agent_for_task(objective, role, agents)

        depends = item.get("depends_on")
        depends_list = [text(dep, "") for dep in depends] if isinstance(depends, list) else []
        out.append({
            "task_id": raw_id,
            "role": role or "SPECIALIST",
            "objective": objective,
            "depends_on": depends_list,
            "assigned_agent_id": assigned_agent_id,
        })

    if not out:
        raise RuntimeError("planner output invalid: no valid tasks")

    valid_ids = {item["task_id"] for item in out}
    raw_to_norm: dict[str, str] = {}
    for idx, item in enumerate(out, start=1):
        raw_to_norm[item["task_id"]] = f"task_{idx}"

    normalized: list[dict[str, Any]] = []
    for idx, item in enumerate(out, start=1):
        task_id = f"task_{idx}"
        depends = [] if force_parallel else [raw_to_norm[dep] for dep in item["depends_on"] if dep in valid_ids]
        logical_role = item["role"]
        normalized.append({
            "id": task_id,
            "description": f"执行子任务#{idx}（角色:{logical_role}）: {item['objective']}",
            "role": "SPECIALIST",
            "depends_on": depends,
            "input_data": {
                **({"preferred_agent_id": item["assigned_agent_id"]} if item["assigned_agent_id"] else {}),
                "logical_role": logical_role,
            },
        })
    return normalized


def _ephemeral_agents_need_planning(agents: list[dict[str, Any]]) -> bool:
    if not agents:
        return False
    return any(
        bool(agent.get("planning_slot")) or text(agent.get("role"), "").upper() == "ORCHESTRATED_SLOT"
        for agent in agents
        if isinstance(agent, dict)
    )


def _fallback_planned_ephemeral_agents(agents: list[dict[str, Any]], user_input: str) -> list[dict[str, Any]]:
    role_pool = [
        ("需求分析者", "SPECIALIST", "负责澄清目标、拆解问题、识别关键约束和输入缺口。"),
        ("方案复核者", "REVIEWER", "负责检查方案漏洞、反例、风险和不充分假设。"),
        ("执行规划者", "PLANNER", "负责把结论拆成步骤、依赖、里程碑和验收标准。"),
        ("风险质询者", "CRITIC", "负责提出尖锐质疑，识别失败点、安全、成本和上线风险。"),
    ]
    planned: list[dict[str, Any]] = []
    for index, agent in enumerate(agents[:4]):
        name, role, description = role_pool[index % len(role_pool)]
        planned.append({
            **agent,
            "name": name,
            "description": description,
            "system_prompt": (
                f"你是{name}。{description}"
                "严格围绕用户任务和会话上下文输出，避免泛泛而谈，结论要具体、可执行。"
            ),
            "role": role,
            "planning_slot": False,
            "ephemeral": True,
        })
    return planned


def _validate_ephemeral_agent_plan(plan: dict[str, Any], agents: list[dict[str, Any]], user_input: str) -> list[dict[str, Any]]:
    items = plan.get("agents") if isinstance(plan, dict) else None
    if not isinstance(items, list) or not items:
        return _fallback_planned_ephemeral_agents(agents, user_input)

    planned: list[dict[str, Any]] = []
    allowed_roles = {"SPECIALIST", "REVIEWER", "PLANNER", "CRITIC", "PM", "RESEARCHER", "IMPLEMENTER", "TESTER", "COORDINATOR"}
    for index, base in enumerate(agents[:4]):
        item = items[index] if index < len(items) and isinstance(items[index], dict) else {}
        name = text(item.get("name"), "").strip()
        description = text(item.get("description"), "").strip()
        system_prompt = text(item.get("system_prompt"), "").strip()
        role = text(item.get("role"), text(base.get("role"), "SPECIALIST")).upper()
        if role not in allowed_roles:
            role = "SPECIALIST"
        if not name:
            name = text(base.get("name"), f"协作角色 {index + 1}")
        if not description:
            description = text(item.get("responsibility"), text(base.get("description"), "根据 Planner 分配承担本轮协作任务。"))
        if not system_prompt:
            system_prompt = f"你是{name}。{description} 输出要具体、简洁、可执行。"
        planned.append({
            **base,
            "name": name[:30],
            "description": description[:300],
            "system_prompt": system_prompt[:1200],
            "role": role,
            "max_tokens": bounded_int(item.get("max_tokens"), bounded_int(base.get("max_tokens"), int(getattr(settings, "PLAYGROUND_AGENT_MAX_TOKENS", 2400)))),
            "temperature": item.get("temperature", base.get("temperature")),
            "planning_slot": False,
            "ephemeral": True,
        })
    return planned or _fallback_planned_ephemeral_agents(agents, user_input)


async def _plan_ephemeral_agents_by_llm(
    user_input: str,
    context_messages: list[dict[str, Any]],
    agents: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    if not agents:
        return agents
    if not _ephemeral_agents_need_planning(agents):
        return agents

    planner_agent = agents[0]
    planner_agent_id = agent_id(planner_agent, "")
    context_text = "\n".join(
        f"{text(item.get('role'), 'user')}: {text(item.get('content'), '')}"
        for item in context_messages[-4:]
        if isinstance(item, dict) and text(item.get("content"), "")
    )
    slot_hints = [
        {
            "slot_id": agent_id(agent, f"slot_{idx}"),
            "hint": text(agent.get("description"), ""),
        }
        for idx, agent in enumerate(agents, start=1)
    ]

    planner_system_prompt = "You are ORIN Collaboration Role Planner. Return compact JSON only. No markdown."
    planner_user_prompt = (
        "Plan temporary collaboration roles for this single run.\n"
        "Output one JSON object: "
        "{\"agents\":[{\"slot_id\":\"...\",\"name\":\"...\",\"role\":\"SPECIALIST|REVIEWER|PLANNER|CRITIC|PM|RESEARCHER|IMPLEMENTER|TESTER|COORDINATOR\","
        "\"description\":\"...\",\"system_prompt\":\"...\",\"temperature\":0.4,\"max_tokens\":2400}]}.\n"
        "Rules:\n"
        f"- output exactly {len(agents)} agents, aligned to slot order\n"
        "- do not default every role to 专家; choose natural roles such as 评审者、执行者、协调者、研究员、测试者、反对者、产品取舍者\n"
        "- names must be short Chinese role names without random suffixes\n"
        "- system_prompt must define responsibility for this task, not generic persona\n"
        "- if the user says to continue previous roles, keep useful roles but you may rename or replace roles when the task changed\n"
        f"user_input={user_input}\n"
        f"context={context_text}\n"
        f"slots={json.dumps(slot_hints, ensure_ascii=False)}"
    )

    planner_override = {
        **planner_agent,
        "system_prompt": planner_system_prompt,
        "max_tokens": int(getattr(settings, "PLAYGROUND_PLANNER_MAX_TOKENS", 800)),
        "temperature": 0.2,
    }
    try:
        runtime = TaskRuntime()
        raw_text = await runtime.execute_agent_task(
            description=planner_user_prompt,
            expected_role="PLANNER",
            context={
                "preferred_agent_id": planner_agent_id,
                "ephemeral_agents": [planner_override],
                "agent_max_tokens": int(getattr(settings, "PLAYGROUND_PLANNER_MAX_TOKENS", 800)),
            },
        )
        plan = _extract_json_object(raw_text)
        return _validate_ephemeral_agent_plan(plan, agents, user_input)
    except Exception:
        logger.exception("Ephemeral role planning failed; falling back to deterministic role plan")
        return _fallback_planned_ephemeral_agents(agents, user_input)


async def _plan_tasks_by_llm(workflow: dict[str, Any], user_input: str, agents: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not agents:
        raise RuntimeError("planner_executor requires at least one agent for LLM planner")

    planner_agent = agents[0]
    planner_agent_id = agent_id(planner_agent, "")
    if not planner_agent_id:
        raise RuntimeError("planner_executor: planner agent id is empty")

    catalog = _catalog_agents(agents)
    if not catalog:
        raise RuntimeError("planner_executor: no valid agents in catalog")

    planner_system_prompt = "You are ORIN Planner. Return compact JSON only. No markdown."
    planner_user_prompt = (
        "Output one JSON object with key 'tasks' only.\n"
        "Task item schema: "
        "{\"task_id\":\"task_n\",\"objective\":\"...\",\"role\":\"SPECIALIST|REVIEWER|PLANNER|CRITIC|PM\","
        "\"depends_on\":[\"task_id\"],\"assigned_agent_id\":\"id from agent_catalog\"}.\n"
        "Rules:\n"
        "- total tasks 2..6\n"
        "- objective must be concise (<= 40 Chinese chars)\n"
        "- assigned_agent_id must come from agent_catalog\n"
        "- dependency must be valid and acyclic\n"
        "- if user explicitly asks parallel execution, set all depends_on to []\n"
        "- if user provides numbered role list, produce exactly that count\n"
        f"user_input={user_input}\n"
        f"agent_catalog={json.dumps(catalog, ensure_ascii=False)}"
    )

    if planner_agent_id.startswith("ephemeral:"):
        planner_override = {
            **planner_agent,
            "system_prompt": planner_system_prompt,
            "max_tokens": int(getattr(settings, "PLAYGROUND_PLANNER_MAX_TOKENS", 800)),
            "temperature": 0.2,
        }
        runtime = TaskRuntime()
        raw_text = await runtime.execute_agent_task(
            description=planner_user_prompt,
            expected_role="PLANNER",
            context={
                "preferred_agent_id": planner_agent_id,
                "ephemeral_agents": [planner_override, *[agent for agent in agents if agent_id(agent, "") != planner_agent_id]],
                "agent_max_tokens": int(getattr(settings, "PLAYGROUND_PLANNER_MAX_TOKENS", 800)),
            },
        )
        plan = _extract_json_object(raw_text)
        return _validate_plan_ir(plan, agents, user_input)

    url = f"{settings.ORIN_BACKEND_URL}/api/v1/agents/{planner_agent_id}/chat"
    payload = {
        "message": planner_user_prompt,
        "system_prompt": planner_system_prompt,
        "max_tokens": int(getattr(settings, "PLAYGROUND_PLANNER_MAX_TOKENS", 800)),
        "enable_thinking": False,
    }
    headers = {"Content-Type": "application/json"}

    response = await post_json_with_context(
        step="planner_llm",
        url=url,
        payload=payload,
        headers=headers,
        timeout_seconds=float(getattr(settings, "PLAYGROUND_PLANNER_CHAT_TIMEOUT_SECONDS", 600.0)),
    )
    if response.status_code >= 400:
        raise RuntimeError(f"planner llm failed: HTTP {response.status_code} {response.text[:200]}")
    data = response.json() if "application/json" in response.headers.get("content-type", "") else response.text

    raw_text = _extract_chat_text(data)
    plan = _extract_json_object(raw_text)
    return _validate_plan_ir(plan, agents, user_input)


def _workflow_tasks(workflow_type: str, user_input: str, agents: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if workflow_type == "single_agent_chat":
        selected = agents[0] if agents else {"id": "assistant", "name": "Assistant"}
        return [{
            "id": "task_1",
            "description": f"处理用户请求：{user_input}",
            "role": "SPECIALIST",
            "depends_on": [],
            "input_data": {"preferred_agent_id": agent_id(selected, "assistant")},
        }]

    if workflow_type == "router_specialists":
        tasks = []
        for idx, agent in enumerate(agents or [{"id": "assistant", "name": "Assistant"}], start=1):
            tasks.append({
                "id": f"task_{idx}",
                "description": f"作为专家并行处理用户请求：{user_input}。建议执行Agent：{agent_name(agent)}",
                "role": "SPECIALIST",
                "depends_on": [],
                "input_data": {"preferred_agent_id": agent_id(agent, f"agent_{idx}")},
            })
        return tasks

    if workflow_type == "planner_executor":
        tasks = []
        planned = plan_tasks(user_input)
        last_id: str | None = None
        used_agent_ids: set[str] = set()
        for idx, task_text in enumerate(planned, start=1):
            selected, _ = select_agent(task_text, agents)
            selected_id = agent_id(selected, f"agent_{idx}")
            if selected_id in used_agent_ids and agents:
                # Diversify assignment across subtasks when multiple agents exist.
                for candidate in agents:
                    candidate_id = agent_id(candidate, "")
                    if candidate_id and candidate_id not in used_agent_ids:
                        selected = candidate
                        selected_id = candidate_id
                        break
            task_id = f"task_{idx}"
            tasks.append({
                "id": task_id,
                "description": f"执行子任务#{idx}: {task_text}。建议执行Agent：{agent_name(selected)}",
                "role": "SPECIALIST",
                "depends_on": [last_id] if last_id else [],
                "input_data": {"preferred_agent_id": selected_id},
            })
            if selected_id:
                used_agent_ids.add(selected_id)
            last_id = task_id
        return tasks

    if workflow_type == "supervisor_dynamic":
        tasks = []
        chain = agents[:3] if agents else [{"id": "assistant", "name": "Assistant"}]
        last_id: str | None = None
        for idx, agent in enumerate(chain, start=1):
            task_id = f"task_{idx}"
            tasks.append({
                "id": task_id,
                "description": f"第{idx}轮监督协作，基于用户请求给出增量贡献：{user_input}。建议执行Agent：{agent_name(agent)}",
                "role": "SPECIALIST",
                "depends_on": [last_id] if last_id else [],
                "input_data": {"preferred_agent_id": agent_id(agent, f"agent_{idx}")},
            })
            last_id = task_id
        return tasks

    if workflow_type == "peer_handoff":
        tasks = []
        chain = agents[:3] if agents else [{"id": "assistant", "name": "Assistant"}]
        last_id: str | None = None
        for idx, agent in enumerate(chain, start=1):
            task_id = f"task_{idx}"
            tasks.append({
                "id": task_id,
                "description": f"接力步骤{idx}，延续前一轮结果并推进最终答案：{user_input}。建议执行Agent：{agent_name(agent)}",
                "role": "SPECIALIST",
                "depends_on": [last_id] if last_id else [],
                "input_data": {"preferred_agent_id": agent_id(agent, f"agent_{idx}")},
            })
            last_id = task_id
        return tasks

    selected, _ = select_agent(user_input, agents)
    return [{
        "id": "task_1",
        "description": f"处理用户请求：{user_input}",
        "role": "SPECIALIST",
        "depends_on": [],
        "input_data": {"preferred_agent_id": agent_id(selected, "assistant")},
    }]


def build_runtime_graph(subtasks: list[dict[str, Any]]) -> dict[str, Any]:
    nodes: list[dict[str, Any]] = [
        node("start", "Start", "start"),
        node("planner", "Planner", "logic"),
    ]
    edges: list[dict[str, Any]] = [edge("start", "planner")]

    task_ids: set[str] = set()
    for task in subtasks or []:
        task_id = text(task.get("id"), "")
        if not task_id:
            continue
        task_ids.add(task_id)
        label = task_id.replace("_", " ").title()
        nodes.append(node(task_id, label, "agent"))

    if not task_ids:
        nodes.append(node("end", "End", "end"))
        edges.append(edge("planner", "end"))
        return {"nodes": nodes, "edges": edges}

    for task in subtasks:
        task_id = text(task.get("id"), "")
        if not task_id:
            continue
        depends = task.get("depends_on") if isinstance(task.get("depends_on"), list) else []
        valid_deps = [text(dep, "") for dep in depends if text(dep, "") in task_ids]
        if valid_deps:
            for dep in valid_deps:
                edges.append(edge(dep, task_id))
        else:
            edges.append(edge("planner", task_id))

    depended_on: set[str] = set()
    for task in subtasks:
        depends = task.get("depends_on") if isinstance(task.get("depends_on"), list) else []
        depended_on.update(text(dep, "") for dep in depends if text(dep, "") in task_ids)
    leaves = [task_id for task_id in task_ids if task_id not in depended_on]
    nodes.append(node("end", "End", "end"))
    if len(task_ids) > 1:
        # Explicit merge node for any multi-task collaboration before workflow end.
        nodes.append(node("merge", "Merge", "merge"))
        for task_id in leaves:
            edges.append(edge(task_id, "merge"))
        edges.append(edge("merge", "end"))
    else:
        for task_id in leaves:
            edges.append(edge(task_id, "end"))

    return {"nodes": nodes, "edges": edges}


def _attach_context_to_subtasks(tasks: list[dict[str, Any]], state: dict[str, Any]) -> list[dict[str, Any]]:
    context_text = format_context_messages(state.get("context_messages") or [])
    if not context_text:
        return tasks
    current_input = text(state.get("user_input"), "")
    suffix = (
        "\n\n执行上下文：\n"
        f"- 当前用户请求：{current_input}\n"
        "- 近期会话上下文用于解析“上面/这个/刚才”等指代：\n"
        f"{context_text[:1800]}\n"
        "请只执行本子任务，并基于上述上下文作答。"
    )
    enriched: list[dict[str, Any]] = []
    for item in tasks:
        if not isinstance(item, dict):
            continue
        next_item = dict(item)
        next_item["description"] = f"{text(next_item.get('description'), text(next_item.get('objective'), ''))}{suffix}"
        enriched.append(next_item)
    return enriched


async def _bootstrap_collaboration_package(state: dict[str, Any]) -> tuple[str, list[dict[str, Any]]]:
    workflow = state["workflow"]
    workflow_type = text(workflow.get("type"), "router_specialists")
    mode = _workflow_mode(workflow_type)
    execution_mode = _execution_mode(workflow)
    dag_subtasks = _workflow_dag_subtasks(workflow)
    if execution_mode == "DAG_STRICT":
        if not dag_subtasks:
            raise RuntimeError("DAG_STRICT requires non-empty workflow.dag_subtasks")
        tasks = dag_subtasks
    elif workflow_type == "planner_executor":
        tasks = await _plan_tasks_by_llm(workflow, state.get("contextual_user_input") or state["user_input"], state["agents"])
    else:
        tasks = _workflow_tasks(workflow_type, state.get("contextual_user_input") or state["user_input"], state["agents"])

    tasks = _attach_context_to_subtasks(tasks, state)

    if state.get("ephemeral_agents"):
        subtasks = []
        for item in tasks:
            if isinstance(item, dict):
                normalized = _normalize_subtask_payload(item)
                normalized["_ephemeral_agents"] = state.get("ephemeral_agents") or []
                subtasks.append(normalized)
        if not subtasks:
            raise RuntimeError("Ephemeral collaboration produced no subtasks")
        return f"ephemeral:{state['run_id']}", subtasks

    url = f"{settings.ORIN_BACKEND_URL}/api/playground/collaboration/bootstrap"
    payload = {
        "run_id": state["run_id"],
        "intent": state.get("contextual_user_input") or state["user_input"],
        "workflow_type": workflow_type,
        "collaboration_mode": mode,
        "trace_id": state.get("trace_id") or state["run_id"],
        "agent_max_tokens": bounded_int(
            workflow.get("agent_max_tokens"),
            int(getattr(settings, "PLAYGROUND_AGENT_MAX_TOKENS", 2400)),
        ),
        "subtasks": tasks,
    }

    response = await post_json_with_context(
        step="bootstrap_package",
        url=url,
        payload=payload,
        timeout_seconds=20.0,
    )
    if response.status_code >= 400:
        raise RuntimeError(f"Bootstrap failed: HTTP {response.status_code} {response.text[:200]}")
    data = response.json()
    package_id = text(data.get("package_id"), "")
    raw_subtasks = data.get("subtasks") if isinstance(data.get("subtasks"), list) else tasks
    subtasks = []
    for item in raw_subtasks:
        if isinstance(item, dict):
            normalized = _normalize_subtask_payload(item)
            normalized["_ephemeral_agents"] = state.get("ephemeral_agents") or []
            subtasks.append(normalized)
    if not package_id:
        raise RuntimeError("Bootstrap failed: empty package_id")
    return package_id, subtasks


async def _trigger_subtask(package_id: str, subtask: dict[str, Any], trace_id: str) -> None:
    subtask_id = text(subtask.get("id"), "")
    if not subtask_id:
        raise RuntimeError("subtask missing id")

    url = f"{settings.ORIN_BACKEND_URL}/api/playground/collaboration/packages/{package_id}/subtasks/{subtask_id}/execute"
    payload = {
        "expectedRole": text(subtask.get("role"), "SPECIALIST"),
        "description": text(subtask.get("description"), ""),
        "inputData": subtask.get("input_data") if isinstance(subtask.get("input_data"), dict) else {},
        "contextSnapshot": {
            "playground": True,
            "runId": trace_id,
            "ephemeral_agents": subtask.get("_ephemeral_agents") if isinstance(subtask.get("_ephemeral_agents"), list) else [],
        },
    }
    headers = {"X-Trace-Id": trace_id}

    response = await post_json_with_context(
        step=f"execute_subtask:{subtask_id}",
        url=url,
        payload=payload,
        headers=headers,
        timeout_seconds=20.0,
    )
    if response.status_code >= 400:
        raise RuntimeError(f"execute subtask failed: HTTP {response.status_code} {response.text[:200]}")


async def _planner_node(state: dict[str, Any]) -> dict[str, Any]:
    workflow = state["workflow"]
    workflow_type = text(workflow.get("type"), "router_specialists")
    execution_mode = _execution_mode(workflow)
    package_id, subtasks = await _bootstrap_collaboration_package(state)
    _append_trace(
        state,
        event(
            "node_progress",
            "Planner Prepared",
            f"Prepared {len(subtasks)} subtask(s) in package {package_id}.",
            "planner",
            state["run_id"],
            "RUNNING",
            package_id=package_id,
            subtask_count=len(subtasks),
            subtasks=[
                {
                    "id": text(task.get("id"), ""),
                    "description": text(task.get("description"), ""),
                    "depends_on": task.get("depends_on") if isinstance(task.get("depends_on"), list) else [],
                    "logical_role": (
                        task.get("input_data", {}).get("logical_role")
                        if isinstance(task.get("input_data"), dict)
                        else None
                    ),
                    "preferred_agent_id": (
                        task.get("input_data", {}).get("preferred_agent_id")
                        if isinstance(task.get("input_data"), dict)
                        else None
                    ),
                }
                for task in subtasks
            ],
            execution_mode=execution_mode,
            planning_source="dag_subtasks" if execution_mode == "DAG_STRICT" else ("planner_llm" if workflow_type == "planner_executor" else "builtin"),
        ),
    )
    return {
        **state,
        "package_id": package_id,
        "subtasks": subtasks,
        "completed_subtasks": [],
        "reports": [],
        "task_reports": [],
    }


async def _execute_one_subtask(state: dict[str, Any], subtask: dict[str, Any]) -> tuple[str, str, dict[str, Any]]:
    subtask_id = text(subtask.get("id"), "")
    subtask_desc = text(subtask.get("description"), "")
    input_data = subtask.get("input_data") if isinstance(subtask.get("input_data"), dict) else {}

    _append_trace(
        state,
        event(
            "node_entered",
            "Node Entered",
            f"Subtask {subtask_id} entered.",
            subtask_id,
            state["run_id"],
            "RUNNING",
            package_id=state["package_id"],
        ),
    )

    if state.get("ephemeral_agents") or str(input_data.get("preferred_agent_id", "")).startswith("ephemeral:"):
        runtime = TaskRuntime()
        result_text = await runtime.execute_agent_task(
            description=subtask_desc,
            expected_role=text(subtask.get("role"), "SPECIALIST"),
            context={
                **input_data,
                "package_id": state.get("package_id"),
                "sub_task_id": subtask_id,
                "_trace_id": state.get("trace_id") or state["run_id"],
                "ephemeral_agents": state.get("ephemeral_agents") or subtask.get("_ephemeral_agents") or [],
                "agent_max_tokens": bounded_int(
                    state.get("workflow", {}).get("agent_max_tokens"),
                    int(getattr(settings, "PLAYGROUND_AGENT_MAX_TOKENS", 1200)),
                ),
            },
        )
        _append_trace(
            state,
            event(
                "node_exited",
                "Node Exited",
                f"Subtask {subtask_id} completed by ephemeral runtime.",
                subtask_id,
                state["run_id"],
                "COMPLETED",
                package_id=state["package_id"],
            ),
        )
        report_text = f"[{subtask_id}] {subtask_desc}\n{result_text}"
        report = {
            "task_id": subtask_id,
            "description": subtask_desc,
            "status": "COMPLETED",
            "result": result_text,
        }
        return subtask_id, report_text, report

    await _trigger_subtask(state["package_id"], subtask, state.get("trace_id") or state["run_id"])

    _append_trace(
        state,
        event(
            "node_progress",
            "Node Progress",
            f"Subtask {subtask_id} dispatched to MQ worker.",
            subtask_id,
            state["run_id"],
            "RUNNING",
            package_id=state["package_id"],
        ),
    )

    poll_timeout = float(getattr(settings, "PLAYGROUND_SUBTASK_POLL_TIMEOUT_SECONDS", 420.0))
    poll_interval = float(getattr(settings, "PLAYGROUND_SUBTASK_POLL_INTERVAL_SECONDS", 1.0))
    result_obj = await poll_branch_result(
        package_id=state["package_id"],
        sub_task_id=subtask_id,
        timeout=poll_timeout,
        poll_interval=poll_interval,
    )
    if result_obj is None:
        ctx = read_collab_ctx(state["package_id"]) or {}
        pending_key = f"pending_task:{subtask_id}"
        has_pending = pending_key in ctx
        hint = "pending task exists but branch result missing (worker/result callback may be stalled)" if has_pending else "pending task marker missing (dispatch/queue consumer may be stalled)"
        raise RuntimeError(
            f"subtask timeout: {subtask_id} after {int(poll_timeout)}s "
            f"(package={state['package_id']}; {hint})"
        )

    failed, error_msg = _is_failed_branch_result(result_obj)
    if failed:
        raise RuntimeError(f"subtask failed: {subtask_id}, {text(error_msg, 'unknown error')}")

    result_text = _extract_result_value(result_obj)
    _append_trace(
        state,
        event(
            "node_exited",
            "Node Exited",
            f"Subtask {subtask_id} completed.",
            subtask_id,
            state["run_id"],
            "COMPLETED",
            package_id=state["package_id"],
        ),
    )
    report_text = f"[{subtask_id}] {subtask_desc}\n{result_text}"
    report = {
        "task_id": subtask_id,
        "description": subtask_desc,
        "status": "COMPLETED",
        "result": result_text,
    }
    return subtask_id, report_text, report


async def _executor_node(state: dict[str, Any]) -> dict[str, Any]:
    workflow_type = text(state["workflow"].get("type"), "router_specialists")
    mode = _workflow_mode(workflow_type)
    subtasks = list(state.get("subtasks") or [])
    completed = set(state.get("completed_subtasks") or [])
    reports = list(state.get("reports") or [])
    task_reports = list(state.get("task_reports") or [])

    while len(completed) < len(subtasks):
        executable: list[dict[str, Any]] = []
        for subtask in subtasks:
            subtask_id = text(subtask.get("id"), "")
            if not subtask_id or subtask_id in completed:
                continue
            depends = subtask.get("depends_on") if isinstance(subtask.get("depends_on"), list) else []
            if all(text(dep, "") in completed for dep in depends):
                executable.append(subtask)

        if not executable:
            raise RuntimeError("No executable subtasks available (possible dependency deadlock)")

        batch = executable if mode == "PARALLEL" else [executable[0]]
        results = await asyncio.gather(*[_execute_one_subtask(state, task) for task in batch], return_exceptions=True)

        for task, result in zip(batch, results):
            task_id = text(task.get("id"), "")
            if isinstance(result, Exception):
                _append_trace(
                    state,
                    event(
                        "node_failed",
                        "Node Failed",
                        f"Subtask {task_id} failed: {result}",
                        task_id,
                        state["run_id"],
                        "FAILED",
                        package_id=state["package_id"],
                        error_message=error_text(result),
                    ),
                )
                raise RuntimeError(error_text(result))
            finished_id, report, task_report = result
            completed.add(finished_id)
            reports.append(report)
            task_reports.append(task_report)

    return {
        **state,
        "completed_subtasks": list(completed),
        "reports": reports,
        "task_reports": task_reports,
    }


async def _merge_reports_by_llm(state: dict[str, Any], task_reports: list[dict[str, Any]]) -> tuple[str, str]:
    agents = [agent for agent in (state.get("agents") or []) if isinstance(agent, dict)]
    workflow = state.get("workflow") if isinstance(state.get("workflow"), dict) else {}
    if not agents:
        raise RuntimeError("merge llm failed: no available agent")

    merge_agent_id = text(agents[0].get("id"), "")
    if not merge_agent_id:
        raise RuntimeError("merge llm failed: merge agent id is empty")

    compact_reports: list[dict[str, str]] = []
    for report in task_reports:
        if not isinstance(report, dict):
            continue
        compact_reports.append(
            {
                "task_id": text(report.get("task_id"), ""),
                "description": text(report.get("description"), ""),
                "result": text(report.get("result"), "")[:4000],
            }
        )

    merge_system_prompt = (
        "You are ORIN Merge Synthesizer. Synthesize multiple task outputs into one final answer.\n"
        "Strictly follow formatting constraints requested by user_input.\n"
        "Do not impose an arbitrary length cap. Be complete enough to satisfy the user's requested structure and detail.\n"
        "If the user asks for a short answer, keep it short; otherwise include the important synthesis, conflicts, tradeoffs, and final recommendation.\n"
        "Do not include internal reasoning."
    )
    merge_user_prompt = (
        f"user_input:\n{state.get('user_input')}\n\n"
        f"conversation_context:\n{format_context_messages(state.get('context_messages') or []) or 'None'}\n\n"
        "task_reports_json:\n"
        f"{json.dumps(compact_reports, ensure_ascii=False)}\n\n"
        "Output final answer only."
    )

    if merge_agent_id.startswith("ephemeral:"):
        runtime = TaskRuntime()
        merge_max_tokens = bounded_int(
            workflow.get("merge_max_tokens"),
            int(getattr(settings, "PLAYGROUND_MERGE_MAX_TOKENS", 6000)),
        )
        merge_ephemeral_agents = [
            {**agent, "max_tokens": merge_max_tokens} if agent_id(agent, "") == merge_agent_id else agent
            for agent in (state.get("ephemeral_agents") or [])
            if isinstance(agent, dict)
        ]
        merged_text = await runtime.execute_agent_task(
            description=merge_user_prompt,
            expected_role="MERGE",
            context={
                "preferred_agent_id": merge_agent_id,
                "ephemeral_agents": merge_ephemeral_agents,
                "agent_max_tokens": merge_max_tokens,
            },
        )
        merged_text = text(merged_text, "")
        if not merged_text:
            raise RuntimeError("merge llm failed: empty response")
        return merged_text, merge_agent_id

    url = f"{settings.ORIN_BACKEND_URL}/api/v1/agents/{merge_agent_id}/chat"
    payload = {
        "message": merge_user_prompt,
        "system_prompt": merge_system_prompt,
        "max_tokens": bounded_int(
            workflow.get("merge_max_tokens"),
            int(getattr(settings, "PLAYGROUND_MERGE_MAX_TOKENS", 6000)),
        ),
        "enable_thinking": False,
    }
    headers = {"Content-Type": "application/json"}
    response = await post_json_with_context(
        step="merge_llm",
        url=url,
        payload=payload,
        headers=headers,
        timeout_seconds=float(getattr(settings, "PLAYGROUND_MERGE_CHAT_TIMEOUT_SECONDS", 90.0)),
    )
    if response.status_code >= 400:
        raise RuntimeError(f"merge llm failed: HTTP {response.status_code} {response.text[:200]}")
    data = response.json() if "application/json" in response.headers.get("content-type", "") else response.text
    merged_text = _extract_chat_text(data)
    merged_text = text(merged_text, "")
    if not merged_text:
        raise RuntimeError("merge llm failed: empty response")
    return merged_text, merge_agent_id


async def _finalizer_node(state: dict[str, Any]) -> dict[str, Any]:
    reports = state.get("reports") or []
    task_reports = state.get("task_reports") or []
    workflow = state.get("workflow") or {}
    agent_name_map: dict[str, str] = {}
    for agent in (state.get("agents") or []):
        if isinstance(agent, dict):
            current_id = text(agent.get("id"), "")
            if current_id:
                agent_name_map[current_id] = text(agent.get("name"), current_id)

    # Emit explicit merge events when multiple leaf tasks converge.
    subtasks = list(state.get("subtasks") or [])
    task_ids = {text(task.get("id"), "") for task in subtasks if text(task.get("id"), "")}
    depended_on: set[str] = set()
    for task in subtasks:
        depends = task.get("depends_on") if isinstance(task.get("depends_on"), list) else []
        depended_on.update(text(dep, "") for dep in depends if text(dep, "") in task_ids)
    leaves = [task_id for task_id in task_ids if task_id and task_id not in depended_on]
    merge_active = len(task_ids) > 1
    if merge_active:
        _append_trace(
            state,
            event(
                "node_entered",
                "Node Entered",
                f"Merge node entered: converging {len(leaves)} parallel branches.",
                "merge",
                state["run_id"],
                "RUNNING",
                package_id=state.get("package_id"),
                branch_count=len(leaves),
            ),
        )

    merge_strategy = "none"
    merge_agent_id = ""
    if bool(workflow.get("finalizer_enabled", True)) and task_reports:
        if len(task_reports) > 1:
            _append_trace(
                state,
                event(
                    "node_progress",
                    "Node Progress",
                    "Merge node is synthesizing final answer from branch outputs.",
                    "merge",
                    state["run_id"],
                    "RUNNING",
                    package_id=state.get("package_id"),
                    branch_count=len(task_reports),
                ),
            )
            try:
                assistant_message, merge_agent_id = await _merge_reports_by_llm(state, task_reports)
                merge_strategy = "llm"
            except Exception as exc:
                _append_trace(
                    state,
                    event(
                        "node_failed",
                        "Node Failed",
                        f"Merge node failed, fallback to concatenated reports: {error_text(exc)}",
                        "merge",
                        state["run_id"],
                        "FAILED",
                        package_id=state.get("package_id"),
                        error_message=error_text(exc),
                    ),
                )
                merge_strategy = "fallback_concat"
                assistant_message = "\n\n".join(
                    [f"[{text(item.get('task_id'), 'task')}] {text(item.get('result'), '')}" for item in task_reports]
                ).strip()
        else:
            merge_strategy = "single_passthrough"
            assistant_message = text(task_reports[0].get("result"), "")
    else:
        merge_strategy = "empty"
        assistant_message = reports[-1] if reports else ""

    if merge_active:
        _append_trace(
            state,
            event(
                "node_exited",
                "Node Exited",
                f"Merge completed with strategy={merge_strategy}.",
                "merge",
                state["run_id"],
                "COMPLETED",
                package_id=state.get("package_id"),
                branch_count=len(leaves),
                merge_strategy=merge_strategy,
                merge_agent_id=merge_agent_id,
                merged_preview=text(assistant_message, "")[:160],
            ),
        )

    _append_trace(
        state,
        event(
            "run_finished",
            "Run Finished",
            "Workflow completed.",
            "end",
            state["run_id"],
            "COMPLETED",
            package_id=state.get("package_id"),
        ),
    )

    artifacts = {
        "route_agent_id": None,
        "route_agent_name": None,
        "route_reason": "langgraph_mq orchestrated",
        "specialist_answer": assistant_message,
        "final_answer": assistant_message,
        "execution_path": "langgraph_mq",
        "package_id": state.get("package_id"),
        "planner": {
            "workflow_type": text(workflow.get("type"), "router_specialists"),
            "execution_mode": _execution_mode(workflow),
            "package_id": state.get("package_id"),
            "subtask_count": len(state.get("subtasks") or []),
            "subtasks": [
                {
                    "id": text(task.get("id"), ""),
                    "description": text(task.get("description"), ""),
                    "depends_on": task.get("depends_on") if isinstance(task.get("depends_on"), list) else [],
                    "expected_role": text(task.get("role"), "SPECIALIST"),
                    "logical_role": (
                        task.get("input_data", {}).get("logical_role")
                        if isinstance(task.get("input_data"), dict)
                        else None
                    ),
                    "preferred_agent_id": (
                        task.get("input_data", {}).get("preferred_agent_id")
                        if isinstance(task.get("input_data"), dict)
                        else None
                    ),
                    "preferred_agent_name": (
                        agent_name_map.get(task.get("input_data", {}).get("preferred_agent_id"))
                        if isinstance(task.get("input_data"), dict)
                        else None
                    ),
                }
                for task in (state.get("subtasks") or [])
            ],
        },
        "ephemeral_agents": [
            {
                "id": agent_id(agent, ""),
                "name": agent_name(agent),
                "base_name": agent_name(agent),
                "description": text(agent.get("description"), ""),
                "system_prompt": text(agent.get("system_prompt"), ""),
                "model": text(agent.get("model"), ""),
                "role": text(agent.get("role"), "SPECIALIST"),
                "max_tokens": agent.get("max_tokens"),
                "temperature": agent.get("temperature"),
                "ephemeral": True,
            }
            for agent in (state.get("ephemeral_agents") or [])
            if isinstance(agent, dict)
        ],
        "task_reports": task_reports,
        "task_count": len(task_reports),
        "merge": {
            "strategy": merge_strategy,
            "agent_id": merge_agent_id,
            "branch_count": len(leaves),
            "preview": text(assistant_message, "")[:200],
        },
    }

    return {
        **state,
        "assistant_message": assistant_message,
        "artifacts": artifacts,
    }


def _build_runtime_graph():
    graph = StateGraph(dict)
    graph.add_node("planner", _planner_node)
    graph.add_node("executor", _executor_node)
    graph.add_node("finalizer", _finalizer_node)
    graph.add_edge("__start__", "planner")
    graph.add_edge("planner", "executor")
    graph.add_edge("executor", "finalizer")
    graph.add_edge("finalizer", END)
    return graph.compile()


_runtime_graph = _build_runtime_graph()


async def _run_playground_workflow(request: PlaygroundRunRequest, trace_queue: asyncio.Queue | None = None) -> dict[str, Any]:
    ephemeral_agents = normalize_ephemeral_agents(request.ephemeral_agents)
    context_messages = normalize_context_messages(request.context_messages)
    role_planning_requested = _ephemeral_agents_need_planning(ephemeral_agents)
    if ephemeral_agents:
        ephemeral_agents = await _plan_ephemeral_agents_by_llm(request.user_input, context_messages, ephemeral_agents)
    runtime_agents = ephemeral_agents or request.agents
    contextual_input = contextual_user_input(request.user_input, context_messages)
    trace = [
        event(
            "run_started",
            "Run Started",
            f"Starting workflow: {text(request.workflow.get('name'), text(request.workflow.get('type'), 'router_specialists'))}",
            "start",
            request.run_id,
            "RUNNING",
            workflow_type=text(request.workflow.get("type"), "router_specialists"),
        )
    ]
    if role_planning_requested:
        roles_event = event(
            "roles_planned",
            "Roles Planned",
            f"Planner selected {len(ephemeral_agents)} temporary collaboration role(s).",
            "planner",
            request.run_id,
            "COMPLETED",
            roles=[
                {"id": agent_id(agent, ""), "name": agent_name(agent), "role": text(agent.get("role"), "")}
                for agent in ephemeral_agents
            ],
        )
        trace.append(roles_event)
        if trace_queue is not None:
            trace_queue.put_nowait({"event": "trace", "data": roles_event})

    state: dict[str, Any] = {
        "run_id": request.run_id,
        "trace_id": request.run_id,
        "workflow": request.workflow,
        "agents": runtime_agents,
        "persistent_agents": request.agents,
        "ephemeral_agents": ephemeral_agents,
        "user_input": request.user_input,
        "context_messages": context_messages,
        "contextual_user_input": contextual_input,
        "conversation_id": request.conversation_id,
        "trace": trace,
        "_trace_queue": trace_queue,
    }

    try:
        result_state = await _runtime_graph.ainvoke(state)
    except Exception as exc:
        runtime_graph = build_runtime_graph(state.get("subtasks") or [])
        failed_event = event(
            "run_failed",
            "Run Failed",
            error_text(exc),
            "end",
            request.run_id,
            "FAILED",
            error_message=error_text(exc),
        )
        trace.append(failed_event)
        if trace_queue is not None:
            trace_queue.put_nowait({"event": "trace", "data": failed_event})
        return {
            "workflow_id": request.workflow.get("id"),
            "user_input": request.user_input,
            "assistant_message": "",
            "trace": trace,
            "graph": runtime_graph,
            "artifacts": {
                "route_agent_id": None,
                "route_agent_name": None,
                "route_reason": "langgraph_mq failed",
                "specialist_answer": "",
                "final_answer": "",
                "execution_path": "langgraph_mq",
                "error_message": error_text(exc),
            },
            "conversation_id": request.conversation_id,
        }

    runtime_graph = build_runtime_graph(result_state.get("subtasks") or [])
    return {
        "workflow_id": request.workflow.get("id"),
        "user_input": request.user_input,
        "assistant_message": text(result_state.get("assistant_message"), ""),
        "trace": result_state.get("trace") or trace,
        "graph": runtime_graph,
        "artifacts": result_state.get("artifacts") or {
            "execution_path": "langgraph_mq",
        },
        "conversation_id": request.conversation_id,
    }


@router.post("/runs")
async def run_playground_workflow(request: PlaygroundRunRequest) -> dict[str, Any]:
    return await _run_playground_workflow(request)


def _sse_frame(event_name: str, data: Any) -> str:
    return f"event: {event_name}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/runs/stream")
async def run_playground_workflow_stream(request: PlaygroundRunRequest):
    queue: asyncio.Queue = asyncio.Queue()

    async def event_stream():
        async def runner():
            try:
                result = await _run_playground_workflow(request, queue)
                await queue.put({"event": "final", "data": result})
            except Exception as exc:
                await queue.put({"event": "error", "data": {"message": error_text(exc)}})
            finally:
                await queue.put({"event": "_end", "data": {}})

        task = asyncio.create_task(runner())
        try:
            while True:
                item = await queue.get()
                if item.get("event") == "_end":
                    break
                yield _sse_frame(text(item.get("event"), "message"), item.get("data"))
        finally:
            if not task.done():
                task.cancel()

    return StreamingResponse(event_stream(), media_type="text/event-stream")
