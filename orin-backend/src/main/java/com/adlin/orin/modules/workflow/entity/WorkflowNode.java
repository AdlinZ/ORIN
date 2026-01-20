package com.adlin.orin.modules.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workflow_nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workflow_id")
    private String workflowId;

    private String name;

    /**
     * Node Type: AGENT, KNOWLEDGE, API, CONDITION, START, END
     */
    private String type;

    /**
     * JSON Configuration specific to the node type.
     * e.g., for AGENT: { "agentId": "xxx" }
     * for KNOWLEDGE: { "kbId": "xxx", "queryVariable": "xxx" }
     */
    @Column(columnDefinition = "TEXT")
    private String configuration;

    /**
     * List of next node IDs (comma separated or JSON)
     * For CONDITION node, might be: "true:nodeId1,false:nodeId2"
     */
    @Column(name = "next_nodes", columnDefinition = "TEXT")
    private String nextNodes;

    /**
     * Position in UI (JSON: {x: 100, y: 100})
     */
    @Column(columnDefinition = "TEXT")
    private String position;
}
