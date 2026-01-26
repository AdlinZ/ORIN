package com.adlin.orin.modules.knowledge.service.impl;

import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.service.RouterService;
import com.adlin.orin.modules.knowledge.service.StructuredService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StructuredServiceImpl implements StructuredService {

    private final RouterService routerService;
    // Mock schema storage (In real app, query Database Metadata)
    private final Map<String, List<Map<String, Object>>> agentSchemas = new HashMap<>();

    @Override
    public Map<String, Object> uploadDataTable(MultipartFile file, String tableName, String agentId) {
        log.info("Uploading structured data: {} into table: {}", file.getOriginalFilename(), tableName);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            // 1. Parse CSV Header
            String headerLine = reader.readLine();
            if (headerLine == null)
                throw new RuntimeException("Empty CSV file");

            String[] headers = headerLine.split(",");
            List<String> columns = Arrays.asList(headers);

            Map<String, Object> tableInfo = new HashMap<>();
            tableInfo.put("tableName", tableName);
            tableInfo.put("columns", columns);

            agentSchemas.computeIfAbsent(agentId, k -> new ArrayList<>()).add(tableInfo);

            // 3. TODO: Insert actual data rows into duckdb/sqlite
            return tableInfo;

        } catch (Exception e) {
            log.error("Failed to parse CSV", e);
            throw new RuntimeException("CSV upload failed", e);
        }
    }

    @Override
    public String generateSql(String agentId, String query) {
        log.info("Generating SQL for query: {}", query);

        List<Map<String, Object>> schemas = getDatabaseSchema(agentId);
        if (schemas.isEmpty()) {
            throw new RuntimeException("No structured data found for agent: " + agentId);
        }

        String schemaDesc = schemas.stream()
                .map(s -> "Table: " + s.get("tableName") + ", Columns: " + s.get("columns"))
                .collect(Collectors.joining("\n"));

        String systemPrompt = "You are a SQL expert. Convert the user query into a valid SQL statement based on the schema below.\n"
                +
                "Schema:\n" + schemaDesc + "\n" +
                "Output ONLY the SQL query. No markdown, no explanation.";

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo") // Default heavy model
                .messages(List.of(
                        ChatCompletionRequest.Message.builder().role("system").content(systemPrompt).build(),
                        ChatCompletionRequest.Message.builder().role("user").content(query).build()))
                .temperature(0.0)
                .build();

        var provider = routerService.selectProviderByModel("gpt-3.5-turbo", request)
                .orElseThrow(() -> new RuntimeException("No AI provider available for SQL generation"));

        try {
            var response = provider.chatCompletion(request).block();
            if (response != null && !response.getChoices().isEmpty()) {
                String sql = response.getChoices().get(0).getMessage().getContent();
                return sql.replaceAll("```sql", "").replaceAll("```", "").trim();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate SQL", e);
        }

        return "";
    }

    @Override
    public List<Map<String, Object>> executeQuery(String agentId, String sql) {
        log.info("Executing SQL: {}", sql);
        // TODO: Execute against actual DB
        // For prototype, return mock success
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> getDatabaseSchema(String agentId) {
        return agentSchemas.getOrDefault(agentId, Collections.emptyList());
    }
}
