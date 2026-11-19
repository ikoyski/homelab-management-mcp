package com.ikoyski.homelab.management.mcp.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;

import tools.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class McpProtocolRouterServletTest {
    private HttpServletMcpTransport modern;
    private HttpServletStatelessServerTransport legacy;
    private McpProtocolRouterServlet router;

    @BeforeEach
    void setUp() {
        modern = mock(HttpServletMcpTransport.class);
        legacy = mock(HttpServletStatelessServerTransport.class);
        router = new McpProtocolRouterServlet(modern, legacy, new ObjectMapper(), 256);
    }

    @Test
    void routesLegacyInitializeByNegotiatedProtocolVersion() throws Exception {
        var request = request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-11-25\"}}", null);
        var response = new MockHttpServletResponse();

        router.service((ServletRequest) request, (ServletResponse) response);

        verify(legacy).service(any(ServletRequest.class), same((ServletResponse) response));
        verifyNoInteractions(modern);
    }

    @Test
    void routesLegacySubsequentRequestsByHeader() throws Exception {
        var request = request("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", "2025-11-25");
        var response = new MockHttpServletResponse();

        router.service((ServletRequest) request, (ServletResponse) response);

        verify(legacy).service(any(ServletRequest.class), same((ServletResponse) response));
        verifyNoInteractions(modern);
    }

    @Test
    void routesModernRequestsByHeader() throws Exception {
        var request = request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", "2026-07-28");
        var response = new MockHttpServletResponse();

        router.service((ServletRequest) request, (ServletResponse) response);

        verify(modern).service(any(ServletRequest.class), same((ServletResponse) response));
        verifyNoInteractions(legacy);
    }

    @Test
    void rejectsMissingProtocolVersion() throws Exception {
        var request = request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", null);
        var response = new MockHttpServletResponse();

        router.service((ServletRequest) request, (ServletResponse) response);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(modern, legacy);
    }

    @Test
    void rejectsRequestBodiesLargerThanConfiguredLimitBeforeRouting() throws Exception {
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.setContentType("application/json");
        request.setContent(new byte[257]);
        var response = new MockHttpServletResponse();

        router.service((ServletRequest) request, (ServletResponse) response);

        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(modern, legacy);
    }

    private static MockHttpServletRequest request(String body, String version) {
        var request = new MockHttpServletRequest("POST", "/mcp");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (version != null) request.addHeader("MCP-Protocol-Version", version);
        return request;
    }
}
