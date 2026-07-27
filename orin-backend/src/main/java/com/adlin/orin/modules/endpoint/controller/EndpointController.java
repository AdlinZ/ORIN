package com.adlin.orin.modules.endpoint.controller;

import com.adlin.orin.modules.endpoint.dto.EndpointResponse;
import com.adlin.orin.modules.endpoint.dto.PublishEndpointRequest;
import com.adlin.orin.modules.endpoint.service.EndpointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint 业务 API（F05）。
 *
 * <p>Base: /api/v1/endpoints（JWT 鉴权）。
 */
@RestController
@RequestMapping("/api/v1/endpoints")
@RequiredArgsConstructor
public class EndpointController {

    private final EndpointService endpointService;

    /** 发布端点：将已冻结 AgentVersion 发布为 API 或 MCP Server。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointResponse publish(@Valid @RequestBody PublishEndpointRequest request,
                                     Authentication auth) {
        return endpointService.publish(request, auth.getName());
    }

    /** 端点列表。 */
    @GetMapping
    public Page<EndpointResponse> listEndpoints(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return endpointService.listEndpoints(pageable);
    }

    /** 端点详情。 */
    @GetMapping("/{endpointId}")
    public EndpointResponse getEndpoint(@PathVariable String endpointId) {
        return endpointService.getEndpoint(endpointId);
    }

    /** 下线端点。 */
    @PostMapping("/{endpointId}/deactivate")
    public EndpointResponse deactivate(@PathVariable String endpointId, Authentication auth) {
        return endpointService.deactivate(endpointId, auth.getName());
    }

    /** 重新激活端点。 */
    @PostMapping("/{endpointId}/activate")
    public EndpointResponse activate(@PathVariable String endpointId, Authentication auth) {
        return endpointService.activate(endpointId, auth.getName());
    }

    /** F05：为 Endpoint 分配 API Key 访问权限。 */
    @PostMapping("/{endpointId}/api-keys/{apiKeyId}")
    public EndpointResponse assignApiKey(@PathVariable String endpointId,
                                          @PathVariable String apiKeyId) {
        return endpointService.assignApiKey(endpointId, apiKeyId);
    }

    /** F05：撤销 API Key 对 Endpoint 的访问权限。 */
    @DeleteMapping("/{endpointId}/api-keys/{apiKeyId}")
    public EndpointResponse revokeApiKey(@PathVariable String endpointId,
                                          @PathVariable String apiKeyId) {
        return endpointService.revokeApiKey(endpointId, apiKeyId);
    }
}
