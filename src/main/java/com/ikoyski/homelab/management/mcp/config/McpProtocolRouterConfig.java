package com.ikoyski.homelab.management.mcp.config;

import com.ikoyski.homelab.management.mcp.transport.McpProtocolRouterServlet;
import tools.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpProtocolRouterConfig {
    @Bean
    public McpProtocolRouterServlet homelabMcpProtocolRouter(
            HttpServletMcpTransport modern,
            HttpServletStatelessServerTransport legacy,
            ObjectMapper mapper) {
        return new McpProtocolRouterServlet(modern, legacy, mapper);
    }

    @Bean
    public ServletRegistrationBean<McpProtocolRouterServlet> homelabMcpServlet(
            McpProtocolRouterServlet router) {
        ServletRegistrationBean<McpProtocolRouterServlet> registration =
                new ServletRegistrationBean<>(router, McpProtocolRouterServlet.ENDPOINT);
        registration.setName("homelabMcpProtocolRouter");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }
}
