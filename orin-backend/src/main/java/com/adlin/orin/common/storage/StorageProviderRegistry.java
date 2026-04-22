package com.adlin.orin.common.storage;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class StorageProviderRegistry {

    private final Map<StorageBackend, ObjectStorageProvider> providers = new EnumMap<>(StorageBackend.class);

    public StorageProviderRegistry(List<ObjectStorageProvider> providerList) {
        for (ObjectStorageProvider provider : providerList) {
            providers.put(provider.backend(), provider);
        }
    }

    public ObjectStorageProvider provider(StorageBackend backend) {
        ObjectStorageProvider provider = providers.get(backend);
        if (provider != null) {
            return provider;
        }
        return providers.get(StorageBackend.LOCAL);
    }
}
