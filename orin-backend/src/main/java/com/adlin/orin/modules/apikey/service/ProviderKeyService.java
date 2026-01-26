package com.adlin.orin.modules.apikey.service;

import com.adlin.orin.modules.apikey.entity.ExternalProviderKey;
import com.adlin.orin.modules.apikey.repository.ExternalProviderKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderKeyService {

    private final ExternalProviderKeyRepository repository;

    public List<ExternalProviderKey> getAllKeys() {
        return repository.findAll();
    }

    public ExternalProviderKey saveKey(ExternalProviderKey key) {
        return repository.save(key);
    }

    public void deleteKey(Long id) {
        repository.deleteById(id);
    }

    public ExternalProviderKey toggleStatus(Long id) {
        ExternalProviderKey key = repository.findById(id).orElseThrow();
        key.setEnabled(!key.getEnabled());
        return repository.save(key);
    }

    public List<ExternalProviderKey> getActiveKeys() {
        return repository.findByEnabledTrue();
    }
}
