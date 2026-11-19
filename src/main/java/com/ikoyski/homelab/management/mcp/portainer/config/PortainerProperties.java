package com.ikoyski.homelab.management.mcp.portainer.config;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Private runtime configuration for the Portainer upstream.
 * Values are supplied by Config Server; no environment-specific defaults live in code.
 */
@ConfigurationProperties(prefix = "homelab.docker.portainer")
public record PortainerProperties(
        URI baseUrl,
        String apiKey,
        int connectTimeoutMs,
        int readTimeoutMs,
        long maxResponseBytes,
        Environments environments) {

    private static final Set<String> APPROVED_ENVIRONMENTS = Set.of(
            "util-pve", "dev-pve", "earn-pve", "media-pve", "ncloud-pve");

    public PortainerProperties {
        if (baseUrl == null || !"https".equalsIgnoreCase(baseUrl.getScheme())
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null) {
            throw new IllegalStateException("homelab.docker.portainer.base-url must be a valid HTTPS URL");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("homelab.docker.portainer.api-key must be configured");
        }
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0 || maxResponseBytes <= 0) {
            throw new IllegalStateException("Portainer timeouts and response-size limit must be positive");
        }
        if (environments == null || environments.allowlist() == null
                || environments.allowlist().isEmpty()) {
            throw new IllegalStateException("homelab.docker.portainer.environments.allowlist must not be empty");
        }
        if (environments.allowlist().stream().anyMatch(key -> key == null
                || !key.matches("[a-z][a-z0-9-]{0,62}"))
                || new HashSet<>(environments.allowlist()).size() != environments.allowlist().size()
                || !Set.copyOf(environments.allowlist()).equals(APPROVED_ENVIRONMENTS)) {
            throw new IllegalStateException("Portainer environment allowlist must contain exactly the five approved hosts");
        }
    }

    public record Environments(List<String> allowlist) {
    }
}
