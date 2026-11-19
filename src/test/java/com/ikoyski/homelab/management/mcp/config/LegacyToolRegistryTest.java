package com.ikoyski.homelab.management.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.ikoyski.homelab.management.mcp.tools.DockerInventoryToolRepository;
import com.ikoyski.homelab.management.mcp.tools.HomelabDiscoveryToolRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyToolRegistryTest {

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
    void exposesAllNineToolsAndDispatchesToTheirOwningRepositories() {
        HomelabDiscoveryToolRepository proxmox = mock(HomelabDiscoveryToolRepository.class);
        DockerInventoryToolRepository docker = mock(DockerInventoryToolRepository.class);
        when(proxmox.toolNames()).thenReturn(PROXMOX_TOOLS);
        when(proxmox.toolDescription(anyString())).thenReturn("Proxmox description");
        when(proxmox.execute(anyString())).thenReturn("{\"source\":\"proxmox\"}");
        when(docker.toolNames()).thenReturn(DOCKER_TOOLS);
        when(docker.toolDescription(anyString())).thenReturn("Docker description");
        when(docker.execute(anyString())).thenReturn("{\"source\":\"docker\"}");

        LegacyToolRegistry registry = LegacyToolRegistry.create(proxmox, docker);

        assertEquals(9, registry.toolNames().size());
        assertTrue(registry.toolNames().containsAll(PROXMOX_TOOLS));
        assertTrue(registry.toolNames().containsAll(DOCKER_TOOLS));
        assertEquals("{\"source\":\"proxmox\"}", registry.execute("get_proxmox_inventory"));
        assertEquals("{\"source\":\"docker\"}", registry.execute("get_docker_inventory"));
        verify(proxmox).execute("get_proxmox_inventory");
        verify(docker).execute("get_docker_inventory");
    }

    @Test
    void preservesFiveProxmoxToolsWhenDockerIsNotConfigured() {
        HomelabDiscoveryToolRepository proxmox = mock(HomelabDiscoveryToolRepository.class);
        when(proxmox.toolNames()).thenReturn(PROXMOX_TOOLS);
        when(proxmox.toolDescription(anyString())).thenReturn("Proxmox description");
        when(proxmox.execute(anyString())).thenReturn("{}");

        LegacyToolRegistry registry = LegacyToolRegistry.create(proxmox, null);

        assertEquals(5, registry.toolNames().size());
        assertTrue(registry.toolNames().containsAll(PROXMOX_TOOLS));
        assertFalse(registry.toolNames().stream().anyMatch(DOCKER_TOOLS::contains));
        assertEquals("Error: Unknown tool.", registry.execute("get_docker_inventory"));
    }
}
