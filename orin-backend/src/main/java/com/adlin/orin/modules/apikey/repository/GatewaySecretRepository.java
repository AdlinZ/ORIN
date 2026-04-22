package com.adlin.orin.modules.apikey.repository;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GatewaySecretRepository extends JpaRepository<GatewaySecret, String> {

    Optional<GatewaySecret> findBySecretId(String secretId);

    List<GatewaySecret> findBySecretTypeAndStatus(GatewaySecret.SecretType secretType,
                                                  GatewaySecret.SecretStatus status);

    List<GatewaySecret> findBySecretTypeOrderByUpdatedAtDesc(GatewaySecret.SecretType secretType);

    List<GatewaySecret> findBySecretTypeAndProviderIgnoreCaseAndStatusOrderByUpdatedAtDesc(
            GatewaySecret.SecretType secretType,
            String provider,
            GatewaySecret.SecretStatus status);
}
