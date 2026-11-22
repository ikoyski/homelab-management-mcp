package com.ikoyski.homelab.management.mcp.inventory.model;

import java.time.Instant;

/**
 * Point-in-time Docker container resource snapshot. CPU is a percentage of the
 * container's available CPU capacity; memory usage and working-set values are bytes.
 */
public record DockerContainerStats(
        String hostKey,
        String containerName,
        String containerId,
        String status,
        Instant sampledAt,
        Double cpuPercent,
        Long memoryUsageBytes,
        Long memoryWorkingSetBytes,
        Long memoryLimitBytes,
        Double memoryUsagePercent,
        Long memoryCacheBytes,
        Long networkRxBytes,
        Long networkTxBytes,
        Long blockReadBytes,
        Long blockWriteBytes,
        String errorCode) {
}
