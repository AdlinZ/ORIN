package com.adlin.orin.modules.zeroclaw.dto;

/**
 * ZeroClaw 连接测试请求 DTO
 */
public class ZeroClawConnectionRequest {

    private String endpointUrl;
    private String accessToken;

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
}
