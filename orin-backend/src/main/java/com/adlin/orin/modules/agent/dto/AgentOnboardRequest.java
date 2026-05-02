package com.adlin.orin.modules.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentOnboardRequest {
    private String name;
    private String endpointUrl;
    private String apiKey;
    private String datasetApiKey;
    private String providerType;
    private Long providerKeyId;

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDatasetApiKey() {
        return datasetApiKey;
    }

    public void setDatasetApiKey(String datasetApiKey) {
        this.datasetApiKey = datasetApiKey;
    }

    private String model;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    private Double temperature;
    private Double topP;
    private Integer maxTokens;

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    private String systemPrompt;

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    private Boolean toolCallingOverride;

    public Boolean getToolCallingOverride() {
        return toolCallingOverride;
    }

    public void setToolCallingOverride(Boolean toolCallingOverride) {
        this.toolCallingOverride = toolCallingOverride;
    }

    // TTS & Image Parameters
    private String voice;
    private Double speed;
    private Integer gain;
    private String imageSize;
    private String seed;
    private Double guidanceScale;
    private Integer inferenceSteps;
    private String negativePrompt;

    // Video Parameters
    private String videoSize;
    private String videoDuration;
}
