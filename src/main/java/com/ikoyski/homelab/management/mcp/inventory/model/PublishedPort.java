package com.ikoyski.homelab.management.mcp.inventory.model;

/** Safe published-port mapping; excludes host network addresses. */
public record PublishedPort(int containerPort, Integer hostPort, String protocol) {
    public PublishedPort {
        if (containerPort < 1 || containerPort > 65535) {
            throw new IllegalArgumentException("containerPort must be between 1 and 65535");
        }
        if (hostPort != null && (hostPort < 1 || hostPort > 65535)) {
            throw new IllegalArgumentException("hostPort must be between 1 and 65535");
        }
        if (protocol == null || !protocol.matches("tcp|udp|sctp")) {
            throw new IllegalArgumentException("protocol is invalid");
        }
    }
}
