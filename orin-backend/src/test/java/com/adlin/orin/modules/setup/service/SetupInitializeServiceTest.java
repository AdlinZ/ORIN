package com.adlin.orin.modules.setup.service;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.model.entity.ModelConfig;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.adlin.orin.modules.setup.dto.SetupDtos.AdminSetupRequest;
import com.adlin.orin.modules.setup.dto.SetupDtos.ClientAccessSetupRequest;
import com.adlin.orin.modules.setup.dto.SetupDtos.InitializeSetupRequest;
import com.adlin.orin.modules.setup.dto.SetupDtos.ProviderSetupRequest;
import com.adlin.orin.modules.system.entity.SysRole;
import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysRoleRepository;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.modules.system.repository.SysUserRoleRepository;
import com.adlin.orin.security.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SetupInitializeServiceTest {

    private SetupStatusService setupStatusService;
    private SysUserRepository userRepository;
    private SysRoleRepository roleRepository;
    private SysUserRoleRepository userRoleRepository;
    private ModelConfigService modelConfigService;
    private GatewaySecretService gatewaySecretService;
    private EncryptionUtil encryptionUtil;
    private AuditHelper auditHelper;
    private SetupInitializeService service;

    @BeforeEach
    void setUp() {
        setupStatusService = mock(SetupStatusService.class);
        userRepository = mock(SysUserRepository.class);
        roleRepository = mock(SysRoleRepository.class);
        userRoleRepository = mock(SysUserRoleRepository.class);
        modelConfigService = mock(ModelConfigService.class);
        gatewaySecretService = mock(GatewaySecretService.class);
        encryptionUtil = mock(EncryptionUtil.class);
        auditHelper = mock(AuditHelper.class);
        service = new SetupInitializeService(
                setupStatusService,
                userRepository,
                roleRepository,
                userRoleRepository,
                new BCryptPasswordEncoder(),
                modelConfigService,
                gatewaySecretService,
                encryptionUtil,
                auditHelper);

        when(setupStatusService.isSetupWriteEnabled()).thenReturn(true);
        when(setupStatusService.isSetupCompleted()).thenReturn(false);
        when(roleRepository.findByRoleCode("ROLE_ADMIN")).thenReturn(Optional.of(role(1L, "ROLE_ADMIN")));
        when(roleRepository.findByRoleCode("ROLE_SUPER_ADMIN")).thenReturn(Optional.of(role(2L, "ROLE_SUPER_ADMIN")));
        when(userRoleRepository.existsByUserIdAndRoleId(anyLong(), anyLong())).thenReturn(false);
        when(userRepository.save(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            if (user.getUserId() == null) {
                user.setUserId(100L);
            }
            return user;
        });
    }

    @Test
    void createsNewSuperAdminAndClientAccessKey() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("owner@orin.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        GatewaySecret clientSecret = GatewaySecret.builder()
                .secretId("gsec_client")
                .keyPrefix("sk-orin-test")
                .last4("abcd")
                .build();
        when(gatewaySecretService.createClientAccessSecret(eq("100"), anyString(), anyString(), isNull(), isNull(), isNull(), isNull(), eq("setup")))
                .thenReturn(new GatewaySecretService.ClientAccessSecretWithValue(clientSecret, "sk-orin-test-secret"));

        var response = service.initialize(new InitializeSetupRequest(
                new AdminSetupRequest("owner", "strong-password", "Owner", "owner@orin.local"),
                null,
                new ClientAccessSetupRequest(true, "MCP key", "setup key")));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getAdminUsername()).isEqualTo("owner");
        assertThat(response.getClientAccessKey().getSecretKey()).isEqualTo("sk-orin-test-secret");
        verify(setupStatusService).markSetupCompleted();
        verify(userRoleRepository, times(2)).save(any());
    }

    @Test
    void disablesDefaultAdminWhenReplacementAdminIsCreated() {
        SysUser defaultAdmin = new SysUser();
        defaultAdmin.setUserId(1L);
        defaultAdmin.setUsername("admin");
        defaultAdmin.setStatus("ENABLED");
        when(userRepository.findByUsername("owner")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("owner@orin.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(defaultAdmin));

        service.initialize(new InitializeSetupRequest(
                new AdminSetupRequest("owner", "strong-password", "Owner", "owner@orin.local"),
                null,
                new ClientAccessSetupRequest(false, null, null)));

        assertThat(defaultAdmin.getStatus()).isEqualTo("DISABLED");
        verify(userRepository, atLeastOnce()).save(defaultAdmin);
    }

    @Test
    void savesProviderCredentialWithoutWritingLegacyApiKeyFields() {
        when(encryptionUtil.isEncryptionEnabled()).thenReturn(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@orin.local")).thenReturn(Optional.empty());
        ModelConfig config = new ModelConfig();
        when(modelConfigService.getConfig()).thenReturn(config);
        GatewaySecret providerSecret = GatewaySecret.builder()
                .secretId("gsec_provider")
                .provider("siliconflow")
                .last4("wxyz")
                .build();
        when(gatewaySecretService.upsertProviderCredential(eq("siliconflow"), anyString(), eq("secret-value"),
                eq("https://api.siliconflow.cn/v1"), anyString(), eq(true), eq("setup")))
                .thenReturn(providerSecret);

        var response = service.initialize(new InitializeSetupRequest(
                new AdminSetupRequest("admin", "strong-password", "Administrator", "admin@orin.local"),
                new ProviderSetupRequest("siliconflow", "https://api.siliconflow.cn/v1", "secret-value", "Qwen/Qwen2"),
                new ClientAccessSetupRequest(false, null, null)));

        assertThat(response.getProviderSecret().getId()).isEqualTo("gsec_provider");
        assertThat(config.getSiliconFlowEndpoint()).isEqualTo("https://api.siliconflow.cn/v1");
        assertThat(config.getSiliconFlowModel()).isEqualTo("Qwen/Qwen2");
        assertThat(config.getSiliconFlowApiKey()).isNull();
        verify(modelConfigService).updateConfig(config);
    }

    @Test
    void rejectsRepeatedInitialization() {
        when(setupStatusService.isSetupCompleted()).thenReturn(true);

        assertThatThrownBy(() -> service.initialize(new InitializeSetupRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SETUP_ALREADY_COMPLETED");
    }

    private SysRole role(Long id, String code) {
        return SysRole.builder()
                .roleId(id)
                .roleCode(code)
                .roleName(code)
                .build();
    }
}
