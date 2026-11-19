package com.ikoyski.homelab.management.mcp.tools;

import com.ikoyski.homelab.management.mcp.inventory.application.DockerInventoryUseCase;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** MCP inbound tool registration for application-owned Docker inventory results. */
@Component
@ConditionalOnProperty(prefix = "homelab.docker.portainer", name = "base-url")
public class DockerInventoryToolRepository implements McpSyncToolRepository {
    private static final Logger log = LoggerFactory.getLogger(DockerInventoryToolRepository.class);
    private static final String HOSTS = "list_docker_hosts";
    private static final String CONTAINERS = "list_docker_containers";
    private static final String NETWORKS = "list_docker_networks";
    private static final String INVENTORY = "get_docker_inventory";

    private final DockerInventoryUseCase inventory;
    private final ObjectMapper mapper;
    private final Map<String, Tool> tools;

    public DockerInventoryToolRepository(DockerInventoryUseCase inventory, ObjectMapper mapper) {
        this.inventory = inventory;
        this.mapper = mapper;
        Map<String, Tool> definitions = new LinkedHashMap<>();
        definitions.put(HOSTS, tool(HOSTS, "List the five configured Docker hosts and their availability."));
        definitions.put(CONTAINERS, tool(CONTAINERS, "List allowlisted Docker container inventory fields across configured hosts."));
        definitions.put(NETWORKS, tool(NETWORKS, "List allowlisted Docker network inventory fields across configured hosts."));
        definitions.put(INVENTORY, tool(INVENTORY, "Get combined Docker host, container, and network inventory with partial-result information."));
        this.tools = Map.copyOf(definitions);
    }

    @Override
    public ToolsPage list(McpRequestContext context, String cursor) {
        return ToolsPage.of(new ArrayList<>(tools.values()));
    }

    @Override
    public Tool find(McpRequestContext context, String name) {
        return tools.get(name);
    }

    @Override
    public McpSyncResponse<CallToolOutcome> call(McpRequestContext context, CallToolRequest request) {
        String result = execute(request.name());
        return McpSyncResponse.result(CallToolResult.builder()
                .addContent(TextContent.builder(result).build())
                .isError(result.startsWith("Error:"))
                .build());
    }

    private String execute(String name) {
        try {
            DockerInventory collected = inventory.collect();
            Object result = switch (name) {
                case HOSTS -> section(collected, collected.hosts());
                case CONTAINERS -> section(collected, collected.containers());
                case NETWORKS -> section(collected, collected.networks());
                case INVENTORY -> collected;
                default -> null;
            };
            if (result == null) {
                return "Error: Unknown tool.";
            }
            log.info("mcp_tool_call_completed tool={}", name);
            return mapper.writeValueAsString(result);
        } catch (RuntimeException ex) {
            log.error("Unable to execute or serialize Docker inventory MCP tool tool={}", name);
            return "Error: Unable to retrieve or serialize Docker inventory.";
        }
    }

    private static Map<String, Object> section(DockerInventory inventory, Object items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", inventory.status());
        result.put("collectedAt", inventory.collectedAt());
        result.put("items", items);
        result.put("errors", inventory.errors());
        return result;
    }

    private static Tool tool(String name, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("additionalProperties", false);
        return Tool.builder(name, schema).description(description).build();
    }
}
