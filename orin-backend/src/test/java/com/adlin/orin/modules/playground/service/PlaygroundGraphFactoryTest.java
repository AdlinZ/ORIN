package com.adlin.orin.modules.playground.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundGraphFactoryTest {

    private final PlaygroundGraphFactory factory = new PlaygroundGraphFactory();

    @Test
    void buildsRouterSpecialistsGraph() {
        Map<String, Object> graph = factory.buildGraph(
                Map.of("type", "router_specialists", "finalizer_enabled", true),
                List.of(agent("agent_a", "Researcher"), agent("agent_b", "Reviewer"))
        );

        List<?> nodes = (List<?>) graph.get("nodes");
        List<?> edges = (List<?>) graph.get("edges");

        assertTrue(nodes.stream().anyMatch(node -> "router".equals(((Map<?, ?>) node).get("id"))));
        assertTrue(nodes.stream().anyMatch(node -> "finalize".equals(((Map<?, ?>) node).get("id"))));
        assertTrue(edges.stream().anyMatch(edge -> "agent_a".equals(((Map<?, ?>) edge).get("target"))));
    }

    @Test
    void buildsPeerHandoffGraphWithPeerPool() {
        Map<String, Object> graph = factory.buildGraph(
                Map.of("type", "peer_handoff", "finalizer_enabled", false),
                List.of(agent("agent_a", "Designer"), agent("agent_b", "Engineer"))
        );

        List<?> nodes = (List<?>) graph.get("nodes");

        assertTrue(nodes.stream().anyMatch(node -> "peer_pool".equals(((Map<?, ?>) node).get("id"))));
        assertEquals("peer_pool", nodes.stream()
                .map(node -> (Map<?, ?>) node)
                .filter(node -> "agent_a".equals(node.get("id")))
                .findFirst()
                .orElseThrow()
                .get("parent_id"));
    }

    private Map<String, Object> agent(String id, String name) {
        return Map.of("id", id, "name", name);
    }
}
