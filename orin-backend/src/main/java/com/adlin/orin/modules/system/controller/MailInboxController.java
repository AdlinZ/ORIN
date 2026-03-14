package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.MailInboxEntity;
import com.adlin.orin.modules.system.service.MailInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 邮件收件箱 Controller
 */
@RestController
@RequestMapping("/api/v1/system/mail-inbox")
@RequiredArgsConstructor
public class MailInboxController {

    private final MailInboxService mailInboxService;

    /**
     * 获取收件箱列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getInboxList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<MailInboxEntity> inboxPage = mailInboxService.getInboxList(pageRequest);

        return ResponseEntity.ok(Map.of(
            "content", inboxPage.getContent(),
            "totalElements", inboxPage.getTotalElements(),
            "totalPages", inboxPage.getTotalPages(),
            "currentPage", inboxPage.getNumber()
        ));
    }

    /**
     * 获取未读邮件数量
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount() {
        return ResponseEntity.ok(Map.of(
            "count", mailInboxService.getUnreadCount()
        ));
    }

    /**
     * 获取邮件详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return mailInboxService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 标记为已读
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable Long id) {
        boolean success = mailInboxService.markAsRead(id);
        return ResponseEntity.ok(Map.of("success", success));
    }

    /**
     * 标记/取消星标
     */
    @PostMapping("/{id}/star")
    public ResponseEntity<Map<String, Object>> markAsStarred(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean starred) {
        boolean success = mailInboxService.markAsStarred(id, starred);
        return ResponseEntity.ok(Map.of("success", success));
    }

    /**
     * 删除邮件
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        boolean success = mailInboxService.delete(id);
        return ResponseEntity.ok(Map.of("success", success));
    }

    /**
     * 拉取邮件
     */
    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetchEmails() {
        int count = mailInboxService.fetchEmails();
        return ResponseEntity.ok(Map.of(
            "success", count >= 0,
            "fetchedCount", count,
            "message", count >= 0 ? "拉取成功" : "拉取失败"
        ));
    }

    /**
     * 获取 IMAP 配置状态
     */
    @GetMapping("/imap-status")
    public ResponseEntity<Map<String, Object>> getImapStatus() {
        return ResponseEntity.ok(Map.of(
            "configured", mailInboxService.isImapConfigured()
        ));
    }
}
