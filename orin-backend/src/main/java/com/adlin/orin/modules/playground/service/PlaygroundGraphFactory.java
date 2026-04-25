package com.adlin.orin.modules.playground.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PlaygroundGraphFactory {

    public Map<String, Object> buildGraph(Map<String, Object> workflow, List<Map<String, Object>> agents) {
        String type = text(workflow.get("type"), "router_specialists");
        boolean finalizer = Boolean.TRUE.equals(workflow.get("finalizer_enabled"));
        return switch (type) {
            case "single_agent_chat" -> singleAgent(agents, finalizer);
            case "planner_executor" -> plannerExecutor(agents, finalizer);
            case "supervisor_dynamic" -> supervisorDynamic(agents, finalizer);
            case "peer_handoff" -> peerHandoff(agents, finalizer);
            default -> routerSpecialists(agents, finalizer);
        };
    }

    private Map<String, Object> singleAgent(List<Map<String, Object>> agents, boolean finalizer) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        nodes.add(node("start", "Start", "start"));
        String agentId = agents.isEmpty() ? "agent" : text(agents.get(0).get("id"), "agent");
        String agentName = agents.isEmpty() ? "Agent" : text(agents.get(0).get("name"), "Agent");
        nodes.add(node(agentId, agentName, "agent"));
        edges.add(edge("start", agentId, null));
        appendFinal(nodes, edges, agentId, finalizer);
        return graph(nodes, edges);
    }

    private Map<String, Object> routerSpecialists(List<Map<String, Object>> agents, boolean finalizer) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        nodes.add(node("start", "Start", "start"));
        nodes.add(node("router", "Router", "logic"));
        edges.add(edge("start", "router", null));
        for (Map<String, Object> agent : agents) {
            String id = text(agent.get("id"), "agent");
            nodes.add(node(id, text(agent.get("name"), id), "agent"));
            edges.add(edge("router", id, "route"));
            if (finalizer) {
                edges.add(edge(id, "finalize", null));
            } else {
                edges.add(edge(id, "end", null));
            }
        }
        appendEnd(nodes, edges, finalizer);
        return graph(nodes, edges);
    }

    private Map<String, Object> plannerExecutor(List<Map<String, Object>> agents, boolean finalizer) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        nodes.add(node("start", "Start", "start"));
        nodes.add(node("planner_core", "Planner Core", "logic"));
        nodes.add(node("planner_validator", "Plan Validator", "logic"));
        nodes.add(node("task_dispatcher", "Task Dispatcher", "logic"));
        edges.add(edge("start", "planner_core", null));
        edges.add(edge("planner_core", "planner_validator", null));
        edges.add(edge("planner_validator", "task_dispatcher", null));
        for (Map<String, Object> agent : agents) {
            String id = text(agent.get("id"), "agent");
            nodes.add(node(id, text(agent.get("name"), id), "agent"));
            edges.add(edge("task_dispatcher", id, "dispatch"));
            edges.add(edge(id, "task_dispatcher", "next"));
        }
        appendFinal(nodes, edges, "task_dispatcher", finalizer);
        return graph(nodes, edges);
    }

    private Map<String, Object> supervisorDynamic(List<Map<String, Object>> agents, boolean finalizer) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        nodes.add(node("start", "Start", "start"));
        nodes.add(node("supervisor_intake", "Supervisor Intake", "logic"));
        nodes.add(node("delegation_policy", "Delegation Policy", "logic"));
        nodes.add(node("supervisor_review", "Supervisor Review", "logic"));
        edges.add(edge("start", "supervisor_intake", null));
        edges.add(edge("supervisor_intake", "delegation_policy", null));
        for (Map<String, Object> agent : agents) {
            String id = text(agent.get("id"), "agent");
            nodes.add(node(id, text(agent.get("name"), id), "agent"));
            edges.add(edge("delegation_policy", id, "delegate"));
            edges.add(edge(id, "supervisor_review", "report"));
        }
        edges.add(edge("supervisor_review", "delegation_policy", "continue"));
        appendFinal(nodes, edges, "supervisor_review", finalizer);
        return graph(nodes, edges);
    }

    private Map<String, Object> peerHandoff(List<Map<String, Object>> agents, boolean finalizer) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        nodes.add(node("start", "Start", "start"));
        nodes.add(node("first_owner_router", "First Owner Router", "logic"));
        nodes.add(node("peer_pool", "Peer Pool", "group"));
        nodes.add(node("handoff_decision", "Handoff Decision", "logic"));
        edges.add(edge("start", "first_owner_router", null));
        edges.add(edge("first_owner_router", "peer_pool", "owner"));
        for (Map<String, Object> agent : agents) {
            String id = text(agent.get("id"), "agent");
            nodes.add(node(id, text(agent.get("name"), id), "agent", "peer_pool"));
            edges.add(edge("peer_pool", id, "handoff"));
            edges.add(edge(id, "handoff_decision", "decide"));
        }
        edges.add(edge("handoff_decision", "peer_pool", "continue"));
        appendFinal(nodes, edges, "handoff_decision", finalizer);
        return graph(nodes, edges);
    }

    private void appendFinal(List<Map<String, Object>> nodes, List<Map<String, Object>> edges, String source, boolean finalizer) {
        if (finalizer) {
            nodes.add(node("finalize", "Finalizer", "final"));
            edges.add(edge(source, "finalize", null));
            nodes.add(node("end", "End", "end"));
            edges.add(edge("finalize", "end", null));
        } else {
            nodes.add(node("end", "End", "end"));
            edges.add(edge(source, "end", null));
        }
    }

    private void appendEnd(List<Map<String, Object>> nodes, List<Map<String, Object>> edges, boolean finalizer) {
        if (finalizer) {
            nodes.add(node("finalize", "Finalizer", "final"));
            nodes.add(node("end", "End", "end"));
            edges.add(edge("finalize", "end", null));
        } else {
            nodes.add(node("end", "End", "end"));
        }
    }

    private Map<String, Object> graph(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        return Map.of("nodes", nodes, "edges", edges);
    }

    private Map<String, Object> node(String id, String label, String kind) {
        return node(id, label, kind, null);
    }

    private Map<String, Object> node(String id, String label, String kind, String parentId) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("label", label);
        node.put("kind", kind);
        if (parentId != null) {
            node.put("parent_id", parentId);
        }
        return node;
    }

    private Map<String, Object> edge(String source, String target, String label) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("source", source);
        edge.put("target", target);
        if (label != null) {
            edge.put("label", label);
        }
        return edge;
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
