package com.ikoyski.homelab.management.mcp.guidance;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceOutcome;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.modern.McpSchema.Resource;
import io.modelcontextprotocol.modern.McpSchema.TextResourceContents;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceRepository;
import io.modelcontextprotocol.modern.server.feature.ResourcesPage;
import org.springframework.stereotype.Component;

/** Exposes the canonical AI client guide through the modern MCP resources API. */
@Component
public final class GuidanceResourceRepository implements McpSyncResourceRepository {

    public static final String RESOURCE_URI = "homelab://guidance/ai-client";
    public static final String RESOURCE_NAME = "Homelab AI Client Guidance";

    private final AiClientGuidanceDocument document;

    public GuidanceResourceRepository(AiClientGuidanceDocument document) {
        this.document = document;
    }

    @Override
    public ResourcesPage list(McpRequestContext context, String cursor) {
        if (cursor != null) {
            throw McpException.invalidParams("No additional AI guidance resource pages are available.");
        }
        return ResourcesPage.of(List.of(resourceDefinition()));
    }

    @Override
    public McpSyncResponse<ReadResourceOutcome> read(
            McpRequestContext context, ReadResourceRequest request) {
        if (!RESOURCE_URI.equals(request.uri())) {
            throw McpException.invalidParams(
                    "Unknown Homelab MCP resource.", Map.of("uri", request.uri()));
        }
        return McpSyncResponse.result(ReadResourceResult.builder(List.of(
                TextResourceContents.builder(RESOURCE_URI, document.content())
                        .mimeType("text/markdown")
                        .build()))
                .build());
    }

    private static Resource resourceDefinition() {
        return Resource.builder(RESOURCE_URI, RESOURCE_NAME)
                .description("Canonical operating guidance for AI clients using the Homelab Management MCP.")
                .mimeType("text/markdown")
                .build();
    }
}
