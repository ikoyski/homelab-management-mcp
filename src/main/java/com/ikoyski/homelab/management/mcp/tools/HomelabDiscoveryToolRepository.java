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
    private static final Logger log=LoggerFactory.getLogger(HomelabDiscoveryToolRepository.class);
    private static final String SCOPE="get_discovery_scope";
    private final Environment environment;
    private final ProxmoxInventoryService proxmox;
    private final ObjectMapper mapper;
    private final Map<String,Tool> tools;

    public HomelabDiscoveryToolRepository(Environment environment,ProxmoxInventoryService proxmox,ObjectMapper mapper) {
        this.environment=environment; this.proxmox=proxmox; this.mapper=mapper;
        Map<String,Tool> definitions=new LinkedHashMap<>();
        definitions.put(SCOPE,tool(SCOPE,"List configured discovery labels and the implemented release scope."));
        definitions.put("list_proxmox_nodes",tool("list_proxmox_nodes","List Proxmox cluster nodes and safe status/resource fields."));
        definitions.put("list_proxmox_guests",tool("list_proxmox_guests","List all discovered QEMU virtual machines and LXC containers; guest names and IDs are discovered dynamically."));
        definitions.put("list_proxmox_storage",tool("list_proxmox_storage","List Proxmox storage inventory and safe capacity/status fields."));
        definitions.put("get_proxmox_inventory",tool("get_proxmox_inventory","Get combined Proxmox node, QEMU VM, LXC container, and storage inventory. Partial results are returned when a section is unavailable."));
        this.tools=Map.copyOf(definitions);
    }

    @Override public ToolsPage list(McpRequestContext context,String cursor) { return ToolsPage.of(new ArrayList<>(tools.values())); }
    @Override public Tool find(McpRequestContext context,String name) { return tools.get(name); }

    @Override
    public McpSyncResponse<CallToolOutcome> call(McpRequestContext context,CallToolRequest request) {
        String name=request.name();
        try {
            Object result=switch(name) {
                case SCOPE -> discoveryScope();
                case "list_proxmox_nodes" -> proxmox.nodes();
                case "list_proxmox_guests" -> proxmox.guests();
                case "list_proxmox_storage" -> proxmox.storage();
                case "get_proxmox_inventory" -> proxmox.inventory();
                default -> null;
            };
            if(result==null) return error("Unknown tool.");
            log.info("mcp_tool_call_completed tool={}",name);
            return McpSyncResponse.result(CallToolResult.builder().addContent(TextContent.builder(mapper.writeValueAsString(result)).build()).build());
        } catch(RuntimeException ex) {
            log.error("Unable to serialize MCP tool result tool={}",name);
            return error("Unable to serialize inventory result.");
        }
    }

    private Map<String,Object> discoveryScope() {
        List<String> targets=Binder.get(environment).bind("homelab.discovery.targets",Bindable.listOf(String.class)).orElseGet(List::of);
        List<String> safe=targets.stream().filter(t->t!=null&&t.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,62}")).toList();
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("mode","read-only"); result.put("discoveryImplemented",true);
        result.put("configuredTargetCount",safe.size()); result.put("targets",safe);
        result.put("message","Proxmox inventory is implemented; Docker discovery is outside Release 1.");
        return result;
    }

    private static Tool tool(String name,String description) {
        Map<String,Object> schema=new LinkedHashMap<>();
        schema.put("type","object"); schema.put("properties",Map.of()); schema.put("additionalProperties",false);
        return Tool.builder(name,schema).description(description).build();
    }
    private static McpSyncResponse<CallToolOutcome> error(String message) {
        return McpSyncResponse.result(CallToolResult.builder().isError(true).addContent(TextContent.builder("Error: "+message).build()).build());
    }
}