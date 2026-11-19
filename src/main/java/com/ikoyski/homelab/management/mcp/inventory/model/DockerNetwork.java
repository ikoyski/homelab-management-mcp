package com.ikoyski.homelab.management.mcp.inventory.model;

import java.util.List;

/** Application-owned Docker network summary; excludes IPAM and raw endpoint configuration. */
public record DockerNetwork(
        String hostKey,
        String id,
        String name,
        String driver,
        String scope,
        Boolean internal,
        Boolean attachable,
        List<String> connectedContainers) {
    public DockerNetwork {
        connectedContainers = connectedContainers == null ? List.of() : List.copyOf(connectedContainers);
    }
}
