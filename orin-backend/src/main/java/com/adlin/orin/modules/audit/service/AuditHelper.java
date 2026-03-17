package com.adlin.orin.modules.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审计日志帮助类 - 提供便捷的系统操作审计日志记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditHelper {

    private final AuditLogService auditLogService;

    /**
     * 记录系统操作日志
     *
     * @param userId     用户ID (可为空)
     * @param operation  操作类型 (如 USER_CREATE, MAIL_SEND)
     * @param endpoint  接口路径
     * @param detail    详细描述
     * @param success   是否成功
     * @param errorMsg  错误信息 (失败时填写)
     */
    @Async
    public void log(String userId, String operation, String endpoint, String detail,
                     boolean success, String errorMsg) {
        try {
            auditLogService.logApiCall(
                    userId != null ? userId : "SYSTEM",
                    null,
                    operation,
                    "SYSTEM",
                    endpoint,
                    "POST",
                    operation,
                    null,
                    "System",
                    detail,
                    null,
                    success ? 200 : 500,
                    0L,
                    0, 0, 0.0,
                    success,
                    errorMsg,
                    null,
                    null
            );
        } catch (Exception e) {
            log.error("记录审计日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录配置变更日志
     */
    @Async
    public void logConfigChange(String userId, String configType, String operation, String detail,
                                boolean success, String errorMsg) {
        log(userId, "CONFIG_" + configType, "/system/config/" + operation, detail, success, errorMsg);
    }

    /**
     * 记录用户管理操作日志
     */
    @Async
    public void logUserOperation(String adminUserId, String targetUser, String operation,
                                  String detail, boolean success, String errorMsg) {
        String fullDetail = String.format("操作人: %s, 目标: %s, 详情: %s",
                adminUserId, targetUser, detail);
        log(adminUserId, "USER_" + operation, "/system/user/" + operation.toLowerCase(),
                fullDetail, success, errorMsg);
    }

    /**
     * 记录文件操作日志
     */
    @Async
    public void logFileOperation(String userId, String operation, String fileName,
                                  String detail, boolean success, String errorMsg) {
        String fullDetail = String.format("操作: %s, 文件: %s, 详情: %s",
                operation, fileName, detail);
        log(userId, "FILE_" + operation, "/system/file/" + operation.toLowerCase(),
                fullDetail, success, errorMsg);
    }

    /**
     * 记录邮件操作日志
     */
    @Async
    public void logMailOperation(String userId, String mailType, String recipient,
                                  String subject, boolean success, String errorMsg) {
        String detail = String.format("类型: %s, 收件人: %s, 主题: %s",
                mailType, recipient, subject);
        log(userId, "MAIL_" + mailType, "/mail/send", detail, success, errorMsg);
    }

    /**
     * 记录告警配置操作日志
     */
    @Async
    public void logAlertOperation(String userId, String operation, String alertName,
                                   String detail, boolean success, String errorMsg) {
        String fullDetail = String.format("操作: %s, 告警: %s, 详情: %s",
                operation, alertName, detail);
        log(userId, "ALERT_" + operation, "/alert/config/" + operation.toLowerCase(),
                fullDetail, success, errorMsg);
    }

    /**
     * 记录工作流操作日志
     */
    @Async
    public void logWorkflowOperation(String userId, String workflowId, String operation,
                                      String detail, boolean success, String errorMsg) {
        String fullDetail = String.format("工作流ID: %s, 操作: %s, 详情: %s",
                workflowId, operation, detail);
        log(userId, "WORKFLOW_" + operation, "/workflow/" + operation.toLowerCase(),
                fullDetail, success, errorMsg);
    }
}
