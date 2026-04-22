package com.adlin.orin.modules.system.controller;

import com.adlin.orin.common.service.FileStorageService;
import com.adlin.orin.common.storage.StorageReplicationRepairService;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Tag(name = "Storage Ops", description = "对象存储与复制修复运维接口")
public class StorageOpsController {

    private final FileStorageService fileStorageService;
    private final StorageReplicationRepairService repairService;

    @GetMapping("/health")
    @Operation(summary = "Get storage health snapshot")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(fileStorageService.healthSnapshot());
    }

    @GetMapping("/replication/status")
    @Operation(summary = "Get replication queue status")
    public ResponseEntity<Map<String, Object>> replicationStatus() {
        return ResponseEntity.ok(Map.of(
                "pending", repairService.pendingCount(),
                "failed", repairService.failedCount(),
                "tasks", repairService.listRepairTasks()));
    }

    @PostMapping("/replication/retry")
    @Operation(summary = "Retry a replication task now")
    public ResponseEntity<Map<String, Object>> retry(@RequestParam("taskId") String taskId) {
        repairService.retryNow(taskId);
        return ResponseEntity.ok(Map.of("taskId", taskId, "status", "scheduled"));
    }

    @PostMapping("/health/minio/test")
    @Operation(summary = "Test MinIO connection with request-time credentials")
    public ResponseEntity<Map<String, Object>> testMinio(@RequestBody Map<String, Object> payload) {
        String endpoint = payload == null ? null : String.valueOf(payload.getOrDefault("endpoint", "")).trim();
        String accessKey = payload == null ? null : String.valueOf(payload.getOrDefault("accessKey", "")).trim();
        String secretKey = payload == null ? null : String.valueOf(payload.getOrDefault("secretKey", "")).trim();
        String bucket = payload == null ? null : String.valueOf(payload.getOrDefault("bucket", "orin-files")).trim();

        Map<String, Object> result = new HashMap<>();
        result.put("backend", "minio");
        result.put("endpoint", endpoint);
        result.put("bucket", bucket);

        if (!StringUtils.hasText(endpoint)) {
            result.put("up", false);
            result.put("error", "endpoint is empty");
            return ResponseEntity.ok(result);
        }
        if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
            result.put("up", false);
            result.put("error", "AccessKey and SecretKey must not be empty");
            return ResponseEntity.ok(result);
        }
        if (!StringUtils.hasText(bucket)) {
            result.put("up", false);
            result.put("error", "bucket is empty");
            return ResponseEntity.ok(result);
        }

        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            result.put("up", true);
            result.put("bucketExists", exists);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("up", false);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
