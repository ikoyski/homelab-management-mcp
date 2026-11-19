package com.ikoyski.homelab.management.mcp.inventory.model;

import java.time.Instant;

/** Application-owned Docker host inventory; contains no Portainer endpoint ID. */
public record DockerHost(
        String hostKey,
        String status,
        String dockerEngineVersion,
        String operatingSystem,
        Instant collectedAt,
        String errorCode) {
}
