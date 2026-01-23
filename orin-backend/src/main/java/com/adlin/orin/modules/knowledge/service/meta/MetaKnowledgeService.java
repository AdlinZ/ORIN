package com.adlin.orin.modules.knowledge.service.meta;

import com.adlin.orin.modules.knowledge.entity.meta.AgentMemory;
import com.adlin.orin.modules.knowledge.entity.meta.PromptTemplate;
import com.adlin.orin.modules.knowledge.repository.meta.AgentMemoryRepository;
import com.adlin.orin.modules.knowledge.repository.meta.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetaKnowledgeService {

        private final PromptTemplateRepository promptTemplateRepository;
        private final AgentMemoryRepository agentMemoryRepository;

        /**
         * Get prompt templates for a specific agent.
         */
        public List<Map<String, Object>> getPromptTemplates(String agentId) {
                return promptTemplateRepository.findByAgentId(agentId).stream()
                                .map(t -> {
                                        Map<String, Object> map = new java.util.HashMap<>();
                                        map.put("id", t.getId());
                                        map.put("name", t.getName());
                                        map.put("content", t.getContent());
                                        map.put("type", t.getType());
                                        map.put("isActive", t.getIsActive());
                                        return map;
                                })
                                .collect(Collectors.toList());
        }

        /**
         * Create or Update a Prompt Template
         */
        @Transactional
        public PromptTemplate savePromptTemplate(PromptTemplate template) {
                if (template.getId() == null) {
                        template.setId(UUID.randomUUID().toString());
                        template.setCreatedAt(LocalDateTime.now());
                }
                return promptTemplateRepository.save(template);
        }

        /**
         * Get memory configuration or stored memory items.
         */
        public List<Map<String, Object>> getAgentMemory(String agentId) {
                return agentMemoryRepository.findByAgentId(agentId).stream()
                                .map(m -> {
                                        Map<String, Object> map = new java.util.HashMap<>();
                                        map.put("id", m.getId());
                                        map.put("key", m.getKey());
                                        map.put("value", m.getValue());
                                        map.put("updatedAt", m.getUpdatedAt().toString());
                                        return map;
                                })
                                .collect(Collectors.toList());
        }

        /**
         * Save a memory item
         */
        @Transactional
        public void saveMemory(String agentId, String key, String value) {
                AgentMemory memory = agentMemoryRepository.findByAgentIdAndKey(agentId, key)
                                .orElse(AgentMemory.builder()
                                                .id(UUID.randomUUID().toString())
                                                .agentId(agentId)
                                                .key(key)
                                                .build());

                memory.setValue(value);
                memory.setUpdatedAt(LocalDateTime.now());
                agentMemoryRepository.save(memory);
                log.info("Saved memory for agent: {} key: {}", agentId, key);
        }

        /**
         * Assemble final System Prompt for Agent execution
         */
        public String assembleSystemPrompt(String agentId) {
                StringBuilder systemPrompt = new StringBuilder();

                // 1. Role Definition
                List<PromptTemplate> templates = promptTemplateRepository.findByAgentId(agentId);
                templates.stream()
                                .filter(t -> "ROLE".equals(t.getType()) && t.getIsActive())
                                .findFirst()
                                .ifPresent(t -> systemPrompt.append(t.getContent()).append("\n\n"));

                // 2. Instructions
                systemPrompt.append("## Instructions\n");
                templates.stream()
                                .filter(t -> "INSTRUCTION".equals(t.getType()) && t.getIsActive())
                                .forEach(t -> systemPrompt.append("- ").append(t.getContent()).append("\n"));

                // 3. Long Term Memory Injection (Context)
                // A. Explicit Key-Value Memory (Current Implementation)
                List<AgentMemory> moments = agentMemoryRepository.findByAgentId(agentId);
                if (!moments.isEmpty()) {
                        systemPrompt.append("\n## Context / Memory (Structured)\n");
                        moments.forEach(m -> systemPrompt.append("- ").append(m.getKey()).append(": ")
                                        .append(m.getValue())
                                        .append("\n"));
                }

                // B. Vector Retrieval Memory (Placeholder for RAG)
                // TODO: [Plan] Implement RAG pipeline: 1. Vector Search, 2. Re-ranking, 3.
                // Context Window Management
                // List<String> relevantDocs = vectorService.search(agentId, query);
                // if (!relevantDocs.isEmpty()) {
                // systemPrompt.append("\n## Context / Relevant Docs (Vector)\n");
                // relevantDocs.forEach(doc -> systemPrompt.append(doc).append("\n"));
                // }

                return systemPrompt.toString();
        }

        private final com.adlin.orin.gateway.service.RouterService routerService;
        private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        /**
         * Extract memory from conversation history using LLM.
         */
        @Transactional
        public void extractMemory(String agentId, String conversationHistory, String modelName) {
                // 1. Get Agent Model Info passed from controller to avoid circular dependency
                String finalModelName = (modelName == null || modelName.isEmpty()) ? "gpt-3.5-turbo" : modelName;

                // 2. Construct Prompt
                String systemPrompt = "You are a memory extraction expert. \n" +
                                "Analyze the conversation below and extract key facts, user preferences, or important rules.\n"
                                +
                                "Output ONLY a JSON array of objects with 'key' and 'value' fields.\n" +
                                "Example: [{\"key\": \"User_Location\", \"value\": \"Beijing\"}, {\"key\": \"Preferred_Language\", \"value\": \"Java\"}]\n"
                                +
                                "Do not output any markdown code blocks.";

                com.adlin.orin.gateway.dto.ChatCompletionRequest request = com.adlin.orin.gateway.dto.ChatCompletionRequest
                                .builder()
                                .model(finalModelName)
                                .messages(java.util.List.of(
                                                com.adlin.orin.gateway.dto.ChatCompletionRequest.Message.builder()
                                                                .role("system")
                                                                .content(systemPrompt)
                                                                .build(),
                                                com.adlin.orin.gateway.dto.ChatCompletionRequest.Message.builder()
                                                                .role("user")
                                                                .content(conversationHistory)
                                                                .build()))
                                .temperature(0.3) // Low temp for deterministic output
                                .build();

                // 3. Call LLM (Blocking for background task simplicity)
                log.info("Extracting memory for agent {} using model {}", agentId, finalModelName);
                var provider = routerService.selectProviderByModel(finalModelName, request)
                                .orElseThrow(() -> new RuntimeException(
                                                "No provider found for model: " + finalModelName));

                try {
                        var response = provider.chatCompletion(request).block();
                        if (response != null && !response.getChoices().isEmpty()) {
                                String content = response.getChoices().get(0).getMessage().getContent();
                                // Clean up markdown if present
                                content = content.replace("```json", "").replace("```", "").trim();

                                List<Map<String, String>> memories = objectMapper.readValue(content,
                                                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {
                                                });

                                for (Map<String, String> mem : memories) {
                                        saveMemory(agentId, mem.get("key"), mem.get("value"));
                                }
                                log.info("Successfully extracted {} memories for agent {}", memories.size(), agentId);
                        }
                } catch (Exception e) {
                        log.error("Failed to extract memory: {}", e.getMessage());
                        throw new RuntimeException("Memory extraction failed", e);
                }
        }
}
