package com.ikoyski.homelab.management.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ikoyski.homelab.management.mcp.guidance.AiClientGuidanceDocument;
import com.ikoyski.homelab.management.mcp.inventory.application.DockerInventoryUseCase;
import com.ikoyski.homelab.management.mcp.inventory.application.DockerContainerLogsUseCase;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerContainerLogs;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerContainerStats;
import com.ikoyski.homelab.management.mcp.proxmox.ProxmoxInventoryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

class CombinedToolRepositoryTest {

    private static final List<String> PROXMOX_TOOLS = List.of(
            "get_homelab_capabilities",
            "list_proxmox_nodes",
            "list_proxmox_guests",
            "list_proxmox_storage",
            "get_proxmox_inventory");

    private static final List<String> GUIDANCE_TOOLS = List.of("get_ai_client_guidance");

    private static final List<String> DOCKER_TOOLS = List.of(
            "list_docker_hosts",
            "list_docker_containers",
            "list_docker_networks",
            "get_docker_inventory",
            "get_docker_container_logs",
            "get_docker_container_stats");

    @Test
    void modernAdapterAdvertisesTheSharedTwelveToolCatalogWhenPortainerIsConfigured() {
        HomelabToolCatalog catalog = configuredCatalog();
        CombinedToolRepository modernAdapter = new CombinedToolRepository(catalog);

        assertEquals(12, catalog.toolNames().size());
        assertEquals(catalog.toolNames().size(), modernAdapter.list(null, null).tools().size());

        for (String name : PROXMOX_TOOLS) {
            assertTrue(catalog.contains(name));
            assertNotNull(modernAdapter.find(null, name), "Missing MCP tool: " + name);
            assertNotNull(catalog.toolDescription(name));
        }
        for (String name : GUIDANCE_TOOLS) {
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
        assertTrue(((String) hostKeySchema.get("description")).contains("configured Docker host key"));
        assertEquals(containerSchema.get("properties"), networkSchema.get("properties"));

        Map<String, Object> inventorySchema = modernAdapter.find(null, "get_docker_inventory").inputSchema();
        assertEquals(Map.of(), inventorySchema.get("properties"));

        Map<String, Object> logsSchema = modernAdapter.find(null, "get_docker_container_logs").inputSchema();
        Map<String, Object> logsProperties = (Map<String, Object>) logsSchema.get("properties");
        Map<String, Object> tailSchema = (Map<String, Object>) logsProperties.get("tail");
        assertEquals(false, logsSchema.get("additionalProperties"));
        assertEquals(List.of("hostKey", "containerName"), logsSchema.get("required"));
        assertEquals(1, tailSchema.get("minimum"));
        assertEquals(200, tailSchema.get("maximum"));
    }

    @Test
    void toolCatalogPassesHostFilterArgumentsToTheDockerProvider() {
        DockerInventoryUseCase useCase = mock(DockerInventoryUseCase.class);
        DockerInventory empty = new DockerInventory("ok", Instant.now(),
                List.of(), List.of(), List.of(), List.of());
        when(useCase.collect("dev-pve")).thenReturn(empty);
        DockerInventoryToolRepository docker = new DockerInventoryToolRepository(
                useCase, mock(DockerContainerLogsUseCase.class), new ObjectMapper());
        HomelabToolCatalog catalog = new HomelabToolCatalog(docker);

        String response = catalog.execute("list_docker_containers", Map.of("hostKey", "dev-pve"));

        assertTrue(response.contains("\"status\":\"ok\""));
        verify(useCase).collect("dev-pve");
    }

    @Test
    void dockerStatsToolDeclaresOptionalFiltersAndDispatchesSnapshotRequest() {
        DockerInventoryUseCase inventory = mock(DockerInventoryUseCase.class);
        DockerContainerStats sample = new DockerContainerStats("dev-pve", "sample", "abcdef123456",
                "ok", Instant.now(), 12.5, 1024L, 768L, 4096L, 18.75, 256L,
                100L, 200L, 300L, 400L, null);
        when(inventory.collectStats("dev-pve", "sample")).thenReturn(List.of(sample));
        DockerInventoryToolRepository docker = new DockerInventoryToolRepository(
                inventory, mock(DockerContainerLogsUseCase.class), new ObjectMapper());
        HomelabToolCatalog catalog = new HomelabToolCatalog(docker);

        Map<String, Object> schema = catalog.toolInputSchema("get_docker_container_stats");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertEquals(false, schema.get("additionalProperties"));
        assertEquals(List.of(), schema.get("required"));
        assertTrue(properties.containsKey("hostKey"));
        assertTrue(properties.containsKey("containerName"));

        String response = catalog.execute("get_docker_container_stats",
                Map.of("hostKey", "dev-pve", "containerName", "sample"));
        assertTrue(response.contains("\"cpuPercent\":12.5"));
        assertTrue(response.contains("\"memoryWorkingSetBytes\":768"));
        verify(inventory).collectStats("dev-pve", "sample");
    }

    @Test
    void catalogKeepsProxmoxAndGuidanceToolsWhenDockerIntegrationIsNotConfigured() {
        HomelabDiscoveryToolRepository proxmox = new HomelabDiscoveryToolRepository(
                new MockEnvironment(), mock(ProxmoxInventoryService.class), new ObjectMapper());
        GuidanceToolRepository guidance = guidanceTool();
        HomelabToolCatalog catalog = new HomelabToolCatalog(proxmox, guidance);
        CombinedToolRepository modernAdapter = new CombinedToolRepository(catalog);

        assertEquals(6, catalog.toolNames().size());
        assertEquals(catalog.toolNames().size(), modernAdapter.list(null, null).tools().size());
        for (String name : PROXMOX_TOOLS) {
            assertNotNull(modernAdapter.find(null, name), "Missing MCP tool: " + name);
        }
        for (String name : GUIDANCE_TOOLS) {
            assertNotNull(modernAdapter.find(null, name), "Missing MCP tool: " + name);
        }
        for (String name : DOCKER_TOOLS) {
            assertNull(modernAdapter.find(null, name), "Unexpected MCP tool: " + name);
        }
    }

    @Test
    void homelabCapabilitiesReportConfiguredIntegrationsAndDoNotClaimConnectivity() {
        MockEnvironment configuredEnvironment = new MockEnvironment()
                .withProperty("homelab.proxmox.api-url", "https://proxmox.example/")
                .withProperty("homelab.proxmox.token-id", "mcp@pve!readonly")
                .withProperty("homelab.proxmox.token-secret", "test-secret")
                .withProperty("homelab.docker.portainer.base-url", "https://portainer.example/")
                .withProperty("homelab.discovery.targets[0]", "dev-pve")
                .withProperty("homelab.discovery.targets[1]", "unsafe target");
        HomelabDiscoveryToolRepository configured = new HomelabDiscoveryToolRepository(
                configuredEnvironment, mock(ProxmoxInventoryService.class), new ObjectMapper());
        HomelabDiscoveryToolRepository unconfigured = new HomelabDiscoveryToolRepository(
                new MockEnvironment(), mock(ProxmoxInventoryService.class), new ObjectMapper());

        String configuredResult = configured.execute("get_homelab_capabilities");
        String unconfiguredResult = unconfigured.execute("get_homelab_capabilities");

        assertTrue(configuredResult.contains("\"proxmoxInventory\":{\"implemented\":true,\"enabled\":true}"));
        assertTrue(configuredResult.contains("\"dockerInventory\":{\"implemented\":true,\"enabled\":true}"));
        assertTrue(configuredResult.contains("\"configured\":true"));
        assertTrue(configuredResult.contains("\"connectivityChecked\":false"));
        assertTrue(configuredResult.contains("\"configuredTargetCount\":1"));
        assertTrue(configuredResult.contains("\"resourceUri\":\"homelab://guidance/ai-client\""));
        assertTrue(configuredResult.contains("\"retrievalTool\":\"get_ai_client_guidance\""));
        assertTrue(unconfiguredResult.contains("\"dockerInventory\":{\"implemented\":true,\"enabled\":false}"));
        assertTrue(unconfiguredResult.contains("\"proxmoxInventory\":{\"implemented\":true,\"enabled\":false}"));
        assertTrue(unconfiguredResult.contains("\"proxmox\":{\"configured\":false"));
    }

    @Test
    void guidanceFallbackToolReturnsTheCanonicalGuideContent() {
        String result = guidanceTool().execute("get_ai_client_guidance");

        assertTrue(result.contains("Inventory-first diagnostic workflow"));
        assertTrue(result.contains("Read-only boundary and operator approval"));
    }

    @Test
    void dockerLogToolDispatchesTheExactHostContainerAndBoundedTail() {
        DockerInventoryUseCase inventory = mock(DockerInventoryUseCase.class);
        DockerContainerLogsUseCase logs = mock(DockerContainerLogsUseCase.class);
        when(logs.readLogs("docker-host-a", "sample", 25)).thenReturn(
                new DockerContainerLogs("ok", "docker-host-a", "sample", 25, 12,
                        false, 0, "sample log\n", null));
        DockerInventoryToolRepository docker = new DockerInventoryToolRepository(
                inventory, logs, new ObjectMapper());
        HomelabToolCatalog catalog = new HomelabToolCatalog(docker);

        String result = catalog.execute("get_docker_container_logs",
                Map.of("hostKey", "docker-host-a", "containerName", "sample", "tail", 25));

        assertTrue(result.contains("\"status\":\"ok\""));
        assertTrue(result.contains("\"logs\":\"sample log"));
        verify(logs).readLogs("docker-host-a", "sample", 25);
    }

    @Test
    void dockerLogToolRejectsUnknownArgumentsAndOutOfRangeTailBeforeProviderCall() {
        DockerContainerLogsUseCase logs = mock(DockerContainerLogsUseCase.class);
        DockerInventoryToolRepository docker = new DockerInventoryToolRepository(
                mock(DockerInventoryUseCase.class), logs, new ObjectMapper());
        HomelabToolCatalog catalog = new HomelabToolCatalog(docker);

        String unknown = catalog.execute("get_docker_container_logs",
                Map.of("hostKey", "docker-host-a", "containerName", "sample", "path", "/etc/passwd"));
        String invalidTail = catalog.execute("get_docker_container_logs",
                Map.of("hostKey", "docker-host-a", "containerName", "sample", "tail", 201));

        assertTrue(unknown.startsWith("Error:"));
        assertTrue(invalidTail.startsWith("Error:"));
        org.mockito.Mockito.verifyNoInteractions(logs);
    }

    private static GuidanceToolRepository guidanceTool() {
        AiClientGuidanceDocument document =
                new AiClientGuidanceDocument(new ClassPathResource("guidance/AI-CLIENT-GUIDANCE.md"));
        return new GuidanceToolRepository(document);
    }

    private static HomelabToolCatalog configuredCatalog() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("homelab.docker.portainer.base-url", "https://portainer.example/");
        HomelabDiscoveryToolRepository proxmox = new HomelabDiscoveryToolRepository(
                environment, mock(ProxmoxInventoryService.class), new ObjectMapper());
        DockerInventoryToolRepository docker = new DockerInventoryToolRepository(
                mock(DockerInventoryUseCase.class), mock(DockerContainerLogsUseCase.class), new ObjectMapper());
        return new HomelabToolCatalog(proxmox, guidanceTool(), docker);
    }
}
