package com.adlin.orin.modules.apikey.repository;

import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExternalProviderKeyRepository extends JpaRepository<ExternalProviderKey, Long> {
    List<ExternalProviderKey> findByEnabledTrue();

    List<ExternalProviderKey> findByProvider(String provider);
}
