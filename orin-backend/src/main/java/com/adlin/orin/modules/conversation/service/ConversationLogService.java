package com.adlin.orin.modules.conversation.service;

import com.adlin.orin.modules.conversation.dto.ConversationSummary;
import com.adlin.orin.modules.conversation.entity.ConversationLog;
import com.adlin.orin.modules.conversation.repository.ConversationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationLogService {

    private final ConversationLogRepository repository;

    @Transactional
    public void log(ConversationLog log) {
        repository.save(log);
    }

    public Page<ConversationSummary> getGroupedLogs(Pageable pageable) {
        // 获取原始结果
        Page<Object[]> rawResults = repository.findGroupedByConversationRaw(pageable);

        // 获取所有 conversationId 并查询累计 tokens
        List<String> conversationIds = rawResults.getContent().stream()
                .map(row -> (String) row[1]) // conversation_id is index 1
                .distinct()
                .collect(Collectors.toList());

        final Map<String, Integer> cumulativeTokensMap;
        if (!conversationIds.isEmpty()) {
            cumulativeTokensMap = repository.findCumulativeTokensByConversationIds(conversationIds)
                    .stream()
                    .collect(Collectors.toMap(
                            row -> (String) row[0],
                            row -> ((Number) row[1]).intValue()
                    ));
        } else {
            cumulativeTokensMap = new HashMap<>();
        }

        // 转换为 ConversationSummary
        List<ConversationSummary> summaries = rawResults.getContent().stream()
                .map(row -> {
                    ConversationSummary summary = new ConversationSummary();
                    summary.setId((String) row[0]);
                    summary.setConversationId((String) row[1]);
                    summary.setAgentId((String) row[2]);
                    summary.setUserId((String) row[3]);
                    summary.setModel((String) row[4]);
                    summary.setQuery((String) row[5]);
                    summary.setResponse((String) row[6]);
                    summary.setPromptTokens(row[7] != null ? ((Number) row[7]).intValue() : null);
                    summary.setCompletionTokens(row[8] != null ? ((Number) row[8]).intValue() : null);
                    summary.setTotalTokens(row[9] != null ? ((Number) row[9]).intValue() : null);
                    summary.setResponseTime(row[11] != null ? ((Number) row[11]).longValue() : null);
                    summary.setSuccess((Boolean) row[12]);
                    summary.setErrorMessage((String) row[13]);
                    summary.setCreatedAt(row[14] != null ? ((java.sql.Timestamp) row[14]).toLocalDateTime() : null);

                    // Set cumulative tokens
                    Integer cumulative = cumulativeTokensMap.get(summary.getConversationId());
                    summary.setCumulativeTokens(cumulative != null ? cumulative : 0);

                    return summary;
                })
                .collect(Collectors.toList());

        return new PageImpl<>(summaries, pageable, rawResults.getTotalElements());
    }

    public List<ConversationLog> getConversationHistory(String conversationId) {
        return repository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
}
