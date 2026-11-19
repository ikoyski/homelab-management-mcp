package com.ikoyski.homelab.management.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;
import com.ikoyski.homelab.management.mcp.proxmox.ProxmoxInventoryService;
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
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Read-only MCP tools for discovery scope and Proxmox inventory. */
@Component
public class HomelabDiscoveryToolRepository implements McpSyncToolRepository {
    private static final Logger log = LoggerFactory.getLogger(HomelabDiscoveryToolRepository.class);
    private static final String SCOPE = "get_discovery_scope";
    private final Environment environment;
    private final ProxmoxInventoryService proxmox;
    private final ObjectMapper mapper;
    private final Map<String, Tool> tools;

    public HomelabDiscoveryToolRepository(Environment environment, ProxmoxInventoryService proxmox,
            ObjectMapper mapper) {
        this.environment = environment;
        this.proxmox = proxmox;
        this.mapper = mapper;
        Map<String, Tool> definitions = new LinkedHashMap<>();
        definitions.put(SCOPE, tool(SCOPE, "List configured discovery labels and the implemented release scope."));
        definitions.put("list_proxmox_nodes", tool("list_proxmox_nodes",
                "List Proxmox cluster nodes and safe status/resource fields. CPU is a 0-1 fraction; cpuUsagePercent is the derived percentage. mem, maxmem, disk, and maxdisk are byte counts; byteUnit identifies the unit. memoryUsagePercent is derived from mem/maxmem. Missing metrics are omitted, not reported as zero."));
        definitions.put("list_proxmox_guests", tool("list_proxmox_guests",
                "List discovered QEMU virtual machines and LXC containers. CPU is a 0-1 fraction; cpuUsagePercent is the derived percentage. mem, maxmem, disk, and maxdisk are byte counts; byteUnit identifies the unit. memoryUsagePercent is derived from mem/maxmem. QEMU disk is Proxmox root-image usage and is not guest filesystem usage; QEMU filesystem metrics are reported separately when the guest agent is available. Missing metrics are omitted, not reported as zero."));
        definitions.put("list_proxmox_storage", tool("list_proxmox_storage",
                "List Proxmox storage inventory and safe capacity/status fields. totalBytes, usedBytes, and availableBytes are byte counts (byteUnit=bytes); usagePercent is calculated from Proxmox-reported usedBytes/totalBytes. Do not infer or relabel units."));
        definitions.put("get_proxmox_inventory", tool("get_proxmox_inventory",
                "Get combined Proxmox node, QEMU VM, LXC container, and storage inventory. CPU fields are fractions from 0 to 1 and cpuUsagePercent is the derived percentage. Memory and disk byte fields use byteUnit=bytes; memoryUsagePercent and storage/filesystem usagePercent are deterministic derived percentages. QEMU disk is not guest filesystem usage. Missing metrics are omitted, not reported as zero. Partial results are returned when a section is unavailable."));
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

    public List<String> toolNames() {
        return List.copyOf(tools.keySet());
    }

    public String toolDescription(String name) {
        Tool definition = tools.get(name);
        return definition == null ? "Read-only homelab inventory operation." : definition.description();
    }

    public String execute(String name) {
        try {
            Object result = switch (name) {
                case SCOPE -> discoveryScope();
                case "list_proxmox_nodes" -> proxmox.nodes();
                case "list_proxmox_guests" -> proxmox.guests();
                case "list_proxmox_storage" -> proxmox.storage();
                case "get_proxmox_inventory" -> proxmox.inventory();
                default -> null;
            };
            if (result == null) {
                return "Error: Unknown tool.";
            }
            log.info("mcp_tool_call_completed tool={}", name);
            return mapper.writeValueAsString(result);
        } catch (RuntimeException ex) {
            log.error("Unable to execute or serialize MCP tool result tool={}", name);
            return "Error: Unable to retrieve or serialize inventory result.";
        }
    }

    @Override
    public McpSyncResponse<CallToolOutcome> call(McpRequestContext context, CallToolRequest request) {
        String result = execute(request.name());
        return McpSyncResponse.result(CallToolResult.builder()
                .addContent(TextContent.builder(result).build())
                .isError(result.startsWith("Error:"))
                .build());
    }

    private Map<String, Object> discoveryScope() {
        List<String> targets = Binder.get(environment)
                .bind("homelab.discovery.targets", Bindable.listOf(String.class))
                .orElseGet(List::of);
        List<String> safe = targets.stream()
                .filter(t -> t != null && t.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,62}"))
                .toList();
        boolean dockerConfigured = environment.getProperty("homelab.docker.portainer.base-url") != null
                && !environment.getProperty("homelab.docker.portainer.base-url").isBlank();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "read-only");
        result.put("discoveryImplemented", true);
        result.put("configuredTargetCount", safe.size());
        result.put("targets", safe);
        result.put("dockerInventoryConfigured", dockerConfigured);
        result.put("message", dockerConfigured
                ? "Proxmox and Docker inventory tools are implemented; Docker data is retrieved through the configured Portainer integration."
                : "Proxmox inventory tools are implemented; Docker inventory tools are available when Portainer is configured.");
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
