package com.ikoyski.homelab.management.mcp.config;

import com.ikoyski.homelab.management.mcp.guidance.GuidanceResourceRepository;
import com.ikoyski.homelab.management.mcp.tools.CombinedToolRepository;
import com.ikoyski.homelab.management.mcp.tools.GuidanceToolRepository;
import com.ikoyski.homelab.management.mcp.tools.DockerInventoryToolRepository;
import com.ikoyski.homelab.management.mcp.tools.HomelabDiscoveryToolRepository;
import com.ikoyski.homelab.management.mcp.tools.HomelabToolCatalog;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.feature.ResourcesFeature;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public HomelabToolCatalog homelabToolCatalog(
            HomelabDiscoveryToolRepository proxmoxTools,
            GuidanceToolRepository guidanceTools,
            ObjectProvider<DockerInventoryToolRepository> dockerTools) {
        return new HomelabToolCatalog(proxmoxTools, guidanceTools, dockerTools.getIfAvailable());
    }

    @Bean
    public CombinedToolRepository homelabToolRepository(HomelabToolCatalog catalog) {
        return new CombinedToolRepository(catalog);
    }

    @Bean
    public McpServer homelabMcpServer(
            CombinedToolRepository tools,
            GuidanceResourceRepository guidanceResources) {
        return McpServer.builder()
                .serverInfo(Implementation.builder("homelab-management-mcp", "0.1.0").build())
                .instructions(HomelabMcpServerInstructions.TEXT)
                .feature(ToolsFeature.ofSync(tools))
                .feature(ResourcesFeature.ofSync(guidanceResources))
                .build();
    }

    /** Internal transport. The protocol router is the only servlet registered at /mcp. */
    @Bean(destroyMethod = "closeGracefully")
    public HttpServletMcpTransport homelabModernMcpTransport(McpServer server) {
        return HttpServletMcpTransport.builder(server).endpoint("/mcp").build();
    }
}
