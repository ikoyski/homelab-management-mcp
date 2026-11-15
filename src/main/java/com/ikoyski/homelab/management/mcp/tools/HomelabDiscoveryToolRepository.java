package com.ikoyski.homelab.management.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * First-stage read-only catalog. It exposes configured target labels only;
 * connection and discovery adapters are added in subsequent implementation steps.
 */
@Component
public class HomelabDiscoveryToolRepository implements McpSyncToolRepository {

    private static final Logger log = LoggerFactory.getLogger(HomelabDiscoveryToolRepository.class);
    private static final String TOOL_NAME = "get_discovery_scope";

    private final Environment environment;
    private final Tool tool;

    public HomelabDiscoveryToolRepository(Environment environment) {
        this.environment = environment;
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("additionalProperties", false);

        this.tool = Tool.builder(TOOL_NAME, schema)
                .description("List the homelab target labels configured for read-only discovery. This reports configuration scope only; it does not connect to or inspect the targets.")
                .build();
    }

    @Override
    public ToolsPage list(McpRequestContext context, String cursor) {
        return ToolsPage.of(List.of(tool));
    }

    @Override
    public Tool find(McpRequestContext context, String name) {
        return TOOL_NAME.equals(name) ? tool : null;
    }

    @Override
    public McpSyncResponse<CallToolOutcome> call(McpRequestContext context, CallToolRequest request) {
        if (!TOOL_NAME.equals(request.name())) {
            return error("Unknown tool.");
        }

        String[] targets = environment.getProperty(
                "homelab.discovery.targets", String[].class, new String[0]);
        List<String> safeTargets = new ArrayList<>();
        for (String target : targets) {
            if (target != null && target.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,62}")) {
                safeTargets.add(target);
            }
        }

        String mode = environment.getProperty("homelab.discovery.mode", "read-only");
        if (!"read-only".equalsIgnoreCase(mode)) {
            log.error("Unsupported discovery mode configured; refusing to advertise write-capable behavior.");
            mode = "read-only";
        }

        String json = """
                {
                  "mode": "%s",
                  "discoveryImplemented": false,
                  "configuredTargetCount": %d,
                  "targets": %s,
                  "message": "This bootstrap build reports configured target labels only. Remote discovery is not yet implemented."
                }
                """.formatted(mode, safeTargets.size(), toJsonArray(safeTargets));

        log.info("mcp_tool_call_completed tool={} configuredTargetCount={}", TOOL_NAME, safeTargets.size());
        return McpSyncResponse.result(CallToolResult.builder()
                .addContent(TextContent.builder(json).build())
                .build());
    }

    private static String toJsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    private static McpSyncResponse<CallToolOutcome> error(String message) {
        return McpSyncResponse.result(CallToolResult.builder()
                .isError(true)
                .addContent(TextContent.builder("Error: " + message).build())
                .build());
    }
}
