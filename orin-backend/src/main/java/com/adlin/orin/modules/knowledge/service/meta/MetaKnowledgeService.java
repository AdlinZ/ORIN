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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetaKnowledgeService implements com.adlin.orin.modules.knowledge.service.MetaMemoryService {

        private final PromptTemplateRepository promptTemplateRepository;
        private final AgentMemoryRepository agentMemoryRepository;
        private final com.adlin.orin.gateway.service.RouterService routerService;
        private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

        // --- MetaMemoryService Implementation ---

        @Override
        public String getSystemPrompt(String agentId) {
                return assembleSystemPrompt(agentId);
        }

        @Override
        public List<Map<String, Object>> getShortTermMemory(String agentId, String sessionId, int limit) {
                String key = "orin:memory:short:" + agentId + ":" + sessionId;
                // Get the last N messages. List is stored as [msg1, msg2, ...] so we take from
                // end.
                // Or if we push to right, we take from end.
                // Assuming RPUSH, we want range (size - limit) to -1.
                // To simplify, just get all and stream limit, or use range 0 -1 if size is
                // small.
                // Best practice for "recent" is usually LINDEX or sorting, but standard is
                // range.
                // Let's assume we store them in chronological order.
                List<String> rawMessages = redisTemplate.opsForList().range(key, -limit, -1);

                if (rawMessages == null) {
                        return java.util.Collections.emptyList();
                }

                return rawMessages.stream().map(json -> {
                        try {
                                return objectMapper.readValue(json,
                                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                                                });
                        } catch (Exception e) {
                                log.error("Failed to parse memory message", e);
                                return null;
                        }
                }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        }

        /**
         * Save a message to short-term memory (Redis) with sliding window.
         * Default window size is 50 if not specified.
         */
        public void saveShortTermMemory(String agentId, String sessionId, String role, String content) {
                String key = "orin:memory:short:" + agentId + ":" + sessionId;
                try {
                        Map<String, Object> message = new java.util.HashMap<>();
                        message.put("role", role);
                        message.put("content", content);
                        message.put("timestamp", LocalDateTime.now().toString());

                        String json = objectMapper.writeValueAsString(message);
                        redisTemplate.opsForList().rightPush(key, json);

                        // Sliding window: keep only last 50 messages
                        redisTemplate.opsForList().trim(key, -50, -1);

                        // Set TTL to 1 day to prevent stale data accumulation
                        redisTemplate.expire(key, 1, java.util.concurrent.TimeUnit.DAYS);
                } catch (Exception e) {
                        log.error("Failed to save short-term memory", e);
                }
        }

        @Override
        public List<String> getLongTermMemory(String agentId, String query) {
                // Logic to search vector DB or specific memory tables
                // For now using basic keyword search in DB if we had it, or just empty
                return java.util.Collections.emptyList();
        }

        @Override
        public void saveLongTermMemory(String agentId, String key, String value) {
                saveMemory(agentId, key, value);
        }

        // --- Existing Methods ---

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
         * Get prompt templates for a specific agent and user.
         * Returns system prompts (userId is null) AND user's specific prompts.
         */
        public List<Map<String, Object>> getPromptTemplatesByUser(String agentId, String userId) {
                // Fetch all prompts for the agent first
                List<PromptTemplate> allTemplates = promptTemplateRepository.findByAgentId(agentId);

                return allTemplates.stream()
                                // Filter: Include if Global (userId is null/empty) OR belongs to current user
                                .filter(t -> (t.getUserId() == null || t.getUserId().isEmpty())
                                                || (userId != null && userId.equals(t.getUserId())))
                                .map(t -> {
                                        Map<String, Object> map = new java.util.HashMap<>();
                                        map.put("id", t.getId());
                                        map.put("name", t.getName());
                                        map.put("description", t.getDescription());
                                        map.put("content", t.getContent());
                                        map.put("type", t.getType());
                                        map.put("isActive", t.getIsActive());
                                        map.put("userId", t.getUserId());
                                        map.put("createdAt",
                                                        t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
                                        return map;
                                })
                                .collect(Collectors.toList());
        }

        /**
         * Create or Update a Prompt Template
         */
        @Transactional
        @Override
        public PromptTemplate savePromptTemplate(PromptTemplate template) {
                if (template.getId() == null) {
                        template.setId(UUID.randomUUID().toString());
                        template.setCreatedAt(LocalDateTime.now());
                }
                if (template.getIsActive() == null) {
                        template.setIsActive(true);
                }
                return promptTemplateRepository.save(template);
        }

        public PromptTemplate savePromptTemplateAndReturn(PromptTemplate template) {
                savePromptTemplate(template);
                return template;
        }

        @Transactional
        public void deletePromptTemplate(String id) {
                promptTemplateRepository.deleteById(id);
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

        @Transactional
        public void deleteMemoryEntry(String id) {
                agentMemoryRepository.deleteById(id);
        }

        @Transactional
        public void clearLongTermMemory(String agentId) {
                agentMemoryRepository.deleteByAgentId(agentId);
        }

        @Transactional
        public void clearPromptTemplates(String agentId) {
                promptTemplateRepository.deleteByAgentId(agentId);
        }

        public void clearAllShortTermMemory(String agentId) {
                Set<String> keys = redisTemplate.keys("orin:memory:short:" + agentId + ":*");
                if (keys != null && !keys.isEmpty()) {
                        redisTemplate.delete(keys);
                }
        }

        public List<String> getShortTermSessions(String agentId) {
                Set<String> keys = redisTemplate.keys("orin:memory:short:" + agentId + ":*");
                if (keys == null || keys.isEmpty())
                        return java.util.Collections.emptyList();
                return keys.stream()
                                .map(k -> k.replace("orin:memory:short:" + agentId + ":", ""))
                                .collect(Collectors.toList());
        }

        public void clearShortTermMemory(String agentId, String sessionId) {
                String key = "orin:memory:short:" + agentId + ":" + sessionId;
                redisTemplate.delete(key);
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
                List<PromptTemplate> instructionTemplates = templates.stream()
                                .filter(t -> "INSTRUCTION".equals(t.getType()) && t.getIsActive())
                                .collect(Collectors.toList());

                if (!instructionTemplates.isEmpty()) {
                        systemPrompt.append("## Instructions\n");
                        instructionTemplates
                                        .forEach(t -> systemPrompt.append("- ").append(t.getContent()).append("\n"));
                }

                // 3. Long Term Memory Injection (Context)
                // A. Explicit Key-Value Memory (Current Implementation)
                List<AgentMemory> moments = agentMemoryRepository.findByAgentId(agentId);
                if (!moments.isEmpty()) {
                        systemPrompt.append("\n## Context / Memory (Structured)\n");
                        moments.forEach(m -> systemPrompt.append("- ").append(m.getKey()).append(": ")
                                        .append(m.getValue())
                                        .append("\n"));
                }

                return systemPrompt.toString();
        }

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
