package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.MailSendLog;
import com.adlin.orin.modules.system.repository.MailSendLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 邮件发送日志 Controller
 */
@RestController
@RequestMapping("/api/v1/system/mail-logs")
@RequiredArgsConstructor
public class MailSendLogController {

    private final MailSendLogRepository repository;

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
}
