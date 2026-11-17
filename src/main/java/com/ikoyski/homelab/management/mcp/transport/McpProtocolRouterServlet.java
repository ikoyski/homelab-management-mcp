package com.ikoyski.homelab.management.mcp.transport;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

/** Single public MCP servlet; request bodies are replayed to the selected SDK transport. */
public class McpProtocolRouterServlet extends HttpServlet {
    public static final String ENDPOINT = "/mcp";
    public static final String LEGACY_VERSION = "2025-11-25";
    public static final String MODERN_VERSION = "2026-07-28";

    private final HttpServletMcpTransport modern;
    private final HttpServletStatelessServerTransport legacy;
    private final ObjectMapper mapper;

    public McpProtocolRouterServlet(HttpServletMcpTransport modern,
            HttpServletStatelessServerTransport legacy, ObjectMapper mapper) {
        this.modern = modern;
        this.legacy = legacy;
        this.mapper = mapper;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String headerVersion = request.getHeader("MCP-Protocol-Version");
        String method = null;
        String initializeVersion = null;

        if (body.length > 0 && isJson(request)) {
            try {
                Map<String, Object> json = mapper.readValue(body, new TypeReference<>() {});
                method = json.get("method") instanceof String value ? value : null;
                Object params = json.get("params");
                if (params instanceof Map<?, ?> values
                        && values.get("protocolVersion") instanceof String value) {
                    initializeVersion = value;
                }
            } catch (RuntimeException ex) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON-RPC request");
                return;
            }
        }

        boolean initialize = "initialize".equals(method);
        boolean legacyRequest = LEGACY_VERSION.equals(headerVersion)
                || (initialize && LEGACY_VERSION.equals(initializeVersion));
        boolean modernRequest = MODERN_VERSION.equals(headerVersion) && !initialize;

        if (!legacyRequest && !modernRequest) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32600,"
                    + "\"message\":\"Unsupported or missing MCP protocol version\"},\"id\":null}");
            return;
        }

        HttpServletRequest replayable = new ReplayableRequest(request, body);
        if (legacyRequest) {
            legacy.service(replayable, response);
        } else {
            modern.service(replayable, response);
        }
    }

    private static boolean isJson(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    private static final class ReplayableRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private ReplayableRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    if (listener == null) return;
                    try {
                        listener.onDataAvailable();
                        if (input.available() == 0) listener.onAllDataRead();
                    } catch (IOException ex) {
                        listener.onError(ex);
                    }
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
    }
}
