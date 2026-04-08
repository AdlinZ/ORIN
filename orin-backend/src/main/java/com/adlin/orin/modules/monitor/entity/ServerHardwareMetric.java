package com.adlin.orin.modules.monitor.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 服务器硬件监控指标实体
 * 记录服务器的 CPU、内存、磁盘、网络等硬件资源使用情况
 */
@Entity
@Table(name = "server_hardware_metrics", indexes = {
        @Index(name = "idx_hardware_time", columnList = "timestamp DESC")
})
public class ServerHardwareMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 数据采集时间戳
     */
    @Column(nullable = false)
    private Long timestamp;

    /**
     * 采集时间 (本地时间)
     */
    private LocalDateTime recordedAt;

    /**
     * CPU 使用率 (0-100%)
     */
    private Double cpuUsage;

    /**
     * 内存使用率 (0-100%)
     */
    private Double memoryUsage;

    /**
     * 磁盘使用率 (0-100%)
     */
    private Double diskUsage;

    /**
     * GPU 使用率 (0-100%)
     */
    private Double gpuUsage;

    /**
     * GPU 内存使用率 (0-100%)
     */
    private Double gpuMemoryUsage;

    /**
     * CPU 核心数
     */
    private Integer cpuCores;

    /**
     * 总内存 (字节)
     */
    private Long memoryTotal;

    /**
     * 已用内存 (字节)
     */
    private Long memoryUsed;

    /**
     * 总磁盘空间 (字节)
     */
    private Long diskTotal;

    /**
     * 已用磁盘空间 (字节)
     */
    private Long diskUsed;

    /**
     * GPU 型号
     */
    private String gpuModel;

    /**
     * GPU 显存信息 (如 "17 MB / 8 GB")
     */
    private String gpuMemory;

    /**
     * 网络下载速率
     */
    private String networkDownload;

    /**
     * 网络上传速率
     */
    private String networkUpload;

    /**
     * 操作系统
     */
    private String os;

    /**
     * CPU 型号
     */
    private String cpuModel;

    /**
     * Prometheus 在线状态
     */
    private Boolean online;

    /**
     * 错误信息 (如果有)
     */
    private String errorMessage;

    /**
     * 服务器唯一标识
     */
    private String serverId;

    /**
     * 服务器显示名
     */
    private String serverName;

    public ServerHardwareMetric() {
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(Double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(Double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public Double getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(Double diskUsage) {
        this.diskUsage = diskUsage;
    }

    public Double getGpuUsage() {
        return gpuUsage;
    }

    public void setGpuUsage(Double gpuUsage) {
        this.gpuUsage = gpuUsage;
    }

    public Double getGpuMemoryUsage() {
        return gpuMemoryUsage;
    }

    public void setGpuMemoryUsage(Double gpuMemoryUsage) {
        this.gpuMemoryUsage = gpuMemoryUsage;
    }

    public Integer getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(Integer cpuCores) {
        this.cpuCores = cpuCores;
    }

    public Long getMemoryTotal() {
        return memoryTotal;
    }

    public void setMemoryTotal(Long memoryTotal) {
        this.memoryTotal = memoryTotal;
    }

    public Long getMemoryUsed() {
        return memoryUsed;
    }

    public void setMemoryUsed(Long memoryUsed) {
        this.memoryUsed = memoryUsed;
    }

    public Long getDiskTotal() {
        return diskTotal;
    }

    public void setDiskTotal(Long diskTotal) {
        this.diskTotal = diskTotal;
    }

    public Long getDiskUsed() {
        return diskUsed;
    }

    public void setDiskUsed(Long diskUsed) {
        this.diskUsed = diskUsed;
    }

    public String getGpuModel() {
        return gpuModel;
    }

    public void setGpuModel(String gpuModel) {
        this.gpuModel = gpuModel;
    }

    public String getGpuMemory() {
        return gpuMemory;
    }

    public void setGpuMemory(String gpuMemory) {
        this.gpuMemory = gpuMemory;
    }

    public String getNetworkDownload() {
        return networkDownload;
    }

    public void setNetworkDownload(String networkDownload) {
        this.networkDownload = networkDownload;
    }

    public String getNetworkUpload() {
        return networkUpload;
    }

    public void setNetworkUpload(String networkUpload) {
        this.networkUpload = networkUpload;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public static ServerHardwareMetricBuilder builder() {
        return new ServerHardwareMetricBuilder();
    }

    public static class ServerHardwareMetricBuilder {
        private Long id;
        private Long timestamp;
        private LocalDateTime recordedAt;
        private Double cpuUsage;
        private Double memoryUsage;
        private Double diskUsage;
        private Double gpuUsage;
        private Double gpuMemoryUsage;
        private Integer cpuCores;
        private Long memoryTotal;
        private Long memoryUsed;
        private Long diskTotal;
        private Long diskUsed;
        private String gpuModel;
        private String gpuMemory;
        private String networkDownload;
        private String networkUpload;
        private String os;
        private String cpuModel;
        private Boolean online;
        private String errorMessage;
        private String serverId;
        private String serverName;

        public ServerHardwareMetricBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ServerHardwareMetricBuilder timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ServerHardwareMetricBuilder recordedAt(LocalDateTime recordedAt) {
            this.recordedAt = recordedAt;
            return this;
        }

        public ServerHardwareMetricBuilder cpuUsage(Double cpuUsage) {
            this.cpuUsage = cpuUsage;
            return this;
        }

        public ServerHardwareMetricBuilder memoryUsage(Double memoryUsage) {
            this.memoryUsage = memoryUsage;
            return this;
        }

        public ServerHardwareMetricBuilder diskUsage(Double diskUsage) {
            this.diskUsage = diskUsage;
            return this;
        }

        public ServerHardwareMetricBuilder gpuUsage(Double gpuUsage) {
            this.gpuUsage = gpuUsage;
            return this;
        }

        public ServerHardwareMetricBuilder gpuMemoryUsage(Double gpuMemoryUsage) {
            this.gpuMemoryUsage = gpuMemoryUsage;
            return this;
        }

        public ServerHardwareMetricBuilder cpuCores(Integer cpuCores) {
            this.cpuCores = cpuCores;
            return this;
        }

        public ServerHardwareMetricBuilder memoryTotal(Long memoryTotal) {
            this.memoryTotal = memoryTotal;
            return this;
        }

        public ServerHardwareMetricBuilder memoryUsed(Long memoryUsed) {
            this.memoryUsed = memoryUsed;
            return this;
        }

        public ServerHardwareMetricBuilder diskTotal(Long diskTotal) {
            this.diskTotal = diskTotal;
            return this;
        }

        public ServerHardwareMetricBuilder diskUsed(Long diskUsed) {
            this.diskUsed = diskUsed;
            return this;
        }

        public ServerHardwareMetricBuilder gpuModel(String gpuModel) {
            this.gpuModel = gpuModel;
            return this;
        }

        public ServerHardwareMetricBuilder gpuMemory(String gpuMemory) {
            this.gpuMemory = gpuMemory;
            return this;
        }

        public ServerHardwareMetricBuilder networkDownload(String networkDownload) {
            this.networkDownload = networkDownload;
            return this;
        }

        public ServerHardwareMetricBuilder networkUpload(String networkUpload) {
            this.networkUpload = networkUpload;
            return this;
        }

        public ServerHardwareMetricBuilder os(String os) {
            this.os = os;
            return this;
        }

        public ServerHardwareMetricBuilder cpuModel(String cpuModel) {
            this.cpuModel = cpuModel;
            return this;
        }

        public ServerHardwareMetricBuilder online(Boolean online) {
            this.online = online;
            return this;
        }

        public ServerHardwareMetricBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ServerHardwareMetricBuilder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }

        public ServerHardwareMetricBuilder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public ServerHardwareMetric build() {
            ServerHardwareMetric metric = new ServerHardwareMetric();
            metric.setId(id);
            metric.setTimestamp(timestamp);
            metric.setRecordedAt(recordedAt);
            metric.setCpuUsage(cpuUsage);
            metric.setMemoryUsage(memoryUsage);
            metric.setDiskUsage(diskUsage);
            metric.setGpuUsage(gpuUsage);
            metric.setGpuMemoryUsage(gpuMemoryUsage);
            metric.setCpuCores(cpuCores);
            metric.setMemoryTotal(memoryTotal);
            metric.setMemoryUsed(memoryUsed);
            metric.setDiskTotal(diskTotal);
            metric.setDiskUsed(diskUsed);
            metric.setGpuModel(gpuModel);
            metric.setGpuMemory(gpuMemory);
            metric.setNetworkDownload(networkDownload);
            metric.setNetworkUpload(networkUpload);
            metric.setOs(os);
            metric.setCpuModel(cpuModel);
            metric.setOnline(online);
            metric.setErrorMessage(errorMessage);
            metric.setServerId(serverId);
            metric.setServerName(serverName);
            return metric;
        }
    }
}
