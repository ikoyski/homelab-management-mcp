package com.ikoyski.homelab.management.mcp.inventory.model;

import java.time.Instant;
import java.util.List;

/** Combined Docker inventory. A result is not an atomic cross-host snapshot. */
public record DockerInventory(
        String status,
        Instant collectedAt,
        List<DockerHost> hosts,
        List<DockerContainer> containers,
        List<DockerNetwork> networks,
        List<String> errors) {
    public DockerInventory {
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
        containers = containers == null ? List.of() : List.copyOf(containers);
        networks = networks == null ? List.of() : List.copyOf(networks);
        errors = errors == null ? List.of() : List.copyOf(errors);
        if (!List.of("ok", "partial", "unavailable").contains(status)) {
            throw new IllegalArgumentException("status must be ok, partial, or unavailable");
        }
    }
}
