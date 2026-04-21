package com.adlin.orin.modules.conversation.tooling;

import com.adlin.orin.modules.conversation.dto.tooling.ToolCatalogItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final ToolCatalogService toolCatalogService;

    public Map<String, ToolCatalogItemDto> getToolMap() {
        return toolCatalogService.getCatalogMap();
    }

    public List<ToolCatalogItemDto> resolveByToolIds(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) return Collections.emptyList();
        Map<String, ToolCatalogItemDto> map = getToolMap();
        Map<String, ToolCatalogItemDto> result = new LinkedHashMap<>();
        for (String toolId : toolIds) {
            ToolCatalogItemDto item = map.get(toolId);
            if (item != null) {
                result.put(toolId, item);
            }
        }
        return result.values().stream().toList();
    }
}
