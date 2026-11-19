package com.ikoyski.homelab.management.mcp.inventory.application;

import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;

/** Application port implemented by an infrastructure adapter. */
public interface DockerInventoryProvider {
    DockerInventory collect();
}
