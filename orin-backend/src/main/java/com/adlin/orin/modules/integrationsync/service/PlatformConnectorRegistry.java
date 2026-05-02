package com.adlin.orin.modules.integrationsync.service;

import com.adlin.orin.modules.integrationsync.model.PlatformType;
import com.adlin.orin.modules.integrationsync.spi.PlatformConnector;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PlatformConnectorRegistry {

    private final Map<PlatformType, PlatformConnector> connectors = new EnumMap<>(PlatformType.class);

    public PlatformConnectorRegistry(List<PlatformConnector> connectorList) {
        connectorList.forEach(connector -> connectors.put(connector.platform(), connector));
    }

    public Optional<PlatformConnector> find(PlatformType platformType) {
        return Optional.ofNullable(connectors.get(platformType));
    }

    public PlatformConnector require(PlatformType platformType) {
        return find(platformType)
                .orElseThrow(() -> new IllegalArgumentException("No sync connector registered for platform: " + platformType));
    }

    public List<PlatformConnector> list() {
        return List.copyOf(connectors.values());
    }
}
