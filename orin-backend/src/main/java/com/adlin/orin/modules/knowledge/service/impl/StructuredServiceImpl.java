package com.adlin.orin.modules.knowledge.service.impl;

import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.service.RouterService;
import com.adlin.orin.modules.knowledge.entity.StructuredDataTable;
import com.adlin.orin.modules.knowledge.repository.StructuredDataTableRepository;
import com.adlin.orin.modules.knowledge.service.StructuredService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 结构化数据服务 - 支持 CSV 上传和 SQL 查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructuredServiceImpl implements StructuredService {

    private final RouterService routerService;
    private final JdbcTemplate jdbcTemplate;
    private final StructuredDataTableRepository tableRepository;

    @Override
    @Transactional
    public Map<String, Object> uploadDataTable(MultipartFile file, String tableName, String agentId) {
        log.info("Uploading structured data: {} into table: {}", file.getOriginalFilename(), tableName);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            // 1. Parse CSV Header
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new RuntimeException("Empty CSV file");
            }

            String[] headers = headerLine.split(",");
            List<String> columns = Arrays.asList(headers);

            // 2. 创建数据库表
            createTableIfNotExists(tableName, columns);

            // 3. 插入数据
            int rowCount = 0;
            String line;
            List<Object[]> batchValues = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length == columns.size()) {
                    // 转义字符串值
                    Object[] row = new Object[values.length];
                    for (int i = 0; i < values.length; i++) {
                        row[i] = values[i].trim();
                    }
                    batchValues.add(row);
                    
                    // 批量插入
                    if (batchValues.size() >= 100) {
                        batchInsert(tableName, columns, batchValues);
                        rowCount += batchValues.size();
                        batchValues.clear();
                    }
                }
            }
            
            // 插入剩余数据
            if (!batchValues.isEmpty()) {
                batchInsert(tableName, columns, batchValues);
                rowCount += batchValues.size();
            }

            // 4. 保存表元数据
            StructuredDataTable tableInfo = tableRepository.findByTableNameAndAgentId(tableName, agentId)
                    .orElse(StructuredDataTable.builder()
                            .tableName(tableName)
                            .agentId(agentId)
                            .build());
            
            tableInfo.setColumnCount(columns.size());
            tableInfo.setRowCount(rowCount);
            tableInfo.setColumns(String.join(",", columns));
            tableInfo.setUpdatedAt(new java.util.Date());
            tableRepository.save(tableInfo);

            Map<String, Object> result = new HashMap<>();
            result.put("tableName", tableName);
            result.put("columns", columns);
            result.put("rowCount", rowCount);
            result.put("success", true);
            
            log.info("CSV upload completed: {} rows inserted into {}", rowCount, tableName);
            return result;

        } catch (Exception e) {
            log.error("Failed to parse CSV", e);
            throw new RuntimeException("CSV upload failed: " + e.getMessage(), e);
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

        String systemPrompt = "You are a SQL expert. Convert the user query into a valid SQL statement based on the schema below.\n" +
                "Schema:\n" + schemaDesc + "\n" +
                "Output ONLY the SQL query. No markdown, no explanation.";

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
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
        
        // 安全检查：只允许 SELECT 查询
        String trimmedSql = sql.trim().toUpperCase();
        if (!trimmedSql.startsWith("SELECT")) {
            throw new RuntimeException("Only SELECT queries are allowed");
        }
        
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("SQL execution failed", e);
            throw new RuntimeException("SQL execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getDatabaseSchema(String agentId) {
        List<StructuredDataTable> tables = tableRepository.findByAgentId(agentId);
        return tables.stream()
                .map(t -> {
                    Map<String, Object> schema = new HashMap<>();
                    schema.put("tableName", t.getTableName());
                    schema.put("columns", t.getColumns().split(","));
                    schema.put("rowCount", t.getRowCount());
                    return schema;
                })
                .collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    private void createTableIfNotExists(String tableName, List<String> columns) {
        // 表名安全处理
        String safeTableName = sanitizeTableName(tableName);
        
        // 检查表是否存在
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + safeTableName, Integer.class);
            log.info("Table {} already exists", safeTableName);
            return;
        } catch (Exception e) {
            // 表不存在，继续创建
        }
        
        // 构建 CREATE TABLE 语句
        StringBuilder createSql = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        createSql.append(safeTableName).append(" (");
        
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) createSql.append(", ");
            createSql.append(sanitizeColumnName(columns.get(i))).append(" VARCHAR(500)");
        }
        
        createSql.append(")");
        
        jdbcTemplate.execute(createSql.toString());
        log.info("Created table: {}", safeTableName);
    }

    private void batchInsert(String tableName, List<String> columns, List<Object[]> values) {
        String safeTableName = sanitizeTableName(tableName);
        String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        String columnNames = String.join(",", columns.stream()
                .map(this::sanitizeColumnName)
                .collect(Collectors.toList()));
        
        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", 
                safeTableName, columnNames, placeholders);
        
        jdbcTemplate.batchUpdate(sql, values);
    }

    private String sanitizeTableName(String name) {
        // 只允许字母、数字、下划线
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String sanitizeColumnName(String name) {
        // 只允许字母、数字、下划线
        return name.trim().replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
