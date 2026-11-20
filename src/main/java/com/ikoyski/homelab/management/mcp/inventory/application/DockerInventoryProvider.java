package com.ikoyski.homelab.management.mcp.inventory.application;

import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;

/** Application port implemented by an infrastructure adapter. */
public interface DockerInventoryProvider {
    DockerInventory collect();

    /** Collects all hosts when hostKey is null, or one configured host otherwise. */
    DockerInventory collect(String hostKey);
}
