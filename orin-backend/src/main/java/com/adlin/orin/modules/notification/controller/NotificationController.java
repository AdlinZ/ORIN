package com.adlin.orin.modules.notification.controller;

import com.adlin.orin.modules.notification.entity.SystemMessage;
import com.adlin.orin.modules.notification.service.SystemNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 消息中心控制器
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "消息中心")
public class NotificationController {

    private final SystemNotificationService notificationService;

    @Operation(summary = "获取消息列表")
    @GetMapping
    public ResponseEntity<Page<SystemMessage>> getMessages(
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String scope) {
        return ResponseEntity.ok(notificationService.getUserMessages(userId, page, size, scope));
    }

    @Operation(summary = "获取广播消息")
    @GetMapping("/broadcasts")
    public ResponseEntity<Page<SystemMessage>> getBroadcasts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getBroadcasts(page, size));
    }

    @Operation(summary = "获取未读消息数")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @Operation(summary = "获取消息统计")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ResponseEntity.ok(notificationService.getMessageStats(userId));
    }

    @Operation(summary = "获取消息详情")
    @GetMapping("/{id}")
    public ResponseEntity<SystemMessage> getMessage(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return notificationService.getMessage(id)
                .map(message -> {
                    // 标记为已读
                    notificationService.markAsRead(id, userId);
                    return ResponseEntity.ok(message);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "发送消息")
    @PostMapping
    public ResponseEntity<SystemMessage> sendMessage(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String senderId) {
        String title = request.get("title");
        String content = request.get("content");
        String type = request.getOrDefault("type", "INFO");
        String receiverId = request.get("receiverId");
        String scope = request.getOrDefault("scope", "USER");

        // 自动判断：如果没有 receiverId 则为广播
        if (receiverId == null || receiverId.isEmpty()) {
            scope = "BROADCAST";
        }

        SystemMessage message = notificationService.sendMessage(title, content, type, receiverId, senderId, scope);
        return ResponseEntity.ok(message);
    }

    @Operation(summary = "发送系统通知")
    @PostMapping("/system")
    public ResponseEntity<SystemMessage> sendSystemNotification(
            @RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");

        SystemMessage message = notificationService.sendSystemNotification(title, content);
        return ResponseEntity.ok(message);
    }

    @Operation(summary = "标记消息为已读")
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "标记所有消息为已读")
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(summary = "清理过期消息")
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Integer>> cleanup() {
        int count = notificationService.cleanupExpiredMessages();
        return ResponseEntity.ok(Map.of("deleted", count));
    }
}
