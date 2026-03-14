package com.adlin.orin.common.util;

import org.springframework.stereotype.Component;

/**
 * HTML邮件模板生成器
 * 提供美观、现代的邮件模板
 */
@Component
public class MailTemplateBuilder {

    private static final String PRIMARY_COLOR = "#4F46E5";
    private static final String SECONDARY_COLOR = "#6366F1";
    private static final String SUCCESS_COLOR = "#10B981";
    private static final String WARNING_COLOR = "#F59E0B";
    private static final String DANGER_COLOR = "#EF4444";
    private static final String GRAY_COLOR = "#6B7280";

    /**
     * 构建验证码邮件模板
     */
    public String buildVerificationCode(String appName, String code, int expireMinutes) {
        return buildHtmlTemplate(
            appName,
            "邮箱验证码",
            """
                <div style="text-align: center; padding: 20px 0;">
                    <p style="font-size: 16px; color: #374151; margin-bottom: 24px;">
                        您好，您正在绑定邮箱，验证码如下：
                    </p>
                    <div style="display: inline-block; background: linear-gradient(135deg, %s 0%%, %s 100%%);
                                padding: 20px 40px; border-radius: 12px; margin: 16px 0;">
                        <span style="font-size: 36px; font-weight: bold; color: white;
                                     letter-spacing: 8px; font-family: monospace;">%s</span>
                    </div>
                    <p style="font-size: 14px; color: %s; margin-top: 24px;">
                        有效期 <strong>%d</strong> 分钟，请尽快完成验证
                    </p>
                </div>
                """.formatted(PRIMARY_COLOR, SECONDARY_COLOR, code, GRAY_COLOR, expireMinutes),
            """
                <ul style="color: %s; font-size: 14px; padding-left: 20px;">
                    <li>验证码只能使用一次，请勿泄露给他人</li>
                    <li>如非本人操作，请忽略此邮件</li>
                    <li>为保护账户安全，请勿将验证码告诉任何人</li>
                </ul>
                """.formatted(GRAY_COLOR)
        );
    }

    /**
     * 构建告警通知邮件模板
     */
    public String buildAlertNotification(String appName, String alertTitle, String alertLevel,
                                         String alertContent, String alertTime) {
        String levelColor = switch (alertLevel.toLowerCase()) {
            case "critical" -> DANGER_COLOR;
            case "warning" -> WARNING_COLOR;
            default -> SUCCESS_COLOR;
        };

        String levelText = switch (alertLevel.toLowerCase()) {
            case "critical" -> "🔴 严重告警";
            case "warning" -> "🟡 警告";
            default -> "🟢 通知";
        };

        return buildHtmlTemplate(
            appName,
            "系统告警通知",
            """
                <div style="text-align: left; padding: 16px 0;">
                    <div style="display: inline-block; background: %s; color: white;
                                padding: 6px 16px; border-radius: 20px;
                                font-size: 14px; font-weight: 500; margin-bottom: 20px;">
                        %s
                    </div>
                    <h2 style="color: #1F2937; margin: 16px 0; font-size: 24px;">%s</h2>
                    <div style="background: #F9FAFB; border-radius: 8px; padding: 20px; margin: 16px 0;">
                        <p style="color: #374151; font-size: 15px; line-height: 1.8; margin: 0; white-space: pre-line;">%s</p>
                    </div>
                    <p style="color: %s; font-size: 13px; margin-top: 16px;">
                        ⏰ 告警时间：%s
                    </p>
                </div>
                """.formatted(levelColor, levelText, alertTitle, alertContent, GRAY_COLOR, alertTime),
            """
                <div style="margin-top: 24px; padding-top: 24px; border-top: 1px solid #E5E7EB;">
                    <p style="color: %s; font-size: 13px;">
                        此邮件由 <strong>%s</strong> 系统自动发送，请勿直接回复<br>
                        如需处理此告警，请登录系统查看详情
                    </p>
                </div>
                """.formatted(GRAY_COLOR, appName)
        );
    }

    /**
     * 构建系统通知邮件模板
     */
    public String buildSystemNotification(String appName, String title, String content,
                                          String actionText, String actionUrl) {
        return buildHtmlTemplate(
            appName,
            title,
            """
                <div style="text-align: left; padding: 16px 0;">
                    <h2 style="color: #1F2937; margin: 0 0 16px 0; font-size: 22px;">%s</h2>
                    <div style="background: #F9FAFB; border-radius: 8px; padding: 24px; margin: 16px 0;">
                        <p style="color: #374151; font-size: 15px; line-height: 1.8; margin: 0; white-space: pre-line;">%s</p>
                    </div>
                    %s
                </div>
                """.formatted(
                    title,
                    content,
                    actionUrl != null ? """
                        <div style="text-align: center; margin: 24px 0;">
                            <a href="%s" style="display: inline-block; background: linear-gradient(135deg, %s 0%%, %s 100%%);
                                       color: white; padding: 14px 32px; border-radius: 8px;
                                       text-decoration: none; font-weight: 500; font-size: 15px;">
                                %s →
                            </a>
                        </div>
                        """.formatted(actionUrl, PRIMARY_COLOR, SECONDARY_COLOR, actionText) : ""
                ),
            """
                <div style="margin-top: 24px; padding-top: 24px; border-top: 1px solid #E5E7EB;">
                    <p style="color: %s; font-size: 13px;">
                        此邮件由 <strong>%s</strong> 系统自动发送<br>
                        如有任何疑问，请联系系统管理员
                    </p>
                </div>
                """.formatted(GRAY_COLOR, appName)
        );
    }

    /**
     * 构建工作流状态通知模板
     */
    public String buildWorkflowNotification(String appName, String workflowName, String status,
                                            String triggerType, String executionTime, String executor) {
        String statusColor = switch (status.toLowerCase()) {
            case "success", "completed" -> SUCCESS_COLOR;
            case "failed" -> DANGER_COLOR;
            case "running" -> PRIMARY_COLOR;
            default -> GRAY_COLOR;
        };

        String statusIcon = switch (status.toLowerCase()) {
            case "success", "completed" -> "✅";
            case "failed" -> "❌";
            case "running" -> "🔄";
            default -> "📋";
        };

        return buildHtmlTemplate(
            appName,
            "工作流执行通知",
            """
                <div style="text-align: left; padding: 16px 0;">
                    <div style="display: flex; align-items: center; margin-bottom: 20px;">
                        <span style="font-size: 24px; margin-right: 12px;">%s</span>
                        <div>
                            <h2 style="color: #1F2937; margin: 0; font-size: 20px;">%s</h2>
                            <p style="color: %s; margin: 4px 0 0 0; font-size: 14px;">执行%s</p>
                        </div>
                    </div>
                    <table style="width: 100%%; border-collapse: collapse; margin: 16px 0;">
                        <tr>
                            <td style="padding: 12px; border-bottom: 1px solid #E5E7EB; color: %s; font-size: 14px; width: 100px;">工作流名称</td>
                            <td style="padding: 12px; border-bottom: 1px solid #E5E7EB; color: #1F2937; font-size: 14px;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding: 12px; border-bottom: 1px solid #E5E7EB; color: %s; font-size: 14px;">触发方式</td>
                            <td style="padding: 12px; border-bottom: 1px solid #E5E7EB; color: #1F2937; font-size: 14px;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding: 12px; border-bottom: 1px solid #E5E7EB; color: %s; font-size: 14px;">执行时间</td>
                            <td style="padding: 12px; border-bottom: 1px solid #E5E7EB; color: #1F2937; font-size: 14px;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding: 12px; color: %s; font-size: 14px;">执行人</td>
                            <td style="padding: 12px; color: #1F2937; font-size: 14px;">%s</td>
                        </tr>
                    </table>
                </div>
                """.formatted(
                    statusIcon, workflowName, statusColor, status,
                    GRAY_COLOR, workflowName,
                    GRAY_COLOR, triggerType,
                    GRAY_COLOR, executionTime,
                    GRAY_COLOR, executor
                ),
            """
                <div style="margin-top: 24px; padding-top: 24px; border-top: 1px solid #E5E7EB;">
                    <p style="color: %s; font-size: 13px;">
                        查看更多详情，请登录 <strong>%s</strong> 系统
                    </p>
                </div>
                """.formatted(GRAY_COLOR, appName)
        );
    }

    /**
     * 构建基础HTML模板
     */
    private String buildHtmlTemplate(String appName, String subject, String mainContent, String footerContent) {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #F3F4F6; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;">
                <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #F3F4F6; padding: 40px 20px;">
                    <tr>
                        <td align="center">
                            <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 600px; background-color: #FFFFFF; border-radius: 16px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);">
                                <!-- Header -->
                                <tr>
                                    <td style="padding: 32px 40px 24px; text-align: center; border-bottom: 1px solid #E5E7EB;">
                                        <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                                            <tr>
                                                <td align="center">
                                                    <!-- Logo -->
                                                    <div style="display: inline-flex; align-items: center; justify-content: center; width: 48px; height: 48px; background: linear-gradient(135deg, %s 0%%, %s 100%%); border-radius: 12px; margin-bottom: 16px;">
                                                        <span style="color: white; font-size: 24px; font-weight: bold;">%s</span>
                                                    </div>
                                                    <h1 style="margin: 0; font-size: 24px; color: #1F2937; font-weight: 600;">%s</h1>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <!-- Content -->
                                <tr>
                                    <td style="padding: 32px 40px;">
                                        %s
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td style="padding: 24px 40px 32px; background-color: #F9FAFB; border-radius: 0 0 16px 16px; text-align: center;">
                                        %s
                                    </td>
                                </tr>
                            </table>
                            <!-- Copyright -->
                            <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 600px; margin-top: 24px;">
                                <tr>
                                    <td align="center">
                                        <p style="margin: 0; color: %s; font-size: 12px;">
                                            © %d %s. All rights reserved.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(
                subject,
                PRIMARY_COLOR,
                SECONDARY_COLOR,
                String.valueOf(appName.charAt(0)).toUpperCase(),
                appName,
                mainContent,
                footerContent,
                GRAY_COLOR,
                java.time.Year.now().getValue(),
                appName
            );
    }
}
