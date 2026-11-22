package com.ikoyski.homelab.management.mcp.config;

import java.util.List;
import java.util.Map;

import com.ikoyski.homelab.management.mcp.guidance.AiClientGuidanceDocument;
import com.ikoyski.homelab.management.mcp.guidance.GuidanceResourceRepository;
import com.ikoyski.homelab.management.mcp.tools.HomelabToolCatalog;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stateless compatibility transport for MCP 2025-11-25. */
@Configuration
public class LegacyMcpConfig {

    @Bean
    public HttpServletStatelessServerTransport homelabLegacyMcpTransport() {
        return HttpServletStatelessServerTransport.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .messageEndpoint("/mcp")
                .contextExtractor(request -> McpTransportContext.create(Map.of()))
                // Legacy SDK transport defaults to NOOP validation. Match the modern
                // transport: allow non-browser clients without Origin, reject Origin
                // unless an explicit trusted-origin allowlist is configured.
                .httpHeaderValidator(DefaultServerTransportSecurityValidator.builder().build())
                .build();
    }

    @Bean(destroyMethod = "closeGracefully")
    public McpStatelessSyncServer homelabLegacyMcpServer(
            HttpServletStatelessServerTransport transport,
            HomelabToolCatalog catalog,
            AiClientGuidanceDocument guidance) {
        var builder = McpServer.sync(transport)
                .serverInfo("homelab-management-mcp", "0.1.0")
                .instructions(HomelabMcpServerInstructions.TEXT)
                .capabilities(ServerCapabilities.builder().tools(false).resources(false, false).build());

        builder.resources(new McpStatelessServerFeatures.SyncResourceSpecification(
                Resource.builder(GuidanceResourceRepository.RESOURCE_URI, GuidanceResourceRepository.RESOURCE_NAME)
                        .description("Canonical operating guidance for AI clients using the Homelab Management MCP.")
                        .mimeType("text/markdown")
                        .build(),
                (transportContext, request) -> ReadResourceResult.builder(List.of(
                        TextResourceContents.builder(GuidanceResourceRepository.RESOURCE_URI, guidance.content())
                                .mimeType("text/markdown")
                                .build()))
                        .build()));

        for (String name : catalog.toolNames()) {
            builder.toolCall(tool(name, catalog.toolDescription(name), catalog.toolInputSchema(name)),
                    (transportContext, request) -> invoke(catalog, request));
        }
        return builder.build();
    }

    private static CallToolResult invoke(HomelabToolCatalog catalog, CallToolRequest request) {
        String result = catalog.execute(request.name(), request.arguments());
        return CallToolResult.builder()
                .content(List.of(TextContent.builder(result).build()))
                .isError(result.startsWith("Error:"))
                .build();
    }

    private static Tool tool(String name, String description, Map<String, Object> inputSchema) {
        return Tool.builder(name, inputSchema).description(description).build();
    }
}
