package com.ikoyski.homelab.management.mcp.inventory.application;

import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Application use case for collecting Docker inventory through an infrastructure port. */
@Service
@ConditionalOnProperty(prefix = "homelab.docker.portainer", name = "base-url")
public class DockerInventoryUseCase {
    private final DockerInventoryProvider provider;

    public DockerInventoryUseCase(DockerInventoryProvider provider) {
        this.provider = provider;
    }

    public DockerInventory collect() {
        return provider.collect();
    }
}
