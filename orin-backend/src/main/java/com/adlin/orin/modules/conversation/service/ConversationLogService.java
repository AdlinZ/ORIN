package com.adlin.orin.modules.conversation.service;

import com.adlin.orin.modules.conversation.entity.ConversationLog;
import com.adlin.orin.modules.conversation.repository.ConversationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationLogService {

    private final ConversationLogRepository repository;

    @Transactional
    public void log(ConversationLog log) {
        repository.save(log);
    }

    public Page<ConversationLog> getGroupedLogs(Pageable pageable) {
        return repository.findGroupedByConversation(pageable);
    }

    public List<ConversationLog> getConversationHistory(String conversationId) {
        return repository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
}
