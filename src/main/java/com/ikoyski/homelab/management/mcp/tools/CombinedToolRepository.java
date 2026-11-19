package com.ikoyski.homelab.management.mcp.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;

/** Combines independently-owned tool repositories into one MCP tool surface. */
public class CombinedToolRepository implements McpSyncToolRepository {
    private final List<McpSyncToolRepository> repositories;

    public CombinedToolRepository(McpSyncToolRepository... repositories) {
        this.repositories = Arrays.stream(repositories).filter(Objects::nonNull).toList();
    }

    public List<String> toolNames() {
        List<String> names = new ArrayList<>();
        for (McpSyncToolRepository repository : repositories) {
            if (repository instanceof HomelabDiscoveryToolRepository proxmox) {
                names.addAll(proxmox.toolNames());
            } else if (repository instanceof DockerInventoryToolRepository docker) {
                names.addAll(docker.toolNames());
            }
        }
        return List.copyOf(names);
    }

    @Override
    public ToolsPage list(McpRequestContext context, String cursor) {
        List<Tool> tools = toolNames().stream().map(name -> find(context, name)).toList();
        return ToolsPage.of(tools);
    }

    @Override
    public Tool find(McpRequestContext context, String name) {
        for (McpSyncToolRepository repository : repositories) {
            Tool tool = repository.find(context, name);
            if (tool != null) {
                return tool;
            }
        }
        return null;
    }

    @Override
    public McpSyncResponse<CallToolOutcome> call(McpRequestContext context, CallToolRequest request) {
        for (McpSyncToolRepository repository : repositories) {
            if (repository.find(context, request.name()) != null) {
                return repository.call(context, request);
            }
        }
        throw new IllegalArgumentException("Unknown MCP tool: " + request.name());
    }
}
