package com.ikoyski.homelab.management.mcp.tools;

import java.util.List;
import java.util.Map;

/** Protocol-neutral contract for a group of read-only homelab inventory tools. */
public interface HomelabToolProvider {

    List<String> toolNames();

    String toolDescription(String name);

    default Map<String, Object> toolInputSchema(String name) {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of(),
                "additionalProperties", false);
    }

    String execute(String name);

    /**
     * Executes a tool with validated protocol arguments. Providers that do not
     * declare arguments retain the original no-argument behavior.
     */
    default String execute(String name, Map<String, Object> arguments) {
        if (arguments != null && !arguments.isEmpty()) {
            return "Error: This tool does not accept arguments.";
        }
        return execute(name);
    }
}
