package com.adlin.orin.modules.setup.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.model.entity.ModelConfig;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.adlin.orin.modules.setup.dto.SetupDtos.AdminSetupRequest;
import com.adlin.orin.modules.setup.dto.SetupDtos.ClientAccessSetupRequest;
import com.adlin.orin.modules.setup.dto.SetupDtos.InitializeSetupRequest;
import com.adlin.orin.modules.setup.dto.SetupDtos.InitializeSetupResponse;
import com.adlin.orin.modules.setup.dto.SetupDtos.ProviderSetupRequest;
import com.adlin.orin.modules.setup.dto.SetupDtos.SecretSummary;
import com.adlin.orin.modules.system.entity.SysRole;
import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.entity.SysUserRole;
import com.adlin.orin.modules.system.repository.SysRoleRepository;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.modules.system.repository.SysUserRoleRepository;
import com.adlin.orin.security.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SetupInitializeService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    private final SetupStatusService setupStatusService;
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelConfigService modelConfigService;
    private final GatewaySecretService gatewaySecretService;
    private final EncryptionUtil encryptionUtil;
    private final AuditHelper auditHelper;

    public boolean testProvider(ProviderSetupRequest request) {
        String provider = normalizeProvider(request.getProvider());
        String endpoint = trim(request.getEndpoint());
        String apiKey = trim(request.getApiKey());
        String model = trim(request.getModel());
        return switch (provider) {
            case "siliconflow" -> modelConfigService.testSiliconFlowConnection(endpoint, apiKey, model);
            case "ollama" -> modelConfigService.testOllamaConnection(endpoint, apiKey, model);
            case "dify" -> modelConfigService.testDifyConnection(endpoint, apiKey);
            default -> throw new BusinessException(ErrorCode.AGENT_PROVIDER_UNSUPPORTED, "不支持的 Provider: " + provider);
        };
    }

    @Transactional
    public InitializeSetupResponse initialize(InitializeSetupRequest request) {
        if (!setupStatusService.isSetupWriteEnabled()) {
            throw new IllegalStateException("SETUP_DISABLED");
        }
        if (setupStatusService.isSetupCompleted()) {
            throw new IllegalStateException("SETUP_ALREADY_COMPLETED");
        }

        AdminSetupRequest adminRequest = request != null ? request.getAdmin() : null;
        SysUser admin = upsertAdmin(adminRequest);
        SecretSummary providerSecret = saveProviderIfRequested(request != null ? request.getProvider() : null);
        SecretSummary clientAccessKey = createClientAccessIfRequested(
                admin,
                request != null ? request.getClientAccess() : null);

        setupStatusService.markSetupCompleted();
        auditHelper.log(String.valueOf(admin.getUserId()), "SETUP_INITIALIZE", "/api/v1/setup/initialize",
                "action=initialize;adminUsername=" + admin.getUsername()
                        + ";providerConfigured=" + (providerSecret != null)
                        + ";clientAccessCreated=" + (clientAccessKey != null),
                true, null);

        return InitializeSetupResponse.builder()
                .success(true)
                .message("初始化完成")
                .adminUsername(admin.getUsername())
                .providerSecret(providerSecret)
                .clientAccessKey(clientAccessKey)
                .build();
    }

    private SysUser upsertAdmin(AdminSetupRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_REQUIRED_FIELD, "管理员信息不能为空");
        }
        String username = trim(request.getUsername());
        String password = trim(request.getPassword());
        if (username.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_REQUIRED_FIELD, "管理员用户名不能为空");
        }
        if (password.length() < 8) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员密码至少 8 位");
        }

        String email = trim(request.getEmail());
        Optional<SysUser> existingEmail = email.isBlank()
                ? Optional.empty()
                : userRepository.findByEmail(email);
        SysUser user = userRepository.findByUsername(username).orElseGet(SysUser::new);
        if (existingEmail.isPresent()
                && user.getUserId() != null
                && !existingEmail.get().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员邮箱已被其他用户使用");
        }
        if (existingEmail.isPresent() && user.getUserId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员邮箱已被其他用户使用");
        }

        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(defaultIfBlank(request.getNickname(), "Administrator"));
        if (!email.isBlank()) {
            user.setEmail(email);
        }
        user.setStatus("ENABLED");
        user.setRole(ROLE_SUPER_ADMIN);
        user = userRepository.save(user);

        ensureRole(user, ROLE_ADMIN, "系统管理员", "拥有系统所有权限,可管理用户、配置、API等");
        ensureRole(user, ROLE_SUPER_ADMIN, "超级管理员", "拥有全局控制权限，可管理组织与平台全部能力");
        disableDefaultAdminIfReplaced(user);
        return user;
    }

    private void disableDefaultAdminIfReplaced(SysUser selectedAdmin) {
        if ("admin".equals(selectedAdmin.getUsername())) {
            return;
        }
        userRepository.findByUsername("admin").ifPresent(defaultAdmin -> {
            if (!defaultAdmin.getUserId().equals(selectedAdmin.getUserId())) {
                defaultAdmin.setStatus("DISABLED");
                userRepository.save(defaultAdmin);
            }
        });
    }

    private void ensureRole(SysUser user, String roleCode, String roleName, String description) {
        SysRole role = roleRepository.findByRoleCode(roleCode).orElseGet(() -> roleRepository.save(SysRole.builder()
                .roleCode(roleCode)
                .roleName(roleName)
                .description(description)
                .build()));
        if (!userRoleRepository.existsByUserIdAndRoleId(user.getUserId(), role.getRoleId())) {
            userRoleRepository.save(SysUserRole.builder()
                    .userId(user.getUserId())
                    .roleId(role.getRoleId())
                    .build());
        }
    }

    private SecretSummary saveProviderIfRequested(ProviderSetupRequest request) {
        if (request == null) {
            return null;
        }
        String provider = normalizeProvider(request.getProvider());
        String endpoint = trim(request.getEndpoint());
        String apiKey = trim(request.getApiKey());
        String model = trim(request.getModel());
        if (provider.isBlank() || (endpoint.isBlank() && apiKey.isBlank() && model.isBlank())) {
            return null;
        }
        if (!isSupportedProvider(provider)) {
            throw new BusinessException(ErrorCode.AGENT_PROVIDER_UNSUPPORTED, "不支持的 Provider: " + provider);
        }
        if (!apiKey.isBlank() && !encryptionUtil.isEncryptionEnabled()) {
            throw new IllegalStateException("ENCRYPTION_KEY_REQUIRED");
        }

        ModelConfig config = modelConfigService.getConfig();
        applyProviderConfig(config, provider, endpoint, model);
        modelConfigService.updateConfig(config);

        if (apiKey.isBlank()) {
            return null;
        }

        GatewaySecret secret = gatewaySecretService.upsertProviderCredential(
                provider,
                provider + " setup credential",
                apiKey,
                endpoint,
                "Created by first-run setup wizard",
                true,
                "setup");
        auditHelper.log("SYSTEM", "SETUP_PROVIDER_TEST", "/api/v1/setup/initialize",
                "action=provider-save;provider=" + provider + ";secretId=" + secret.getSecretId(),
                true, null);
        return SecretSummary.builder()
                .id(secret.getSecretId())
                .provider(secret.getProvider())
                .last4(secret.getLast4())
                .build();
    }

    private void applyProviderConfig(ModelConfig config, String provider, String endpoint, String model) {
        switch (provider) {
            case "siliconflow" -> {
                if (!endpoint.isBlank()) {
                    config.setSiliconFlowEndpoint(endpoint);
                }
                if (!model.isBlank()) {
                    config.setSiliconFlowModel(model);
                }
            }
            case "ollama" -> {
                if (!endpoint.isBlank()) {
                    config.setOllamaEndpoint(endpoint);
                }
                if (!model.isBlank()) {
                    config.setOllamaModel(model);
                }
            }
            case "dify" -> {
                if (!endpoint.isBlank()) {
                    config.setDifyEndpoint(endpoint);
                }
            }
            default -> throw new BusinessException(ErrorCode.AGENT_PROVIDER_UNSUPPORTED, "不支持的 Provider: " + provider);
        }
        config.setDifyApiKey(null);
        config.setSiliconFlowApiKey(null);
        config.setOllamaApiKey(null);
    }

    private SecretSummary createClientAccessIfRequested(SysUser admin, ClientAccessSetupRequest request) {
        if (request == null || !request.isCreate()) {
            return null;
        }
        GatewaySecretService.ClientAccessSecretWithValue created = gatewaySecretService.createClientAccessSecret(
                String.valueOf(admin.getUserId()),
                defaultIfBlank(request.getName(), "First-run MCP access key"),
                defaultIfBlank(request.getDescription(), "Created by ORIN setup wizard"),
                null,
                null,
                null,
                null,
                "setup");
        GatewaySecret secret = created.getSecret();
        auditHelper.log(String.valueOf(admin.getUserId()), "SETUP_CLIENT_KEY_CREATE", "/api/v1/setup/initialize",
                "action=client-access-create;keyId=" + secret.getSecretId(), true, null);
        return SecretSummary.builder()
                .id(secret.getSecretId())
                .keyPrefix(secret.getKeyPrefix())
                .last4(secret.getLast4())
                .secretKey(created.getSecretValue())
                .warning("请妥善保存此密钥，它只会显示一次！")
                .build();
    }

    private boolean isSupportedProvider(String provider) {
        return "siliconflow".equals(provider) || "ollama".equals(provider) || "dify".equals(provider);
    }

    private String normalizeProvider(String provider) {
        return trim(provider).toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed.isBlank() ? fallback : trimmed;
    }
}
