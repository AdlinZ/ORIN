package com.adlin.orin.modules.conversation.tool;

import com.adlin.orin.modules.conversation.dto.ChatMessageResponse.ToolTrace;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ToolExecutionContext {
    private String sessionId;
    private String query;
    private List<String> kbIds;
    private List<Long> mcpIds;
    private Map<String, List<String>> kbDocFilters;
    private List<ToolTrace> traces;
    private Map<String, Object> sharedState;
    private List<Object> retrievedChunks;

    public void addTrace(ToolTrace trace) {
        if (this.traces == null) {
            this.traces = new ArrayList<>();
        }
        this.traces.add(trace);
    }

    public void putSharedState(String key, Object value) {
        if (this.sharedState == null) {
            this.sharedState = new HashMap<>();
        }
        this.sharedState.put(key, value);
    }

    public Object getSharedState(String key) {
        return this.sharedState != null ? this.sharedState.get(key) : null;
    }

    public void addRetrievedChunk(Object chunk) {
        if (this.retrievedChunks == null) {
            this.retrievedChunks = new ArrayList<>();
        }
        this.retrievedChunks.add(chunk);
    }
}
