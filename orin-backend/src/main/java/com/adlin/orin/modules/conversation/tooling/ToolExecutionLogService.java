package com.adlin.orin.modules.conversation.tooling;

import com.adlin.orin.modules.conversation.entity.ToolExecutionLog;
import com.adlin.orin.modules.conversation.repository.ToolExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ToolExecutionLogService {

    private final ToolExecutionLogRepository repository;

    public void log(String sessionId, String agentId, String toolId, String runtimeMode,
            boolean success, String errorCode, long latencyMs, Map<String, Object> detail) {
        ToolExecutionLog row = ToolExecutionLog.builder()
                .sessionId(sessionId)
                .agentId(agentId)
                .toolId(toolId)
                .runtimeMode(runtimeMode)
                .success(success)
                .errorCode(errorCode)
                .latencyMs(latencyMs)
                .detail(detail)
                .build();
        repository.save(row);
    }
}
