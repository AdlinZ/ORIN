package com.adlin.orin.modules.workflow.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("documentExtractorNodeHandler")
public class DocumentExtractorNodeHandler implements NodeHandler {

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        String variable = (String) nodeData.getOrDefault("variable", "document");
        log.info("DocumentExtractor executing: variable={}", variable);

        Object doc = context.get(variable);
        String text = "";
        if (doc instanceof String s) {
            text = s;
        } else if (doc instanceof Map<?, ?> m) {
            // support {content: "..."} or {text: "..."}
            Object content = m.get("content");
            if (content == null) content = m.get("text");
            if (content != null) text = String.valueOf(content);
        } else if (doc instanceof List<?> pages) {
            StringBuilder sb = new StringBuilder();
            for (Object p : pages) {
                if (p instanceof String s) sb.append(s).append('\n');
                else if (p instanceof Map<?, ?> m) {
                    Object c = m.get("content");
                    if (c == null) c = m.get("text");
                    if (c != null) sb.append(c).append('\n');
                }
            }
            text = sb.toString().trim();
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("text", text);
        outputs.put("output", text);
        return NodeExecutionResult.success(outputs);
    }
}
