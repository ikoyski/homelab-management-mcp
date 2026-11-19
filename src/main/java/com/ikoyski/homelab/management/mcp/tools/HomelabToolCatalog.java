package com.ikoyski.homelab.management.mcp.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared, protocol-neutral registry for all configured homelab inventory tools.
 *
 * <p>Both MCP protocol adapters use this catalog so their advertised tool surfaces
 * and execution dispatch remain consistent.</p>
 */
public class HomelabToolCatalog {

    private final Map<String, HomelabToolProvider> providersByTool;
    private final List<String> toolNames;

    public HomelabToolCatalog(HomelabToolProvider... providers) {
        Map<String, HomelabToolProvider> registrations = new LinkedHashMap<>();
        for (HomelabToolProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            for (String name : provider.toolNames()) {
                HomelabToolProvider previous = registrations.putIfAbsent(name, provider);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate homelab tool registration: " + name);
                }
            }
        }
        this.providersByTool = Collections.unmodifiableMap(registrations);
        this.toolNames = List.copyOf(new ArrayList<>(registrations.keySet()));
    }

    public List<String> toolNames() {
        return toolNames;
    }

    public boolean contains(String name) {
        return providersByTool.containsKey(name);
    }

    public String toolDescription(String name) {
        HomelabToolProvider provider = providersByTool.get(name);
        return provider == null ? null : provider.toolDescription(name);
    }

    public String execute(String name) {
        HomelabToolProvider provider = providersByTool.get(name);
        return provider == null ? "Error: Unknown tool." : provider.execute(name);
    }
}
