package com.ikoyski.homelab.management.mcp.inventory.application;

import com.ikoyski.homelab.management.mcp.inventory.model.DockerContainerLogs;

/** Application port for bounded, read-only Docker container log retrieval. */
public interface DockerContainerLogsProvider {
    DockerContainerLogs readLogs(String hostKey, String containerName, int tail);
}
