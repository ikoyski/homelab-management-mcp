package com.ikoyski.homelab.management.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ikoyski.homelab.management.mcp.inventory.application.DockerInventoryUseCase;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;
import com.ikoyski.homelab.management.mcp.proxmox.ProxmoxInventoryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    void modernAdapterAdvertisesTheSharedNineToolCatalogWhenPortainerIsConfigured() {
        HomelabToolCatalog catalog = configuredCatalog();
        CombinedToolRepository modernAdapter = new CombinedToolRepository(catalog);

        assertEquals(9, catalog.toolNames().size());
        assertEquals(catalog.toolNames().size(), modernAdapter.list(null, null).tools().size());

        for (String name : PROXMOX_TOOLS) {
            assertTrue(catalog.contains(name));
            assertNotNull(modernAdapter.find(null, name), "Missing MCP tool: " + name);
            assertNotNull(catalog.toolDescription(name));
        }
        for (String name : DOCKER_TOOLS) {
            assertTrue(catalog.contains(name));
            assertNotNull(modernAdapter.find(null, name), "Missing MCP tool: " + name);
            assertNotNull(catalog.toolDescription(name));
        }
    }

    @Test
    void dockerListToolsDeclareOptionalHostKeyAndRejectAdditionalArgumentsInTheirSchemas() {
        CombinedToolRepository modernAdapter = new CombinedToolRepository(configuredCatalog());

        Map<String, Object> containerSchema = modernAdapter.find(null, "list_docker_containers").inputSchema();
        Map<String, Object> networkSchema = modernAdapter.find(null, "list_docker_networks").inputSchema();
        Map<String, Object> containerProperties = (Map<String, Object>) containerSchema.get("properties");
        Map<String, Object> hostKeySchema = (Map<String, Object>) containerProperties.get("hostKey");

        assertEquals("object", containerSchema.get("type"));
        assertEquals(false, containerSchema.get("additionalProperties"));
        assertEquals("string", hostKeySchema.get("type"));
        assertTrue(((String) hostKeySchema.get("description")).contains("configured host key"));
        assertEquals(containerSchema.get("properties"), networkSchema.get("properties"));

        Map<String, Object> inventorySchema = modernAdapter.find(null, "get_docker_inventory").inputSchema();
        assertEquals(Map.of(), inventorySchema.get("properties"));
    }

    @Test
    void toolCatalogPassesHostFilterArgumentsToTheDockerProvider() {
        DockerInventoryUseCase useCase = mock(DockerInventoryUseCase.class);
        DockerInventory empty = new DockerInventory("ok", Instant.now(),
                List.of(), List.of(), List.of(), List.of());
        when(useCase.collect("dev-pve")).thenReturn(empty);
        DockerInventoryToolRepository docker = new DockerInventoryToolRepository(useCase, new ObjectMapper());
        HomelabToolCatalog catalog = new HomelabToolCatalog(docker);

        String response = catalog.execute("list_docker_containers", Map.of("hostKey", "dev-pve"));

        assertTrue(response.contains("\"status\":\"ok\""));
        verify(useCase).collect("dev-pve");
    }

    @Test
    void catalogKeepsTheFiveProxmoxToolsWhenDockerIntegrationIsNotConfigured() {
        HomelabDiscoveryToolRepository proxmox = new HomelabDiscoveryToolRepository(
                new MockEnvironment(), mock(ProxmoxInventoryService.class), new ObjectMapper());
        HomelabToolCatalog catalog = new HomelabToolCatalog(proxmox);
        CombinedToolRepository modernAdapter = new CombinedToolRepository(catalog);

        assertEquals(5, catalog.toolNames().size());
        assertEquals(catalog.toolNames().size(), modernAdapter.list(null, null).tools().size());
        for (String name : PROXMOX_TOOLS) {
            assertNotNull(modernAdapter.find(null, name), "Missing MCP tool: " + name);
        }
        for (String name : DOCKER_TOOLS) {
            assertNull(modernAdapter.find(null, name), "Unexpected MCP tool: " + name);
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

    private static HomelabToolCatalog configuredCatalog() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("homelab.docker.portainer.base-url", "https://portainer.example/");
        HomelabDiscoveryToolRepository proxmox = new HomelabDiscoveryToolRepository(
                environment, mock(ProxmoxInventoryService.class), new ObjectMapper());
        DockerInventoryToolRepository docker = new DockerInventoryToolRepository(
                mock(DockerInventoryUseCase.class), new ObjectMapper());
        return new HomelabToolCatalog(proxmox, docker);
    }
}
