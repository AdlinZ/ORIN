package com.adlin.orin.modules.conversation.tooling;

import com.adlin.orin.modules.conversation.dto.tooling.ToolCatalogItemDto;
import org.springframework.stereotype.Component;

@Component
public class ToolRouter {

    public String routeRuntimeMode(ToolCatalogItemDto tool, boolean modelSupportsFunctionCalling) {
        if (tool == null) return ToolCatalogService.MODE_CONTEXT_ONLY;

        String mode = tool.getRuntimeMode();
        if (ToolCatalogService.MODE_FUNCTION_CALL.equalsIgnoreCase(mode) && modelSupportsFunctionCalling) {
            return ToolCatalogService.MODE_FUNCTION_CALL;
        }
        return ToolCatalogService.MODE_CONTEXT_ONLY;
    }
}
