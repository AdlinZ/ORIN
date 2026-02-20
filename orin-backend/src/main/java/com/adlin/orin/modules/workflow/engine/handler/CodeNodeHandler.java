package com.adlin.orin.modules.workflow.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("codeNodeHandler")
public class CodeNodeHandler implements NodeHandler {

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        String code = (String) nodeData.get("code");
        String language = (String) nodeData.getOrDefault("code_language", "python3");

        log.info("CodeNode executing: language={}, codeLength={}", language, code != null ? code.length() : 0);

        // In a real implementation, this would invoke a sandboxed execution environment
        // For now, we simulate success and return a placeholder result
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("result", "Code execution simulated. Output depends on implementation of sandbox.");
        outputs.put("status", "success");

        return NodeExecutionResult.success(outputs);
    }
}
