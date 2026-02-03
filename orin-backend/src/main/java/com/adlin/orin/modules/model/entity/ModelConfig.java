package com.adlin.orin.modules.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "model_config")
public class ModelConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "api_path")
    private String apiPath;

    @Column(name = "timeout")
    private Integer timeout;

    @Column(name = "llama_factory_path")
    private String llamaFactoryPath;

    @Column(name = "llama_factory_webui")
    private String llamaFactoryWebUI;

    @Column(name = "model_save_path")
    private String modelSavePath;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "dify_endpoint")
    private String difyEndpoint;

    @Column(name = "dify_api_key")
    private String difyApiKey;

    @Column(name = "silicon_flow_endpoint")
    private String siliconFlowEndpoint;

    @Column(name = "silicon_flow_api_key")
    private String siliconFlowApiKey;

    @Column(name = "silicon_flow_model")
    private String siliconFlowModel;

    @Column(name = "vlm_model")
    private String vlmModel;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "system_model")
    private String systemModel;

    @Column(name = "auto_analysis_enabled")
    private Boolean autoAnalysisEnabled;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiPath() {
        return apiPath;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getLlamaFactoryPath() {
        return llamaFactoryPath;
    }

    public void setLlamaFactoryPath(String llamaFactoryPath) {
        this.llamaFactoryPath = llamaFactoryPath;
    }

    public String getLlamaFactoryWebUI() {
        return llamaFactoryWebUI;
    }

    public void setLlamaFactoryWebUI(String llamaFactoryWebUI) {
        this.llamaFactoryWebUI = llamaFactoryWebUI;
    }

    public String getModelSavePath() {
        return modelSavePath;
    }

    public void setModelSavePath(String modelSavePath) {
        this.modelSavePath = modelSavePath;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getDifyEndpoint() {
        return difyEndpoint;
    }

    public void setDifyEndpoint(String difyEndpoint) {
        this.difyEndpoint = difyEndpoint;
    }

    public String getDifyApiKey() {
        return difyApiKey;
    }

    public void setDifyApiKey(String difyApiKey) {
        this.difyApiKey = difyApiKey;
    }

    public String getSiliconFlowEndpoint() {
        return siliconFlowEndpoint;
    }

    public void setSiliconFlowEndpoint(String siliconFlowEndpoint) {
        this.siliconFlowEndpoint = siliconFlowEndpoint;
    }

    public String getSiliconFlowApiKey() {
        return siliconFlowApiKey;
    }

    public void setSiliconFlowApiKey(String siliconFlowApiKey) {
        this.siliconFlowApiKey = siliconFlowApiKey;
    }

    public String getSiliconFlowModel() {
        return siliconFlowModel;
    }

    public void setSiliconFlowModel(String siliconFlowModel) {
        this.siliconFlowModel = siliconFlowModel;
    }

    public String getVlmModel() {
        return vlmModel;
    }

    public void setVlmModel(String vlmModel) {
        this.vlmModel = vlmModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getSystemModel() {
        return systemModel;
    }

    public void setSystemModel(String systemModel) {
        this.systemModel = systemModel;
    }

    public Boolean getAutoAnalysisEnabled() {
        return autoAnalysisEnabled;
    }

    public void setAutoAnalysisEnabled(Boolean autoAnalysisEnabled) {
        this.autoAnalysisEnabled = autoAnalysisEnabled;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}