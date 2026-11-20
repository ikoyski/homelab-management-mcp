package com.ikoyski.homelab.management.mcp.inventory.model;

/** Bounded, read-only container log result. Log contents may still contain sensitive data. */
public record DockerContainerLogs(
        String status,
        String hostKey,
        String containerName,
        int requestedTail,
        int returnedBytes,
        boolean truncated,
        int redactedValueCount,
        String logs,
        String errorCode) {
}
