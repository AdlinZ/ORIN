package com.adlin.orin.modules.apikey.service;

import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProviderKeyService {

    private final GatewaySecretService gatewaySecretService;

    public List<ExternalProviderKey> getAllKeys() {
        List<GatewaySecret> secrets = gatewaySecretService.listByType(GatewaySecret.SecretType.PROVIDER_CREDENTIAL);
        List<ExternalProviderKey> result = new ArrayList<>();
        for (GatewaySecret secret : secrets) {
            result.add(toExternalProviderKey(secret, false));
        }
        return result;
    }

    public ExternalProviderKey saveKey(ExternalProviderKey key) {
        String secret = key.getApiKey();
        if (secret != null && secret.startsWith("****")) {
            secret = null;
        }
        GatewaySecret saved = gatewaySecretService.upsertProviderCredential(
                key.getProvider(),
                key.getName(),
                secret,
                key.getBaseUrl(),
                key.getDescription(),
                key.getEnabled() == null || key.getEnabled(),
                "system");
        return toExternalProviderKey(saved, false);
    }

    public void deleteKey(Long id) {
        GatewaySecret secret = requireByLegacyId(id);
        gatewaySecretService.deleteBySecretId(secret.getSecretId(), "system");
    }

    public ExternalProviderKey toggleStatus(Long id) {
        GatewaySecret secret = requireByLegacyId(id);
        GatewaySecret.SecretStatus nextStatus = secret.getStatus() == GatewaySecret.SecretStatus.ACTIVE
                ? GatewaySecret.SecretStatus.DISABLED
                : GatewaySecret.SecretStatus.ACTIVE;
        gatewaySecretService.updateStatus(secret.getSecretId(), nextStatus, "system");
        Optional<GatewaySecret> updated = gatewaySecretService.findBySecretId(secret.getSecretId());
        return toExternalProviderKey(updated.orElse(secret), false);
    }

    public List<ExternalProviderKey> getActiveKeys() {
        List<GatewaySecret> secrets = gatewaySecretService.listByType(GatewaySecret.SecretType.PROVIDER_CREDENTIAL);
        List<ExternalProviderKey> result = new ArrayList<>();
        for (GatewaySecret secret : secrets) {
            if (secret.getStatus() == GatewaySecret.SecretStatus.ACTIVE) {
                result.add(toExternalProviderKey(secret, true));
            }
        }
        return result;
    }

    private GatewaySecret requireByLegacyId(Long id) {
        String legacySecretId = "gsec_provider_" + id;
        return gatewaySecretService.findBySecretId(legacySecretId)
                .orElseThrow();
    }

    private ExternalProviderKey toExternalProviderKey(GatewaySecret secret, boolean withPlainSecret) {
        ExternalProviderKey key = new ExternalProviderKey();
        key.setId(legacyId(secret.getSecretId()));
        key.setName(secret.getName());
        key.setProvider(secret.getProvider() != null ? secret.getProvider().toLowerCase(Locale.ROOT) : null);
        key.setBaseUrl(secret.getBaseUrl());
        key.setDescription(secret.getDescription());
        key.setEnabled(secret.getStatus() == GatewaySecret.SecretStatus.ACTIVE);
        if (withPlainSecret) {
            String plain = gatewaySecretService.revealSecret(secret.getSecretId()).orElse("");
            key.setApiKey(plain);
        } else {
            String tail = secret.getLast4() != null ? secret.getLast4() : "****";
            key.setApiKey("****" + tail);
        }
        key.setCreateTime(secret.getCreatedAt());
        key.setUpdateTime(secret.getUpdatedAt());
        return key;
    }

    private Long legacyId(String secretId) {
        if (secretId != null && secretId.startsWith("gsec_provider_")) {
            String tail = secretId.substring("gsec_provider_".length());
            try {
                return Long.parseLong(tail);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
