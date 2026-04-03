package com.adlin.orin.modules.alert.service;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import com.adlin.orin.modules.alert.service.channel.AlertChannelSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 告警渠道网关：统一管理渠道分发。
 */
@Slf4j
@Service
public class AlertChannelGateway {

    private static final Map<String, String> CHANNEL_ALIAS = Map.of(
        "wechat", "wecom"
    );

    private final Map<String, AlertChannelSender> senderMap;

    public AlertChannelGateway(List<AlertChannelSender> senders) {
        this.senderMap = senders.stream()
            .collect(Collectors.toMap(
                sender -> normalizeChannel(sender.channel()),
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    public boolean send(String channel, AlertNotificationConfig config, String title, String content, String receiverOverride) {
        String normalized = normalizeChannel(channel);
        AlertChannelSender sender = senderMap.get(normalized);
        if (sender == null) {
            log.warn("未知的通知渠道: {}", channel);
            return false;
        }
        return sender.send(config, title, content, receiverOverride);
    }

    public Set<String> supportedChannels() {
        return senderMap.keySet();
    }

    public String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "";
        }
        String normalized = channel.trim().toLowerCase(Locale.ROOT);
        return CHANNEL_ALIAS.getOrDefault(normalized, normalized);
    }
}
