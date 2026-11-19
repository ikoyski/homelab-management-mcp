package com.ikoyski.homelab.management.mcp.tools;

import java.util.List;

/** Protocol-neutral contract for a group of read-only homelab inventory tools. */
public interface HomelabToolProvider {

    List<String> toolNames();

    String toolDescription(String name);

    String execute(String name);
}
