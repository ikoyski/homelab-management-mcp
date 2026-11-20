package com.ikoyski.homelab.management.mcp.inventory.application;

import com.ikoyski.homelab.management.mcp.inventory.model.DockerContainerLogs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Application use case for bounded, read-only Docker container log retrieval. */
@Service
@ConditionalOnProperty(prefix = "homelab.docker.portainer", name = "base-url")
public class DockerContainerLogsUseCase {

    private final DockerContainerLogsProvider provider;

    public DockerContainerLogsUseCase(DockerContainerLogsProvider provider) {
        this.provider = provider;
    }

    public DockerContainerLogs readLogs(String hostKey, String containerName, int tail) {
        return provider.readLogs(hostKey, containerName, tail);
    }
}
