package com.adlin.orin.modules.audit.controller;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api/v1/audit/logs")
@RequiredArgsConstructor
@Tag(name = "Phase 5: Audit Logs", description = "审计日志查询接口")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "分页获取系统审计日志")
    @GetMapping
    public Page<AuditLog> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false, defaultValue = "ALL") String type,
            @RequestParam(required = false, defaultValue = "false") boolean grouped) {

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        if (userId != null && !userId.isBlank()) {
            return auditLogService.getUserAuditLogs(userId, pageable);
        }

        if (grouped) {
            // Native query requires actual column names for sorting
            String nativeSortBy = sortBy.equals("createdAt") ? "created_at" : sortBy;
            Pageable nativePageable = PageRequest.of(page, size,
                    Sort.by(Sort.Direction.fromString(direction), nativeSortBy));
            return auditLogService.getAuditLogsGrouped(type, nativePageable);
        }

        // Use the new filtered method
        return auditLogService.getAuditLogsFiltered(type, pageable);
    }

    @Operation(summary = "获取指定会话的完整历史记录")
    @GetMapping("/conversation/{conversationId}")
    public java.util.List<AuditLog> getConversationHistory(@PathVariable String conversationId) {
        return auditLogService.getRecentConversationLogs(conversationId, 100);
    }
}
