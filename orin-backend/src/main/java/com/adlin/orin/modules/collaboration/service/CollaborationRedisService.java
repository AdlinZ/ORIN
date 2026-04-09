package com.adlin.orin.modules.collaboration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 协作 Redis 服务 - 新版键规范
 *
 * 新键规范（按总计划 12.1）：
 * - collab:{packageId}:ctx          - 统一上下文
 * - collab:{packageId}:branch_counter - 并行分支计数器
 * - collab:{packageId}:lock:{subTaskId} - 分布式锁
 * - collab:{packageId}:checkpoint:{id}  - 检查点
 * - collab:{packageId}:cursor        - 执行光标
 * - collab:idemp:{packageId}:{subTaskId}:{attempt} - 幂等键
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationRedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 新版键前缀
    private static final String CTX_PREFIX = "collab:%s:ctx";
    private static final String BRANCH_COUNTER_PREFIX = "collab:%s:branch_counter";
    private static final String LOCK_PREFIX = "collab:%s:lock:%s";
    private static final String CHECKPOINT_PREFIX = "collab:%s:checkpoint:%s";
    private static final String CURSOR_PREFIX = "collab:%s:cursor";
    private static final String IDEMPOTENT_PREFIX = "collab:idemp:%s:%s:%d";

    // 旧版前缀（用于 dual-write 迁移）
    private static final String LEGACY_BLACKBOARD_PREFIX = "collab:blackboard:";

    // TTL
    private static final Duration CTX_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final Duration IDEMPOTENT_TTL = Duration.ofMinutes(10);

    // ========== 统一上下文（New Schema）==========

    /**
     * 保存统一上下文
     */
    public void saveContext(String packageId, Map<String, Object> context) {
        String key = String.format(CTX_PREFIX, packageId);
        try {
            String json = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(key, json, CTX_TTL);
            log.debug("Saved context for package: {}", packageId);
        } catch (JsonProcessingException e) {
            log.error("Failed to save context for package: {}", packageId, e);
            throw new RuntimeException("Failed to save context", e);
        }
    }

    /**
     * 加载统一上下文
     */
    public Optional<Map<String, Object>> loadContext(String packageId) {
        String key = String.format(CTX_PREFIX, packageId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
        } catch (JsonProcessingException e) {
            log.error("Failed to load context for package: {}", packageId, e);
            return Optional.empty();
        }
    }

    /**
     * 更新上下文字段
     */
    public void updateContextField(String packageId, String field, Object value) {
        Map<String, Object> ctx = loadContext(packageId).orElse(new HashMap<>());
        ctx.put(field, value);
        saveContext(packageId, ctx);
    }

    /**
     * 获取上下文特定字段
     */
    public Optional<Object> getContextField(String packageId, String field) {
        return loadContext(packageId)
                .map(ctx -> ctx.get(field));
    }

    // ========== 分布式锁 ==========

    /**
     * 获取分布式锁
     */
    public boolean acquireLock(String packageId, String subTaskId, Duration timeout) {
        return acquireLockWithToken(packageId, subTaskId, "locked", timeout);
    }

    /**
     * 获取分布式锁（带 token）
     */
    public boolean acquireLockWithToken(String packageId, String subTaskId, String token, Duration timeout) {
        String lockKey = String.format(LOCK_PREFIX, packageId, subTaskId);
        Duration effectiveTimeout = timeout != null ? timeout : LOCK_TTL;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, effectiveTimeout);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 释放分布式锁
     */
    public void releaseLock(String packageId, String subTaskId) {
        String lockKey = String.format(LOCK_PREFIX, packageId, subTaskId);
        redisTemplate.delete(lockKey);
        log.debug("Released lock: {}", lockKey);
    }

    /**
     * 释放分布式锁（token 校验）
     */
    public boolean releaseLockWithToken(String packageId, String subTaskId, String token) {
        String lockKey = String.format(LOCK_PREFIX, packageId, subTaskId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "  return redis.call('del', KEYS[1]) " +
                "else " +
                "  return 0 " +
                "end"
        );
        Long deleted = redisTemplate.execute(script, List.of(lockKey), token);
        return deleted != null && deleted > 0;
    }

    /**
     * 尝试获取锁（非阻塞）
     */
    public boolean tryLock(String packageId, String subTaskId) {
        String lockKey = String.format(LOCK_PREFIX, packageId, subTaskId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    // ========== 并行分支计数器 ==========

    /**
     * 递增分支计数器
     */
    public long incrementBranchCounter(String packageId) {
        String key = String.format(BRANCH_COUNTER_PREFIX, packageId);
        Long counter = redisTemplate.opsForValue().increment(key);
        if (counter != null && counter == 1L) {
            redisTemplate.expire(key, CTX_TTL);
        }
        return counter != null ? counter : 0;
    }

    /**
     * 原子写入分支结果并递增分支计数
     */
    public long writeBranchResultAndIncrement(String packageId, String subTaskId, Object result) {
        String ctxKey = String.format(CTX_PREFIX, packageId);
        String counterKey = String.format(BRANCH_COUNTER_PREFIX, packageId);
        String resultField = "branch_result:" + subTaskId;

        String resultJson;
        try {
            resultJson = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize branch result", e);
        }

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "local ctxRaw = redis.call('GET', KEYS[1]) " +
                "local ctx = {} " +
                "if ctxRaw and ctxRaw ~= '' then " +
                "  local ok, decoded = pcall(cjson.decode, ctxRaw) " +
                "  if ok and type(decoded) == 'table' then ctx = decoded end " +
                "end " +
                "ctx[ARGV[1]] = cjson.decode(ARGV[2]) " +
                "redis.call('SET', KEYS[1], cjson.encode(ctx), 'EX', ARGV[3]) " +
                "local counter = redis.call('INCR', KEYS[2]) " +
                "if counter == 1 then redis.call('EXPIRE', KEYS[2], ARGV[3]) end " +
                "return counter"
        );

        Long counter = redisTemplate.execute(
                script,
                List.of(ctxKey, counterKey),
                resultField,
                resultJson,
                String.valueOf(CTX_TTL.toSeconds())
        );
        return counter != null ? counter : 0L;
    }

    /**
     * 获取当前分支计数器值
     */
    public long getBranchCounter(String packageId) {
        String key = String.format(BRANCH_COUNTER_PREFIX, packageId);
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0;
    }

    /**
     * 重置分支计数器
     */
    public void resetBranchCounter(String packageId) {
        String key = String.format(BRANCH_COUNTER_PREFIX, packageId);
        redisTemplate.delete(key);
    }

    // ========== 检查点（新版）==========

    /**
     * 保存检查点
     */
    public void saveCheckpoint(String packageId, String checkpointId, Map<String, Object> data) {
        String key = String.format(CHECKPOINT_PREFIX, packageId, checkpointId);
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, CTX_TTL);
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
        String key = String.format(CHECKPOINT_PREFIX, packageId, checkpointId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
        } catch (JsonProcessingException e) {
            log.error("Failed to read checkpoint: {}", checkpointId, e);
            return Optional.empty();
        }
    }

    /**
     * 获取所有检查点 ID
     */
    public Set<String> listCheckpoints(String packageId) {
        String pattern = String.format("collab:%s:checkpoint:*", packageId);
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        return keys;
    }

    /**
     * 删除检查点
     */
    public void deleteCheckpoint(String packageId, String checkpointId) {
        String key = String.format(CHECKPOINT_PREFIX, packageId, checkpointId);
        redisTemplate.delete(key);
    }

    // ========== 执行光标（新版）==========

    /**
     * 保存执行光标
     */
    public void saveCursor(String packageId, Map<String, String> cursor) {
        String key = String.format(CURSOR_PREFIX, packageId);
        try {
            String json = objectMapper.writeValueAsString(cursor);
            redisTemplate.opsForValue().set(key, json, CTX_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to save cursor for package: {}", packageId, e);
        }
    }

    /**
     * 获取执行光标
     */
    public Optional<Map<String, String>> getCursor(String packageId) {
        String key = String.format(CURSOR_PREFIX, packageId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, new TypeReference<Map<String, String>>() {}));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    // ========== 幂等键（增强版）==========

    /**
     * 检查任务是否已处理（幂等性检查）
     */
    public boolean isTaskExecuted(String packageId, String subTaskId, int attempt) {
        String key = String.format(IDEMPOTENT_PREFIX, packageId, subTaskId, attempt);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 标记任务已处理
     */
    public void markTaskExecuted(String packageId, String subTaskId, int attempt) {
        String key = String.format(IDEMPOTENT_PREFIX, packageId, subTaskId, attempt);
        redisTemplate.opsForValue().set(key, "1", IDEMPOTENT_TTL);
    }

    /**
     * 检查并标记（原子操作）- 返回是否重复
     */
    public boolean checkAndMarkTask(String packageId, String subTaskId, int attempt) {
        String key = String.format(IDEMPOTENT_PREFIX, packageId, subTaskId, attempt);
        Boolean existed = redisTemplate.opsForValue().setIfAbsent(key, "1", IDEMPOTENT_TTL);
        // existed 为 true 表示已存在（重复），false 表示新设置（不是重复）
        return existed == null || !existed;
    }

    // ========== Dual-Write 支持（迁移期）==========

    /**
     * 双重写入：新旧键都写
     */
    public void writeToBlackboardDual(String packageId, String key, Object value) {
        // 新版 schema
        updateContextField(packageId, "blackboard." + key, value);

        // 旧版 schema（兼容迁移）
        String legacyKey = LEGACY_BLACKBOARD_PREFIX + packageId + ":" + key;
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(legacyKey, jsonValue, CTX_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to write legacy blackboard key: {}", key, e);
        }
    }

    // ========== 清理 ==========

    /**
     * 清理协作包的所有 Redis 数据（新版键）
     */
    public void cleanupPackage(String packageId) {
        String[] patterns = {
            String.format(CTX_PREFIX.replaceAll("%s", packageId).replace("ctx", "*"), packageId),
            String.format("collab:%s:checkpoint:*", packageId),
            String.format("collab:%s:lock:*", packageId),
            String.format(CURSOR_PREFIX, packageId),
            String.format(BRANCH_COUNTER_PREFIX, packageId)
        };

        for (String pattern : patterns) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }

        // 清理幂等键
        String idempPattern = String.format("collab:idemp:%s:*", packageId);
        Set<String> idempKeys = redisTemplate.keys(idempPattern);
        if (idempKeys != null && !idempKeys.isEmpty()) {
            redisTemplate.delete(idempKeys);
        }

        log.info("Cleaned up Redis data for package: {}", packageId);
    }

    /**
     * 获取包统计信息
     */
    public Map<String, Object> getPackageStats(String packageId) {
        Map<String, Object> stats = new HashMap<>();

        // 检查新版上下文
        String ctxKey = String.format(CTX_PREFIX, packageId);
        stats.put("hasContext", redisTemplate.hasKey(ctxKey));

        // 检查点数量
        String checkpointPattern = String.format("collab:%s:checkpoint:*", packageId);
        Set<String> checkpointKeys = redisTemplate.keys(checkpointPattern);
        stats.put("checkpointCount", checkpointKeys != null ? checkpointKeys.size() : 0);

        // 锁数量
        String lockPattern = String.format("collab:%s:lock:*", packageId);
        Set<String> lockKeys = redisTemplate.keys(lockPattern);
        stats.put("activeLocks", lockKeys != null ? lockKeys.size() : 0);

        // 分支计数器
        stats.put("branchCounter", getBranchCounter(packageId));

        // 光标
        stats.put("hasCursor", redisTemplate.hasKey(String.format(CURSOR_PREFIX, packageId)));

        return stats;
    }
}
