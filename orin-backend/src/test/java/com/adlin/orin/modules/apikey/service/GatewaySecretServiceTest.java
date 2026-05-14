package com.adlin.orin.modules.apikey.service;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.repository.GatewaySecretRepository;
import com.adlin.orin.security.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewaySecretServiceTest {

    @Mock
    private GatewaySecretRepository gatewaySecretRepository;
    @Mock
    private EncryptionUtil encryptionUtil;

    private GatewaySecretService service;

    @BeforeEach
    void setUp() {
        service = new GatewaySecretService(gatewaySecretRepository, encryptionUtil);
    }

    @Test
    void createMcpEnvSecret_encryptsAndSetsTypeStatus() {
        when(encryptionUtil.isEncryptionEnabled()).thenReturn(true);
        when(encryptionUtil.encrypt("ghp_realtoken")).thenReturn("ENC(ghp_realtoken)");
        when(gatewaySecretRepository.save(any(GatewaySecret.class))).thenAnswer(inv -> inv.getArgument(0));

        GatewaySecret created = service.createMcpEnvSecret("gh token", "ghp_realtoken", "desc", "admin");

        ArgumentCaptor<GatewaySecret> captor = ArgumentCaptor.forClass(GatewaySecret.class);
        verify(gatewaySecretRepository).save(captor.capture());
        GatewaySecret saved = captor.getValue();
        assertEquals(GatewaySecret.SecretType.MCP_ENV, saved.getSecretType());
        assertEquals(GatewaySecret.SecretStatus.ACTIVE, saved.getStatus());
        assertEquals("ENC(ghp_realtoken)", saved.getEncryptedSecret());
        assertEquals("oken", saved.getLast4());
        assertTrue(saved.getSecretId().startsWith("gsec_mcp_"));
        assertEquals(created, saved);
    }

    @Test
    void createMcpEnvSecret_encryptionDisabled_hardRejects() {
        when(encryptionUtil.isEncryptionEnabled()).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.createMcpEnvSecret("gh token", "ghp_realtoken", "desc", "admin"));
        verify(gatewaySecretRepository, never()).save(any());
    }
}
