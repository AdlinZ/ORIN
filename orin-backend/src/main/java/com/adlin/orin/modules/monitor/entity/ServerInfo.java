package com.adlin.orin.modules.monitor.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 服务器静态信息实体
 * 存储服务器的硬件规格信息，这些信息在服务器运行期间不会变化
 * 只有在服务器首次上线或重启时才需要更新
 */
@Entity
@Table(name = "server_info")
public class ServerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 服务器唯一标识
     */
    @Column(nullable = false, unique = true)
    private String serverId;

    /**
     * 服务器名称/别名
     */
    private String serverName;

    /**
     * CPU 型号
     */
    private String cpuModel;

    /**
     * CPU 核心数
     */
    private Integer cpuCores;

    /**
     * 总内存 (字节)
     */
    private Long memoryTotal;

    /**
     * 总磁盘空间 (字节)
     */
    private Long diskTotal;

    /**
     * GPU 型号
     */
    private String gpuModel;

    /**
     * GPU 显存总量 (字节)
     */
    private Long gpuMemoryTotal;

    /**
     * 操作系统
     */
    private String os;

    /**
     * Prometheus URL
     */
    private String prometheusUrl;

    /**
     * 首次上线时间
     */
    private LocalDateTime firstOnlineTime;

    /**
     * 最后在线时间
     */
    private LocalDateTime lastOnlineTime;

    /**
     * 最后离线时间
     */
    private LocalDateTime lastOfflineTime;

    /**
     * 当前在线状态
     */
    private Boolean online;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
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

    public Long getDiskTotal() {
        return diskTotal;
    }

    public void setDiskTotal(Long diskTotal) {
        this.diskTotal = diskTotal;
    }

    public String getGpuModel() {
        return gpuModel;
    }

    public void setGpuModel(String gpuModel) {
        this.gpuModel = gpuModel;
    }

    public Long getGpuMemoryTotal() {
        return gpuMemoryTotal;
    }

    public void setGpuMemoryTotal(Long gpuMemoryTotal) {
        this.gpuMemoryTotal = gpuMemoryTotal;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getPrometheusUrl() {
        return prometheusUrl;
    }

    public void setPrometheusUrl(String prometheusUrl) {
        this.prometheusUrl = prometheusUrl;
    }

    public LocalDateTime getFirstOnlineTime() {
        return firstOnlineTime;
    }

    public void setFirstOnlineTime(LocalDateTime firstOnlineTime) {
        this.firstOnlineTime = firstOnlineTime;
    }

    public LocalDateTime getLastOnlineTime() {
        return lastOnlineTime;
    }

    public void setLastOnlineTime(LocalDateTime lastOnlineTime) {
        this.lastOnlineTime = lastOnlineTime;
    }

    public LocalDateTime getLastOfflineTime() {
        return lastOfflineTime;
    }

    public void setLastOfflineTime(LocalDateTime lastOfflineTime) {
        this.lastOfflineTime = lastOfflineTime;
    }

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
