package com.adlin.orin.modules.alert.service.channel;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;

/**
 * 告警通知渠道发送器抽象。
 */
public interface AlertChannelSender {

    /**
     * 渠道标识（如 email/dingtalk/wecom）。
     */
    String channel();

    /**
     * 发送通知。
     *
     * @param config 告警通知配置
     * @param title 标题
     * @param content 内容
     * @param receiverOverride 覆盖接收目标（如收件人/Webhook 列表，逗号分隔），可为空
     * @return 是否发送成功
     */
    boolean send(AlertNotificationConfig config, String title, String content, String receiverOverride);
}
