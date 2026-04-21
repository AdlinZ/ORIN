package com.adlin.orin.modules.conversation.dto.tooling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveToolBinding {
    private String agentId;
    private String sessionId;

    @Builder.Default
    private List<String> toolIds = new ArrayList<>();

    @Builder.Default
    private List<String> kbIds = new ArrayList<>();

    @Builder.Default
    private List<Long> skillIds = new ArrayList<>();

    @Builder.Default
    private List<Long> mcpIds = new ArrayList<>();

    @Builder.Default
    private Boolean enableSuggestions = true;

    @Builder.Default
    private Boolean showRetrievedContext = true;

    @Builder.Default
    private Boolean autoRenameSession = true;
}
