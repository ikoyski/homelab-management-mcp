package com.ikoyski.homelab.management.mcp.portainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;
import com.ikoyski.homelab.management.mcp.portainer.config.PortainerProperties;
import com.ikoyski.homelab.management.mcp.portainer.discovery.PortainerEndpointResolver;
import com.ikoyski.homelab.management.mcp.portainer.discovery.PortainerEndpointResolver.EndpointResolution;
import com.ikoyski.homelab.management.mcp.portainer.discovery.PortainerEndpointResolver.Status;
import com.ikoyski.homelab.management.mcp.portainer.client.PortainerApiClient;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class PortainerDockerInventoryAdapterTest {
    private static final List<String> HOSTS =
            List.of("util-pve", "dev-pve", "earn-pve", "media-pve", "ncloud-pve");
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void mapsOnlyAllowlistedHostContainerAndNetworkFields() throws Exception {
        PortainerApiClient client = mock(PortainerApiClient.class);
        PortainerEndpointResolver resolver = mock(PortainerEndpointResolver.class);
        when(resolver.resolveAll()).thenReturn(resolutions("util-pve", 7));
        when(client.getDockerInfo(7)).thenReturn(mapper.readTree("""
                {"ServerVersion":"28.4.0","OperatingSystem":"Docker Engine","SecurityOptions":["sensitive"]}
                """));
        when(client.listContainers(7)).thenReturn(mapper.readTree("""
                [{
                  "Id":"abc123",
                  "Names":["/sample"],
                  "Image":"nginx:latest",
                  "State":"running",
                  "Status":"Up 2 hours (healthy)",
                  "Created":1760000000,
                  "Ports":[{"PrivatePort":80,"PublicPort":8080,"Type":"tcp"}],
                  "NetworkSettings":{"Networks":{"bridge":{}}},
                  "Env":["SECRET=value"],
                  "Labels":{"private":"metadata"},
                  "Mounts":[{"Source":"/private"}]
                }]
                """));
        when(client.listNetworks(7)).thenReturn(mapper.readTree("""
                [{
                  "Id":"network123",
                  "Name":"bridge",
                  "Driver":"bridge",
                  "Scope":"local",
                  "Internal":false,
                  "Attachable":true,
                  "Containers":{"abc123":{"Name":"sample","IPv4Address":"10.0.0.2"}},
                  "IPAM":{"Config":[{"Subnet":"10.0.0.0/24"}]},
                  "Options":{"private":"metadata"}
                }]
                """));

        DockerInventory result = adapter(client, resolver).collect();

        assertThat(result.status()).isEqualTo("partial");
        assertThat(result.hosts()).hasSize(5);
        assertThat(result.hosts().get(0).hostKey()).isEqualTo("util-pve");
        assertThat(result.hosts().get(0).dockerEngineVersion()).isEqualTo("28.4.0");
        assertThat(result.containers()).hasSize(1);
        assertThat(result.containers().get(0).name()).isEqualTo("sample");
        assertThat(result.containers().get(0).health()).isEqualTo("healthy");
        assertThat(result.containers().get(0).restartCount()).isNull();
        assertThat(result.containers().get(0).publishedPorts()).containsExactly(
                new com.ikoyski.homelab.management.mcp.inventory.model.PublishedPort(80, 8080, "tcp"));
        assertThat(result.networks()).hasSize(1);
        assertThat(result.networks().get(0).connectedContainers()).containsExactly("sample");
        assertThat(result.toString()).doesNotContain("SECRET", "private", "10.0.0.2", "10.0.0.0/24");
    }

    @Test
    void isolatesContainerCollectionFailureToOneEnvironment() throws Exception {
        PortainerApiClient client = mock(PortainerApiClient.class);
        PortainerEndpointResolver resolver = mock(PortainerEndpointResolver.class);
        when(resolver.resolveAll()).thenReturn(resolutions("util-pve", 7, "dev-pve", 8));
        when(client.getDockerInfo(7)).thenReturn(mapper.readTree("{\"ServerVersion\":\"28.4.0\"}"));
        when(client.getDockerInfo(8)).thenReturn(mapper.readTree("{\"ServerVersion\":\"29.1.3\"}"));
        when(client.listContainers(7)).thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        when(client.listContainers(8)).thenReturn(mapper.readTree("[]"));
        when(client.listNetworks(7)).thenReturn(mapper.readTree("[]"));
        when(client.listNetworks(8)).thenReturn(mapper.readTree("[]"));

        DockerInventory result = adapter(client, resolver).collect();

        assertThat(result.status()).isEqualTo("partial");
        assertThat(result.hosts()).filteredOn(host -> "util-pve".equals(host.hostKey()))
                .singleElement().extracting(host -> host.status()).isEqualTo("partial");
        assertThat(result.hosts()).filteredOn(host -> "dev-pve".equals(host.hostKey()))
                .singleElement().extracting(host -> host.status()).isEqualTo("ok");
        assertThat(result.errors()).anyMatch(error -> error.startsWith("util-pve: containers:"));
    }

    private PortainerDockerInventoryAdapter adapter(PortainerApiClient client,
            PortainerEndpointResolver resolver) {
        PortainerProperties properties = new PortainerProperties(
                URI.create("https://portainer.test/"), "test-api-key", 3000, 8000, 2048,
                new PortainerProperties.Environments(HOSTS));
        return new PortainerDockerInventoryAdapter(client, resolver, properties);
    }

    private static Map<String, EndpointResolution> resolutions(Object... pairs) {
        Map<String, EndpointResolution> result = new LinkedHashMap<>();
        for (String host : HOSTS) {
            result.put(host, new EndpointResolution(Status.NOT_FOUND, null));
        }
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], new EndpointResolution(Status.RESOLVED, (Integer) pairs[i + 1]));
        }
        return result;
    }
}
