package com.adlin.orin.modules.monitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrometheusService {

    private final RestTemplate restTemplate;

    public PrometheusService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); // 2 seconds connect timeout
        factory.setReadTimeout(3000); // 3 seconds read timeout (Fast for dashboard)
        // FORCE DIRECT CONNECTION: Bypass system proxies for queries too
        factory.setProxy(java.net.Proxy.NO_PROXY);
        this.restTemplate = new RestTemplate(factory);
    }

    private URI buildUri(String baseUrl, String endpointPath, String queryParamName, String queryParamValue) {
        String clean = baseUrl.trim();
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }

        // Aggressively strip known suffixes to get to the root
        if (clean.endsWith("/metadata")) {
            clean = clean.substring(0, clean.length() - "/metadata".length());
        }
        if (clean.endsWith("/status/buildinfo")) {
            clean = clean.substring(0, clean.length() - "/status/buildinfo".length());
        }

        // Strip /api/v1 to ensure we have the pure host root (e.g.
        // http://192.168.1.107:9090)
        int idx = clean.indexOf("/api/v1");
        if (idx != -1) {
            clean = clean.substring(0, idx);
        }

        // Always reconstruct from root
        String finalUrl = clean + endpointPath;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(finalUrl);
        if (queryParamName != null && queryParamValue != null) {
            builder.queryParam(queryParamName, queryParamValue);
        }
        return builder.build().toUri();
    }

    /**
     * 查询 Prometheus 的当前数据
     * 
     * @param baseUrl Prometheus 基础地址 (如 http://localhost:9090)
     * @param query   PromQL 查询语句
     * @return 查询结果的核心数值
     */
    public Double queryValue(String baseUrl, String query) {
        try {
            if (baseUrl == null)
                return Double.NaN;

            URI url = buildUri(baseUrl, "/api/v1/query", "query", query);

            log.debug("Probing URL: '{}'", url);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && "success".equals(response.get("status"))) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data == null)
                    return 0.0;

                List<Map<String, Object>> result = (List<Map<String, Object>>) data.get("result");
                if (result != null && !result.isEmpty()) {
                    List<Object> value = (List<Object>) result.get(0).get("value");
                    if (value != null && value.size() >= 2) {
                        return Double.parseDouble(value.get(1).toString());
                    }
                }
            }
        } catch (Exception e) {
            // Log only if it's not a common "no data" case or for debugging
            // For now, let's log debug to avoid flooding if offline
            log.info("Failed to query Prometheus at {} with query [{}]: {}", baseUrl, query, e.getMessage());
        }
        return Double.NaN;
    }

    /**
     * 获取 CPU 使用率 (0-100)
     */

    public Double getCpuUsage(String baseUrl) {
        // Try Linux node_exporter
        String linuxQuery = "100 - (avg(irate(node_cpu_seconds_total{mode=\"idle\"}[1m])) * 100)";
        Double value = queryValue(baseUrl, linuxQuery);

        if (Double.isNaN(value)) {
            // Windows (modern)
            String winQuery = "100 - (avg(irate(windows_cpu_time_total{mode=\"idle\"}[1m])) * 100)";
            value = queryValue(baseUrl, winQuery);
        }

        if (Double.isNaN(value)) {
            // Windows (legacy wmi_exporter)
            String winQueryOld = "100 - (avg(irate(wmi_cpu_time_total{mode=\"idle\"}[1m])) * 100)";
            value = queryValue(baseUrl, winQueryOld);
        }

        return Double.isNaN(value) ? 0.0 : Math.round(value * 100.0) / 100.0;
    }

    /**
     * 获取内存使用率
     */
    public Double getMemoryUsage(String baseUrl) {
        // Try Linux node_exporter
        String linuxQuery = "(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100";
        Double value = queryValue(baseUrl, linuxQuery);

        if (Double.isNaN(value)) {
            // Fallback to Windows windows_exporter (Updated metric names)
            String winQuery = "100 * (1 - (windows_memory_physical_free_bytes / windows_memory_physical_total_bytes))";
            value = queryValue(baseUrl, winQuery);
        }

        return Double.isNaN(value) ? 0.0 : Math.round(value * 100.0) / 100.0;
    }

    /**
     * 获取磁盘使用率
     */
    public Double getDiskUsage(String baseUrl) {
        // Try Linux node_exporter
        String linuxQuery = "(1 - node_filesystem_avail_bytes{mountpoint=\"/\"} / node_filesystem_size_bytes{mountpoint=\"/\"}) * 100";
        Double value = queryValue(baseUrl, linuxQuery);

        if (Double.isNaN(value)) {
            // Fallback to Windows windows_exporter
            String winQuery = "100 * (1 - (windows_logical_disk_free_bytes{volume=\"C:\"} / windows_logical_disk_size_bytes{volume=\"C:\"}))";
            value = queryValue(baseUrl, winQuery);
        }

        return Double.isNaN(value) ? 0.0 : Math.round(value * 100.0) / 100.0;
    }

    /**
     * Query a specific label from the first result of a Prometheus query.
     */
    public String queryLabel(String baseUrl, String query, String label) {
        try {
            if (baseUrl == null)
                return "Unknown";

            URI url = buildUri(baseUrl, "/api/v1/query", "query", query);

            log.debug("Probing URL Label: '{}'", url);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && "success".equals(response.get("status"))) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data == null)
                    return "Unknown";

                List<Map<String, Object>> result = (List<Map<String, Object>>) data.get("result");
                if (result != null && !result.isEmpty()) {
                    // Iterate all results to find the first one containing the label
                    for (Map<String, Object> item : result) {
                        Map<String, Object> metric = (Map<String, Object>) item.get("metric");
                        if (metric != null && metric.containsKey(label)) {
                            return metric.get(label).toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.info("Failed to query Prometheus label at {} query {}: {}", baseUrl, query, e.getMessage());
        }
        return "Unknown";
    }

    /**
     * 获取操作系统名称
     */
    public String getOsName(String baseUrl) {
        // Try Linux directly
        String linuxQuery = "node_os_info";
        String osName = queryLabel(baseUrl, linuxQuery, "pretty_name");

        if ("Unknown".equals(osName)) {
            // Fallback to name if pretty_name is logic
            osName = queryLabel(baseUrl, linuxQuery, "name");
        }

        if ("Unknown".equals(osName)) {
            // Try Windows
            // windows_os_info{product="Microsoft Windows Server 2019
            // Standard",version="10.0.17763"}
            String winQuery = "windows_os_info";
            String product = queryLabel(baseUrl, winQuery, "product");
            if (!"Unknown".equals(product)) {
                return product;
            }
        }
        return osName;
    }

    /**
     * 获取 CPU 核心数
     */
    public Integer getCpuCores(String baseUrl) {
        // Linux: count(count(node_cpu_seconds_total{mode="system"}) by (cpu))
        String linuxQuery = "count(count(node_cpu_seconds_total{mode=\"system\"}) by (cpu))";

        Double val = queryValue(baseUrl, linuxQuery);
        if (Double.isNaN(val)) {
            // Windows fallback: count distinct cores
            // Old/Standard Windows Exporter: windows_cpu_time_total{mode="idle",
            // core="0,1..."}
            String winQuery = "count(windows_cpu_time_total{mode=\"idle\"})";
            val = queryValue(baseUrl, winQuery);
        }

        if (Double.isNaN(val)) {
            // Second fallback for other windows exporter variants
            String winQuery2 = "count(windows_cpu_logical_processor)";
            val = queryValue(baseUrl, winQuery2);
        }

        return !Double.isNaN(val) && val.intValue() > 0 ? val.intValue() : 0;
    }

    /**
     * 获取总内存 (Bytes)
     */
    public Long getTotalMemory(String baseUrl) {
        String linuxQuery = "node_memory_MemTotal_bytes";
        Double val = queryValue(baseUrl, linuxQuery);
        if (Double.isNaN(val)) {
            String winQuery = "windows_memory_physical_total_bytes";
            val = queryValue(baseUrl, winQuery);
        }
        return !Double.isNaN(val) ? val.longValue() : 0L;
    }

    /**
     * 获取 CPU 型号信息
     */
    public String getCpuModel(String baseUrl) {
        // Try Linux: node_cpu_info -> model_name
        String linuxQuery = "node_cpu_info";
        String model = queryLabel(baseUrl, linuxQuery, "model_name");

        if ("Unknown".equals(model)) {
            // Try Windows Exporter 0.31.x: windows_cpu_info -> name
            String winQuery = "windows_cpu_info";
            model = queryLabel(baseUrl, winQuery, "name");
        }

        if ("Unknown".equals(model)) {
            // Try Windows Exporter 0.31.x: windows_cpu_info -> description
            String winQuery = "windows_cpu_info";
            model = queryLabel(baseUrl, winQuery, "description");
        }

        if ("Unknown".equals(model)) {
            // Try older Windows Exporter: windows_cs_processor -> name (Computer System
            // Processor)
            String winQuery = "windows_cs_processor";
            model = queryLabel(baseUrl, winQuery, "name");
        }

        if ("Unknown".equals(model)) {
            // Try Windows (legacy): wmi_cs_processor -> name
            String wmiQuery = "wmi_cs_processor";
            model = queryLabel(baseUrl, wmiQuery, "name");
        }

        return model;
    }

    /**
     * 获取总磁盘容量 (Bytes)
     */
    public Double getTotalDiskSpace(String baseUrl) {
        // Linux: sum(node_filesystem_size_bytes{mountpoint="/"})
        String linuxQuery = "sum(node_filesystem_size_bytes{mountpoint=\"/\"})"; // Only root partition for simplicity
                                                                                 // or sum all
        // Actually better to show total of all relevant partitions or just root. Let's
        // stick to root for now or sum non-tmpfs.
        // Let's use the same logic as usage: just root.
        String linuxRoot = "max(node_filesystem_size_bytes{mountpoint=\"/\"})";

        Double val = queryValue(baseUrl, linuxRoot);
        if (Double.isNaN(val)) {
            // Windows: windows_logical_disk_size_bytes{volume="C:"}
            String winQuery = "windows_logical_disk_size_bytes{volume=\"C:\"}";
            val = queryValue(baseUrl, winQuery);
        }
        return !Double.isNaN(val) ? val : 0.0;
    }

    /**
     * Get GPU Usage %
     */
    public Double getGpuUsage(String baseUrl) {
        // nvidia_smi_utilization_gpu_ratio is 0-1, multiply by 100 for percentage
        String query1 = "avg(nvidia_smi_utilization_gpu_ratio) * 100";
        Double val = queryValue(baseUrl, query1);

        if (Double.isNaN(val)) {
            // Try DCGM: DCGM_FI_DEV_GPU_UTIL (0-100)
            val = queryValue(baseUrl, "avg(DCGM_FI_DEV_GPU_UTIL)");
        }
        return !Double.isNaN(val) ? val : 0.0;
    }

    /**
     * Get GPU Memory Usage %
     */
    public Double getGpuMemoryUsage(String baseUrl) {
        // For single GPU: direct division
        String query = "(nvidia_smi_memory_used_bytes / nvidia_smi_memory_total_bytes) * 100";
        Double val = queryValue(baseUrl, query);

        if (Double.isNaN(val)) {
            // For multiple GPUs: average
            String queryAvg = "avg(nvidia_smi_memory_used_bytes / nvidia_smi_memory_total_bytes) * 100";
            val = queryValue(baseUrl, queryAvg);
        }
        return !Double.isNaN(val) ? val : 0.0;
    }

    /**
     * Get GPU Total Memory in Bytes
     */
    public Long getGpuMemoryTotalBytes(String baseUrl) {
        String query = "nvidia_smi_memory_total_bytes";
        Double val = queryValue(baseUrl, query);
        return !Double.isNaN(val) ? val.longValue() : 0L;
    }

    /**
     * Get GPU Used Memory in Bytes
     */
    public Long getGpuMemoryUsedBytes(String baseUrl) {
        String query = "nvidia_smi_memory_used_bytes";
        Double val = queryValue(baseUrl, query);
        return !Double.isNaN(val) ? val.longValue() : 0L;
    }

    /**
     * Get GPU Model
     */
    public String getGpuModel(String baseUrl) {
        // nvidia_smi_gpu_info -> name label
        return queryLabel(baseUrl, "nvidia_smi_gpu_info", "name");
    }

    /**
     * 获取网络下载速率 (Bytes/s)
     */
    /**
     * 获取网络下载速率 (Bytes/s)
     */
    public Double getNetworkReceiveRate(String baseUrl) {
        // Sum of all non-loopback interfaces
        String linuxQuery = "sum(irate(node_network_receive_bytes_total{device!=\"lo\"}[1m]))";
        Double val = queryValue(baseUrl, linuxQuery);

        if (Double.isNaN(val)) {
            // Windows fallback (exclude loopback and virtuals to avoid double counting or
            // noise)
            String winQuery = "sum(irate(windows_net_bytes_received_total{nic!~'isatap.*|Teredo.*|.*Loopback.*'}[1m]))";
            val = queryValue(baseUrl, winQuery);
        }
        if (Double.isNaN(val)) {
            // Legacy wmi
            String wmiQuery = "sum(irate(wmi_net_bytes_received_total{nic!~'isatap.*|Teredo.*|.*Loopback.*'}[1m]))";
            val = queryValue(baseUrl, wmiQuery);
        }
        return !Double.isNaN(val) ? val : 0.0;
    }

    /**
     * 获取网络上传速率 (Bytes/s)
     */
    public Double getNetworkTransmitRate(String baseUrl) {
        // Sum of all non-loopback interfaces
        String linuxQuery = "sum(irate(node_network_transmit_bytes_total{device!=\"lo\"}[1m]))";
        Double val = queryValue(baseUrl, linuxQuery);

        if (Double.isNaN(val)) {
            // Windows fallback
            String winQuery = "sum(irate(windows_net_bytes_sent_total{nic!~'isatap.*|Teredo.*|.*Loopback.*'}[1m]))";
            val = queryValue(baseUrl, winQuery);
        }
        if (Double.isNaN(val)) {
            // Legacy wmi
            String wmiQuery = "sum(irate(wmi_net_bytes_sent_total{nic!~'isatap.*|Teredo.*|.*Loopback.*'}[1m]))";
            val = queryValue(baseUrl, wmiQuery);
        }
        return !Double.isNaN(val) ? val : 0.0;
    }

    /**
     * Probe if Prometheus is reachable.
     * Throws exception if unreachable.
     */
    public Map<String, String> probe(String baseUrl) {
        if (baseUrl == null)
            throw new IllegalArgumentException("URL cannot be null");

        // Automatically append the standard buildinfo endpoint
        URI url = buildUri(baseUrl, "/api/v1/status/buildinfo", null, null);

        log.debug("Probing Connectivity: {}", url);
        Map<String, String> result = new HashMap<>();
        result.put("targetUrl", url.toString());

        try {
            // Use a custom RestTemplate with specific timeout for probe
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000); // 5s connect
            factory.setReadTimeout(10000); // 10s read

            // FORCE DIRECT CONNECTION: Bypass system proxies that might choke on LAN IPs
            factory.setProxy(java.net.Proxy.NO_PROXY);

            RestTemplate probeTemplate = new RestTemplate(factory);

            // Expecting valid JSON with "success"
            String response = probeTemplate.getForObject(url, String.class);
            log.debug("Probe response: {}", response);

            result.put("response", response);

            if (response == null || !response.contains("success")) {
                throw new RuntimeException("Invalid response: "
                        + (response != null && response.length() > 100 ? response.substring(0, 100) + "..."
                                : response));
            }
            return result;
        } catch (Exception e) {
            log.warn("Probe failed for {}: {}", url, e.getMessage());
            throw new RuntimeException("Probe failed: " + e.getMessage(), e);
        }
    }
}
