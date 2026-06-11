package com.adlin.orin.modules.workflow.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component("templateTransformNodeHandler")
public class TemplateTransformNodeHandler implements NodeHandler {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*\\}\\}");

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        String template = (String) nodeData.getOrDefault("template", "");
        log.info("TemplateTransform executing, templateLength={}", template.length());

        String output = VAR_PATTERN.matcher(template).replaceAll(match -> {
            String key = match.group(1);
            Object val = resolveKey(key, context);
            return val != null ? Matcher.quoteReplacement(String.valueOf(val)) : match.group(0);
        });

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", output);
        return NodeExecutionResult.success(outputs);
    }

    private Object resolveKey(String key, Map<String, Object> context) {
        if (context.containsKey(key)) return context.get(key);
        // support dot-notation: "node1.text"
        int dot = key.indexOf('.');
        if (dot > 0) {
            Object parent = context.get(key.substring(0, dot));
            if (parent instanceof Map<?, ?> m) return m.get(key.substring(dot + 1));
        }
        return null;
    }
}
