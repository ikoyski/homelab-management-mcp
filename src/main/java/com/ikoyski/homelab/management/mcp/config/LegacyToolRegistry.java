package com.ikoyski.homelab.management.mcp.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.ikoyski.homelab.management.mcp.tools.DockerInventoryToolRepository;
import com.ikoyski.homelab.management.mcp.tools.HomelabDiscoveryToolRepository;

/**
 * Protocol-neutral registry used by the legacy MCP transport.
 *
 * The legacy SDK and modern SDK expose different tool repository APIs, so this
 * class bridges the existing application-owned tool executors without coupling
 * the legacy transport to modern SDK types.
 */
public final class LegacyToolRegistry {

    public record ToolDefinition(String name, String description, Function<String, String> executor) {
        public ToolDefinition {
            Objects.requireNonNull(name);
            Objects.requireNonNull(description);
            Objects.requireNonNull(executor);
        }

        public String execute() {
            return executor.apply(name);
        }
    }

    private final List<ToolDefinition> definitions;

    private LegacyToolRegistry(List<ToolDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
    }

    public static LegacyToolRegistry create(
            HomelabDiscoveryToolRepository proxmox,
            DockerInventoryToolRepository docker) {
        Objects.requireNonNull(proxmox, "Proxmox tool repository is required");
        List<ToolDefinition> tools = new ArrayList<>();

        proxmox.toolNames().forEach(name -> tools.add(new ToolDefinition(
                name, proxmox.toolDescription(name), proxmox::execute)));

        if (docker != null) {
            docker.toolNames().forEach(name -> tools.add(new ToolDefinition(
                    name, docker.toolDescription(name), docker::execute)));
        }

        return new LegacyToolRegistry(tools);
    }

    public List<ToolDefinition> tools() {
        return definitions;
    }

    public List<String> toolNames() {
        return definitions.stream().map(ToolDefinition::name).toList();
    }

    public String execute(String name) {
        return definitions.stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst()
                .map(ToolDefinition::execute)
                .orElse("Error: Unknown tool.");
    }
}
