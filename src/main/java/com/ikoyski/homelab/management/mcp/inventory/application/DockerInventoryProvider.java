package com.ikoyski.homelab.management.mcp.inventory.application;

import com.ikoyski.homelab.management.mcp.inventory.model.DockerContainerStats;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;
import java.util.List;

/** Application port implemented by an infrastructure adapter. */
public interface DockerInventoryProvider {
    DockerInventory collect();

    /** Collects all hosts when hostKey is null, or one configured host otherwise. */
    DockerInventory collect(String hostKey);

    /**
     * Collects a live, read-only resource snapshot. A null hostKey selects all
     * configured hosts; a null containerName selects all discovered containers.
     */
    List<DockerContainerStats> collectStats(String hostKey, String containerName);
}
