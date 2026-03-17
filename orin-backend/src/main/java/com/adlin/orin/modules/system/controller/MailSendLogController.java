package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.MailSendLog;
import com.adlin.orin.modules.system.repository.MailSendLogRepository;
import com.adlin.orin.modules.system.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 邮件发送日志 Controller
 */
@RestController
@RequestMapping("/api/v1/system/mail-logs")
@RequiredArgsConstructor
@Slf4j
public class MailSendLogController {

    private final MailSendLogRepository repository;
    private final MailService mailService;

    /**
     * 获取邮件发送日志列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<MailSendLog> logPage;
        if (status != null && !status.isEmpty()) {
            logPage = repository.findByStatus(status, pageRequest);
        } else {
            logPage = repository.findAll(pageRequest);
        }

        return ResponseEntity.ok(Map.of(
            "content", logPage.getContent(),
            "totalElements", logPage.getTotalElements(),
            "totalPages", logPage.getTotalPages(),
            "currentPage", logPage.getNumber()
        ));
    }

    /**
     * 获取最近的日志
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentLogs() {
        return ResponseEntity.ok(repository.findTop10ByOrderByCreatedAtDesc());
    }

    /**
     * 重试发送单条日志
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retrySingle(@PathVariable Long id) {
        try {
            MailSendLog log = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("日志不存在"));

            if (!MailSendLog.STATUS_FAILED.equals(log.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "只有失败的记录可以重试"
                ));
            }

            // 更新状态为待发送
            log.setStatus(MailSendLog.STATUS_PENDING);
            log.setErrorMessage(null);
            repository.save(log);

            // 触发重试发送
            mailService.retrySend(log);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "已添加到重试队列"
            ));
        } catch (Exception e) {
            log.error("重试发送失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 批量重试发送
     */
    @PostMapping("/batch-retry")
    public ResponseEntity<Map<String, Object>> batchRetry(@RequestBody Map<String, List<Long>> payload) {
        try {
            List<Long> ids = payload.get("ids");
            if (ids == null || ids.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "请选择要重试的记录"
                ));
            }

            int successCount = 0;
            for (Long id : ids) {
                try {
                    MailSendLog log = repository.findById(id).orElse(null);
                    if (log != null && MailSendLog.STATUS_FAILED.equals(log.getStatus())) {
                        log.setStatus(MailSendLog.STATUS_PENDING);
                        log.setErrorMessage(null);
                        repository.save(log);
                        mailService.retrySend(log);
                        successCount++;
                    }
                } catch (Exception e) {
                    log.warn("重试记录 {} 失败", id, e);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "已添加 " + successCount + " 条记录到重试队列"
            ));
        } catch (Exception e) {
            log.error("批量重试失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
