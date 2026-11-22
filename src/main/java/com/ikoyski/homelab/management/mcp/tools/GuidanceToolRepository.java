package com.ikoyski.homelab.management.mcp.tools;

import com.ikoyski.homelab.management.mcp.guidance.AiClientGuidanceDocument;
import java.util.List;
import org.springframework.stereotype.Component;

/** Tool-based fallback for MCP clients that cannot expose or read resources. */
@Component
public final class GuidanceToolRepository implements HomelabToolProvider {

    public static final String TOOL_NAME = "get_ai_client_guidance";

    private final AiClientGuidanceDocument document;

    public GuidanceToolRepository(AiClientGuidanceDocument document) {
        this.document = document;
    }

    @Override
    public List<String> toolNames() {
        return List.of(TOOL_NAME);
    }

    @Override
    public String toolDescription(String name) {
        return TOOL_NAME.equals(name)
                ? "Return the complete canonical AI client operating guide for safe Homelab MCP use. Use this when the guidance resource cannot be read."
                : null;
    }

    @Override
    public String execute(String name) {
        return TOOL_NAME.equals(name) ? document.content() : "Error: Unknown tool.";
    }
}
