package com.adlin.orin.modules.skill.service.impl;

import com.adlin.orin.common.exception.ValidationException;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import com.adlin.orin.modules.skill.service.McpServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * MCP 服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServiceServiceImpl implements McpServiceService {

    private final McpServiceRepository mcpServiceRepository;
    private static final List<String> SENSITIVE_ENV_SUFFIXES = List.of("_KEY", "_TOKEN", "_SECRET");

    @Override
    public List<McpService> getAllServices() {
        return mcpServiceRepository.findAll();
    }

    @Override
    public McpService getServiceById(Long id) {
        return mcpServiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("MCP 服务不存在: " + id));
    }

    @Override
    @Transactional
    public McpService createService(McpService service) {
        validateEnvVars(service.getEnvVars());
        if (mcpServiceRepository.existsByName(service.getName())) {
            throw new RuntimeException("服务名称已存在: " + service.getName());
        }
        if (service.getEnabled() == null) {
            service.setEnabled(true);
        }
        service.setStatus(McpService.McpStatus.DISCONNECTED);
        service.setHealthScore(100);
        return mcpServiceRepository.save(service);
    }

    @Override
    @Transactional
    public McpService updateService(Long id, McpService service) {
        validateEnvVars(service.getEnvVars());
        McpService existing = mcpServiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("MCP 服务不存在: " + id));

        // 检查名称冲突（排除自身）
        if (!existing.getName().equals(service.getName()) &&
                mcpServiceRepository.existsByNameAndIdNot(service.getName(), id)) {
            throw new RuntimeException("服务名称已存在: " + service.getName());
        }

        existing.setName(service.getName());
        existing.setType(service.getType());
        existing.setCommand(service.getCommand());
        existing.setUrl(service.getUrl());
        if (service.getEnvVars() == null || !service.getEnvVars().contains("******")) {
            existing.setEnvVars(service.getEnvVars());
        }
        existing.setDescription(service.getDescription());
        existing.setToolKey(service.getToolKey());
        existing.setEnabled(service.getEnabled() != null ? service.getEnabled() : existing.getEnabled());

        return mcpServiceRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteService(Long id) {
        McpService service = getServiceById(id);
        mcpServiceRepository.delete(service);
    }

    @Override
    @Transactional
    public Map<String, Object> testConnection(Long id) {
        McpService service = getServiceById(id);
        Map<String, Object> result = new HashMap<>();

        try {
            if (!Boolean.TRUE.equals(service.getEnabled())) {
                service.setStatus(McpService.McpStatus.DISCONNECTED);
                mcpServiceRepository.save(service);
                result.put("success", false);
                result.put("message", "服务已禁用");
                result.put("errorDetail", "请先启用服务后再测试连接");
                return result;
            }

            // 标记为测试中
            service.setStatus(McpService.McpStatus.TESTING);
            mcpServiceRepository.save(service);

            if (service.getType() == McpService.McpType.STDIO) {
                result = testStdioService(service);
            } else if (service.getType() == McpService.McpType.SSE) {
                result = testSseService(service);
            } else {
                result.put("success", false);
                result.put("message", "不支持的 MCP 服务类型");
                result.put("reasonCode", "UNSUPPORTED_TYPE");
                result.put("errorDetail", String.valueOf(service.getType()));
            }

            boolean success = Boolean.TRUE.equals(result.get("success"));
            String errorDetail = (String) result.getOrDefault("errorDetail", "");

            // 更新服务状态
            if (success) {
                service.setStatus(McpService.McpStatus.CONNECTED);
                service.setLastConnected(LocalDateTime.now());
                service.setLastError(null);
                service.setHealthScore(100);
            } else {
                service.setStatus(McpService.McpStatus.ERROR);
                service.setLastError(errorDetail);
                service.setHealthScore(0);
            }

            mcpServiceRepository.save(service);

        } catch (Exception e) {
            log.error("测试连接失败: {}", e.getMessage(), e);
            service.setStatus(McpService.McpStatus.ERROR);
            service.setLastError(e.getMessage());
            service.setHealthScore(0);
            mcpServiceRepository.save(service);

            result.put("success", false);
            result.put("message", "连接测试失败");
            result.put("errorDetail", e.getMessage());
        }

        return result;
    }

    private Map<String, Object> testStdioService(McpService service) throws IOException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        String command = service.getCommand();
        if (command == null || command.isBlank()) {
            result.put("success", false);
            result.put("message", "命令不能为空");
            result.put("reasonCode", "CONFIG_MISSING");
            result.put("errorDetail", "STDIO 类型服务需要配置启动命令");
            return result;
        }

        String executable = command.trim().split("\\s+")[0];
        Process which = new ProcessBuilder("sh", "-lc", "command -v " + shellQuote(executable))
                .redirectErrorStream(true)
                .start();
        if (!which.waitFor(2, TimeUnit.SECONDS) || which.exitValue() != 0) {
            result.put("success", false);
            result.put("message", "命令不存在");
            result.put("reasonCode", "COMMAND_NOT_FOUND");
            result.put("errorDetail", "找不到可执行命令: " + executable);
            return result;
        }

        Process process = new ProcessBuilder("sh", "-lc", command).start();

        try {
            sendMcpRequest(process.getOutputStream(),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"orin\",\"version\":\"1.0.0\"}}}");
            String initializeResponse = readMcpMessage(process.getInputStream(), 5);
            if (!initializeResponse.contains("\"result\"")) {
                result.put("success", false);
                result.put("message", "MCP 初始化失败");
                result.put("reasonCode", "START_FAILED");
                result.put("errorDetail", truncate(initializeResponse));
                return result;
            }

            sendMcpNotification(process.getOutputStream(), "notifications/initialized");
            sendMcpRequest(process.getOutputStream(),
                    "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
            String toolsResponse = readMcpMessage(process.getInputStream(), 5);
            int toolCount = countToolNames(toolsResponse);
            if (toolCount <= 0) {
                result.put("success", false);
                result.put("message", "工具列表为空");
                result.put("reasonCode", "TOOL_LIST_EMPTY");
                result.put("errorDetail", truncate(toolsResponse));
                return result;
            }

            result.put("success", true);
            result.put("message", "MCP 协议握手通过");
            result.put("reasonCode", "CONNECTED");
            result.put("toolCount", toolCount);
            return result;
        } catch (java.net.http.HttpTimeoutException e) {
            result.put("success", false);
            result.put("message", "MCP 响应超时");
            result.put("reasonCode", "TIMEOUT");
            result.put("errorDetail", e.getMessage());
            return result;
        } catch (IOException e) {
            if (!process.isAlive()) {
                int exitCode = process.exitValue();
                result.put("success", false);
                result.put("message", exitCode == 0 ? "服务启动后立即退出，工具列表为空" : "服务启动失败");
                result.put("reasonCode", exitCode == 0 ? "TOOL_LIST_EMPTY" : "START_FAILED");
                result.put("errorDetail", "进程退出码: " + exitCode);
                return result;
            }
            result.put("success", false);
            result.put("message", "MCP 协议握手失败");
            result.put("reasonCode", "START_FAILED");
            result.put("errorDetail", e.getMessage());
            return result;
        } finally {
            process.destroyForcibly();
        }
    }

    private Map<String, Object> testSseService(McpService service) {
        Map<String, Object> result = new HashMap<>();
        String url = service.getUrl();
        if (url == null || url.isBlank()) {
            result.put("success", false);
            result.put("message", "URL 不能为空");
            result.put("reasonCode", "CONFIG_MISSING");
            result.put("errorDetail", "SSE 类型服务需要配置 URL");
            return result;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            result.put("success", false);
            result.put("message", "URL 格式无效");
            result.put("reasonCode", "CONFIG_INVALID");
            result.put("errorDetail", "URL 必须以 http:// 或 https:// 开头");
            return result;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            Optional<String> contentType = response.headers().firstValue("content-type");
            boolean success = response.statusCode() >= 200
                    && response.statusCode() < 300
                    && contentType.map(value -> value.toLowerCase().contains("text/event-stream")).orElse(false);
            result.put("success", success);
            result.put("message", success ? "SSE 地址可访问" : "SSE 地址不可用");
            result.put("reasonCode", success ? "CONNECTED" : "START_FAILED");
            result.put("httpStatus", response.statusCode());
            result.put("contentType", contentType.orElse(null));
            if (!success) {
                result.put("errorDetail", "HTTP 状态码: " + response.statusCode()
                        + ", Content-Type: " + contentType.orElse("unknown"));
            }
        } catch (java.net.http.HttpTimeoutException e) {
            result.put("success", false);
            result.put("message", "连接超时");
            result.put("reasonCode", "TIMEOUT");
            result.put("errorDetail", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败");
            result.put("reasonCode", "START_FAILED");
            result.put("errorDetail", e.getMessage());
        }
        return result;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void sendMcpRequest(OutputStream outputStream, String payload) throws IOException {
        writeMcpFrame(outputStream, payload);
    }

    private void sendMcpNotification(OutputStream outputStream, String method) throws IOException {
        writeMcpFrame(outputStream, "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\"}");
    }

    private void writeMcpFrame(OutputStream outputStream, String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + body.length + "\r\n\r\n";
        outputStream.write(header.getBytes(StandardCharsets.US_ASCII));
        outputStream.write(body);
        outputStream.flush();
    }

    private String readMcpMessage(InputStream inputStream, int timeoutSeconds) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        int contentLength = -1;
        while (System.nanoTime() < deadline) {
            String headerLine = readLine(inputStream, deadline);
            if (headerLine == null) {
                continue;
            }
            if (headerLine.isBlank()) {
                if (contentLength > 0) {
                    return readBody(inputStream, contentLength, deadline);
                }
                continue;
            }
            String lower = headerLine.toLowerCase();
            if (lower.startsWith("content-length:")) {
                contentLength = Integer.parseInt(headerLine.substring(headerLine.indexOf(':') + 1).trim());
            }
        }
        throw new java.net.http.HttpTimeoutException("Timed out waiting for MCP response");
    }

    private String readLine(InputStream inputStream, long deadline) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (System.nanoTime() < deadline) {
            if (inputStream.available() <= 0) {
                sleepQuietly(25);
                continue;
            }
            int next = inputStream.read();
            if (next == -1) {
                throw new IOException("MCP process closed stdout");
            }
            if (next == '\n') {
                return line.toString(StandardCharsets.US_ASCII).replace("\r", "");
            }
            line.write(next);
        }
        return null;
    }

    private String readBody(InputStream inputStream, int contentLength, long deadline) throws IOException {
        byte[] body = new byte[contentLength];
        int offset = 0;
        while (offset < contentLength && System.nanoTime() < deadline) {
            if (inputStream.available() <= 0) {
                sleepQuietly(25);
                continue;
            }
            int read = inputStream.read(body, offset, contentLength - offset);
            if (read == -1) {
                throw new IOException("MCP process closed stdout");
            }
            offset += read;
        }
        if (offset < contentLength) {
            throw new java.net.http.HttpTimeoutException("Timed out reading MCP response body");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private int countToolNames(String toolsResponse) {
        if (!toolsResponse.contains("\"tools\"")) {
            return 0;
        }
        return Math.max(0, toolsResponse.split("\"name\"\\s*:").length - 1);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<McpService> searchServices(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllServices();
        }
        return mcpServiceRepository.findByNameContaining(keyword);
    }

    @Override
    public List<McpService> getServicesByType(McpService.McpType type) {
        return mcpServiceRepository.findByType(type);
    }

    @Override
    @Transactional
    public McpService setServiceEnabled(Long id, boolean enabled) {
        McpService service = getServiceById(id);
        service.setEnabled(enabled);
        if (!enabled) {
            service.setStatus(McpService.McpStatus.DISCONNECTED);
            service.setLastError(null);
        }
        return mcpServiceRepository.save(service);
    }

    private void validateEnvVars(String envVars) {
        if (envVars == null || envVars.isBlank()) {
            return;
        }
        for (String line : envVars.split("\\R")) {
            if (line == null || line.isBlank() || !line.contains("=")) {
                continue;
            }
            String key = line.substring(0, line.indexOf('=')).trim().toUpperCase();
            if (SENSITIVE_ENV_SUFFIXES.stream().anyMatch(key::endsWith)) {
                throw new ValidationException("MCP 环境变量禁止包含敏感字段: " + key);
            }
        }
    }

}
