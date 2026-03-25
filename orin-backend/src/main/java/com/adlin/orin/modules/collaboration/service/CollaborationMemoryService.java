package com.adlin.orin.modules.collaboration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 协作共享记忆服务 - 提供协作黑板、执行游标、幂等键、检查点功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis 键前缀
    private static final String PREFIX = "collab:memory:";
    private static final String CURSOR_PREFIX = "collab:cursor:";
    private static final String IDEMPOTENT_PREFIX = "collab:idemp:";
    private static final String CHECKPOINT_PREFIX = "collab:checkpoint:";
    private static final String BLACKBOARD_PREFIX = "collab:blackboard:";

    // 默认 TTL
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration CURSOR_TTL = Duration.ofHours(1);
    private static final Duration IDEMPOTENT_TTL = Duration.ofMinutes(10);

    // ========== 共享黑板（Shared Blackboard）==========

    /**
     * 写入黑板数据
     */
    public void writeToBlackboard(String packageId, String key, Object value) {
        String redisKey = BLACKBOARD_PREFIX + packageId + ":" + key;
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(redisKey, jsonValue, DEFAULT_TTL);
            log.debug("Written to blackboard: packageId={}, key={}", packageId, key);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize value for blackboard: key={}", key, e);
            throw new RuntimeException("Failed to write to blackboard", e);
        }
    }

    /**
     * 读取黑板数据
     */
    public Optional<Object> readFromBlackboard(String packageId, String key) {
        String redisKey = BLACKBOARD_PREFIX + packageId + ":" + key;
        String value = redisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, Object.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize from blackboard: key={}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 读取黑板所有数据
     */
    public Map<String, Object> readAllBlackboard(String packageId) {
        String pattern = BLACKBOARD_PREFIX + packageId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>();
        for (String key : keys) {
            String dataKey = key.substring(key.lastIndexOf(":") + 1);
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                try {
                    result.put(dataKey, objectMapper.readValue(value, Object.class));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize key: {}", dataKey);
                }
            }
        }
        return result;
    }

    /**
     * 删除黑板数据
     */
    public void deleteFromBlackboard(String packageId, String key) {
        String redisKey = BLACKBOARD_PREFIX + packageId + ":" + key;
        redisTemplate.delete(redisKey);
    }

    /**
     * 清空黑板
     */
    public void clearBlackboard(String packageId) {
        String pattern = BLACKBOARD_PREFIX + packageId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ========== 执行游标（Execution Cursor）==========

    /**
     * 更新执行游标
     */
    public void updateCursor(String packageId, String subtaskId, String status) {
        String redisKey = CURSOR_PREFIX + packageId;
        Map<String, String> cursor = getCursorData(redisKey);
        cursor.put("currentSubtask", subtaskId);
        cursor.put("status", status);
        cursor.put("lastUpdate", String.valueOf(System.currentTimeMillis()));

        try {
            String json = objectMapper.writeValueAsString(cursor);
            redisTemplate.opsForValue().set(redisKey, json, CURSOR_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to update cursor for package: {}", packageId, e);
        }
    }

    /**
     * 获取执行游标
     */
    public Map<String, String> getCursor(String packageId) {
        String redisKey = CURSOR_PREFIX + packageId;
        return getCursorData(redisKey);
    }

    private Map<String, String> getCursorData(String redisKey) {
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    /**
     * 推进游标到下一个子任务
     */
    public String advanceToNextSubtask(String packageId, List<String> subtaskOrder) {
        Map<String, String> cursor = getCursor(packageId);
        String currentSubtaskId = cursor.get("currentSubtask");

        int currentIndex = currentSubtaskId != null ? subtaskOrder.indexOf(currentSubtaskId) : -1;
        int nextIndex = currentIndex + 1;

        if (nextIndex < subtaskOrder.size()) {
            String nextSubtaskId = subtaskOrder.get(nextIndex);
            updateCursor(packageId, nextSubtaskId, "RUNNING");
            return nextSubtaskId;
        }
        return null;
    }

    // ========== 幂等键（Idempotent Key）==========

    /**
     * 检查操作是否已执行（幂等性检查）
     */
    public boolean isExecuted(String operationId) {
        String redisKey = IDEMPOTENT_PREFIX + operationId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    /**
     * 标记操作已执行
     */
    public void markExecuted(String operationId) {
        String redisKey = IDEMPOTENT_PREFIX + operationId;
        redisTemplate.opsForValue().set(redisKey, "1", IDEMPOTENT_TTL);
    }

    /**
     * 检查并标记（原子操作）
     */
    public boolean checkAndMark(String operationId) {
        String redisKey = IDEMPOTENT_PREFIX + operationId;
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", IDEMPOTENT_TTL);
        return result != null && !result; // 如果已存在返回 true
    }

    // ========== 回放检查点（Checkpoint）==========

    /**
     * 保存检查点
     */
    public void saveCheckpoint(String packageId, String checkpointId, Map<String, Object> data) {
        String redisKey = CHECKPOINT_PREFIX + packageId + ":" + checkpointId;
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(redisKey, json, DEFAULT_TTL);
            log.debug("Saved checkpoint: packageId={}, checkpointId={}", packageId, checkpointId);
        } catch (JsonProcessingException e) {
            log.error("Failed to save checkpoint: {}", checkpointId, e);
            throw new RuntimeException("Failed to save checkpoint", e);
        }
    }

    /**
     * 读取检查点
     */
    public Optional<Map<String, Object>> readCheckpoint(String packageId, String checkpointId) {
        String redisKey = CHECKPOINT_PREFIX + packageId + ":" + checkpointId;
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, Map.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to read checkpoint: {}", checkpointId, e);
            return Optional.empty();
        }
    }

    /**
     * 获取所有检查点
     */
    public List<String> listCheckpoints(String packageId) {
        String pattern = CHECKPOINT_PREFIX + packageId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        return keys.stream()
                .map(k -> k.substring(k.lastIndexOf(":") + 1))
                .collect(Collectors.toList());
    }

    /**
     * 删除检查点
     */
    public void deleteCheckpoint(String packageId, String checkpointId) {
        String redisKey = CHECKPOINT_PREFIX + packageId + ":" + checkpointId;
        redisTemplate.delete(redisKey);
    }

    /**
     * 回滚到指定检查点
     */
    public Optional<Map<String, Object>> rollbackToCheckpoint(String packageId, String checkpointId) {
        return readCheckpoint(packageId, checkpointId);
    }

    // ========== 协作包级别操作 ==========

    /**
     * 删除协作包相关的所有 Redis 数据
     */
    public void cleanupPackage(String packageId) {
        String[] patterns = {
            BLACKBOARD_PREFIX + packageId + ":*",
            CURSOR_PREFIX + packageId,
            CHECKPOINT_PREFIX + packageId + ":*"
        };

        for (String pattern : patterns) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        log.info("Cleaned up Redis data for package: {}", packageId);
    }

    /**
     * 获取协作包相关的 Redis 统计
     */
    public Map<String, Object> getPackageStats(String packageId) {
        Map<String, Object> stats = new HashMap<>();

        // 黑板键数
        String blackboardPattern = BLACKBOARD_PREFIX + packageId + ":*";
        Set<String> blackboardKeys = redisTemplate.keys(blackboardPattern);
        stats.put("blackboardKeys", blackboardKeys != null ? blackboardKeys.size() : 0);

        // 检查点数
        String checkpointPattern = CHECKPOINT_PREFIX + packageId + ":*";
        Set<String> checkpointKeys = redisTemplate.keys(checkpointPattern);
        stats.put("checkpoints", checkpointKeys != null ? checkpointKeys.size() : 0);

        // 游标
        String cursorKey = CURSOR_PREFIX + packageId;
        stats.put("hasCursor", redisTemplate.hasKey(cursorKey));

        return stats;
    }
}