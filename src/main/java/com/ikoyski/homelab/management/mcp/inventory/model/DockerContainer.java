package com.ikoyski.homelab.management.mcp.inventory.model;

import java.time.Instant;
import java.util.List;

/** Application-owned, explicitly allowlisted Docker container inventory. */
public record DockerContainer(
        String hostKey,
        String id,
        String name,
        String image,
        String state,
        String status,
        String health,
        int restartCount,
        Instant createdAt,
        Instant startedAt,
        Long uptimeSeconds,
        List<String> networks,
        List<PublishedPort> publishedPorts) {
    public DockerContainer {
        if (restartCount < 0) {
            throw new IllegalArgumentException("restartCount must not be negative");
        }
        if (uptimeSeconds != null && uptimeSeconds < 0) {
            throw new IllegalArgumentException("uptimeSeconds must not be negative");
        }
        networks = networks == null ? List.of() : List.copyOf(networks);
        publishedPorts = publishedPorts == null ? List.of() : List.copyOf(publishedPorts);
    }
}
