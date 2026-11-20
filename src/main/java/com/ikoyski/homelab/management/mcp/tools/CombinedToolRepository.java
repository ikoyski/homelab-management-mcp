package com.ikoyski.homelab.management.mcp.tools;

import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;

/** Modern MCP SDK adapter over the shared homelab tool catalog. */
public class CombinedToolRepository implements McpSyncToolRepository {

    private final HomelabToolCatalog catalog;

    public CombinedToolRepository(HomelabToolCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public ToolsPage list(McpRequestContext context, String cursor) {
        return ToolsPage.of(catalog.toolNames().stream()
                .map(name -> find(context, name))
                .toList());
    }

    @Override
    public Tool find(McpRequestContext context, String name) {
        if (!catalog.contains(name)) {
            return null;
        }
        return Tool.builder(name, catalog.toolInputSchema(name))
                .description(catalog.toolDescription(name))
                .build();
    }

    @Override
    public McpSyncResponse<CallToolOutcome> call(McpRequestContext context, CallToolRequest request) {
        if (!catalog.contains(request.name())) {
            throw new IllegalArgumentException("Unknown MCP tool: " + request.name());
        }
        String result = catalog.execute(request.name(), request.arguments());
        return McpSyncResponse.result(CallToolResult.builder()
                .addContent(TextContent.builder(result).build())
                .isError(result.startsWith("Error:"))
                .build());
    }
}
