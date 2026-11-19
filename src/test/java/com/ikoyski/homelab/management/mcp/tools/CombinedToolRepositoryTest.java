package com.ikoyski.homelab.management.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.ikoyski.homelab.management.mcp.inventory.application.DockerInventoryUseCase;
import com.ikoyski.homelab.management.mcp.proxmox.ProxmoxInventoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

class CombinedToolRepositoryTest {

    private static final List<String> PROXMOX_TOOLS = List.of(
            "get_discovery_scope",
            "list_proxmox_nodes",
            "list_proxmox_guests",
            "list_proxmox_storage",
            "get_proxmox_inventory");

    private static final List<String> DOCKER_TOOLS = List.of(
            "list_docker_hosts",
            "list_docker_containers",
            "list_docker_networks",
            "get_docker_inventory");

    @Test
    void advertisesAllProxmoxAndDockerToolsTogetherWhenPortainerIsConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("homelab.docker.portainer.base-url", "https://portainer.example/");
        ObjectMapper mapper = new ObjectMapper();
        HomelabDiscoveryToolRepository proxmox = new HomelabDiscoveryToolRepository(
                environment, mock(ProxmoxInventoryService.class), mapper);
        DockerInventoryToolRepository docker = new DockerInventoryToolRepository(
                mock(DockerInventoryUseCase.class), mapper);

        CombinedToolRepository combined = new CombinedToolRepository(proxmox, docker);

        for (String name : PROXMOX_TOOLS) {
            assertNotNull(combined.find(null, name), "Missing MCP tool: " + name);
        }
        for (String name : DOCKER_TOOLS) {
            assertNotNull(combined.find(null, name), "Missing MCP tool: " + name);
        }
    }

    @Test
    void keepsProxmoxToolsAvailableWhenDockerIntegrationIsNotConfigured() {
        HomelabDiscoveryToolRepository proxmox = new HomelabDiscoveryToolRepository(
                new MockEnvironment(), mock(ProxmoxInventoryService.class), new ObjectMapper());

        CombinedToolRepository combined = new CombinedToolRepository(proxmox, null);

        for (String name : PROXMOX_TOOLS) {
            assertNotNull(combined.find(null, name), "Missing MCP tool: " + name);
        }
        for (String name : DOCKER_TOOLS) {
            assertNull(combined.find(null, name), "Unexpected MCP tool: " + name);
        }
    }

    @Test
    void discoveryScopeReportsDockerAsConfiguredOnlyWhenPortainerBaseUrlExists() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("homelab.docker.portainer.base-url", "https://portainer.example/");
        HomelabDiscoveryToolRepository configured = new HomelabDiscoveryToolRepository(
                environment, mock(ProxmoxInventoryService.class), new ObjectMapper());
        HomelabDiscoveryToolRepository unconfigured = new HomelabDiscoveryToolRepository(
                new MockEnvironment(), mock(ProxmoxInventoryService.class), new ObjectMapper());

        assertTrue(configured.execute("get_discovery_scope").contains("\"dockerInventoryConfigured\":true"));
        assertTrue(unconfigured.execute("get_discovery_scope").contains("\"dockerInventoryConfigured\":false"));
    }
}
