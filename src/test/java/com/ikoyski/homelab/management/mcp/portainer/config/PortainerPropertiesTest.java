package com.ikoyski.homelab.management.mcp.portainer.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortainerPropertiesTest {

    private static final List<String> HOST_KEYS = List.of("docker-host-a", "docker-host-b");

    @Test
    void acceptsHttpsAndAnAllowlistSuppliedByPrivateConfiguration() {
        assertDoesNotThrow(() -> properties(
                URI.create("https://portainer.test/"), "test-key", HOST_KEYS));
    }

    @Test
    void acceptsAnyConfiguredHostKeySetWithoutEmbeddingEnvironmentNamesInCode() {
        assertDoesNotThrow(() -> properties(
                URI.create("https://portainer.test/"), "test-key",
                List.of("primary-engine", "secondary-engine", "media-engine")));
    }

    @Test
    void rejectsNonHttpsBaseUrl() {
        assertThrows(IllegalStateException.class, () -> properties(
                URI.create("http://portainer.test/"), "test-key", HOST_KEYS));
    }

    @Test
    void rejectsMissingApiKey() {
        assertThrows(IllegalStateException.class, () -> properties(
                URI.create("https://portainer.test/"), " ", HOST_KEYS));
    }

    @Test
    void rejectsDuplicateAllowlistEntries() {
        assertThrows(IllegalStateException.class, () -> properties(
                URI.create("https://portainer.test/"), "test-key",
                List.of("docker-host-a", "docker-host-a")));
    }

    @Test
    void rejectsAnEmptyAllowlist() {
        assertThrows(IllegalStateException.class, () -> properties(
                URI.create("https://portainer.test/"), "test-key", List.of()));
    }

    @Test
    void rejectsInvalidHostKeys() {
        assertThrows(IllegalStateException.class, () -> properties(
                URI.create("https://portainer.test/"), "test-key", List.of("Docker Host A")));
        assertThrows(IllegalStateException.class, () -> properties(
                URI.create("https://portainer.test/"), "test-key", List.of("../other-host")));
    }

    @Test
    void rejectsInvalidTimeoutAndResponseLimits() {
        assertThrows(IllegalStateException.class, () -> new PortainerProperties(
                URI.create("https://portainer.test/"), "test-key",
                0, 8000, 1024, new PortainerProperties.Environments(HOST_KEYS)));
    }

    private static PortainerProperties properties(URI url, String key, List<String> hosts) {
        return new PortainerProperties(url, key, 3000, 8000, 1024,
                new PortainerProperties.Environments(hosts));
    }
}
