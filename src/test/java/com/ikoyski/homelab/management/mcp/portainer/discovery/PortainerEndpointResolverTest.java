package com.ikoyski.homelab.management.mcp.portainer.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ikoyski.homelab.management.mcp.portainer.config.PortainerProperties;
import com.ikoyski.homelab.management.mcp.portainer.client.PortainerApiClient;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PortainerEndpointResolverTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void resolvesOnlyExactConfiguredHostNamesAndKeepsEndpointIdsInternalToResolution() throws Exception {
        PortainerApiClient client = mock(PortainerApiClient.class);
        when(client.listEndpoints()).thenReturn(mapper.readTree("""
                [
                  {"Id": 11, "Name": "docker-host-a", "URL": "tcp://ignored"},
                  {"Id": 12, "Name": "docker-host-b"},
                  {"Id": 99, "Name": "unconfigured-host"},
                  {"Id": 13, "Name": "DOCKER-HOST-A"}
                ]
                """));
        PortainerEndpointResolver resolver = resolver(client, "docker-host-a", "docker-host-b", "docker-host-c");

        Map<String, PortainerEndpointResolver.EndpointResolution> result = resolver.resolveAll();

        assertEquals(PortainerEndpointResolver.Status.RESOLVED, result.get("docker-host-a").status());
        assertEquals(11, result.get("docker-host-a").endpointId());
        assertEquals(PortainerEndpointResolver.Status.RESOLVED, result.get("docker-host-b").status());
        assertEquals(12, result.get("docker-host-b").endpointId());
        assertEquals(PortainerEndpointResolver.Status.NOT_FOUND, result.get("docker-host-c").status());
        assertFalse(result.containsKey("unconfigured-host"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.put("unconfigured-host", new PortainerEndpointResolver.EndpointResolution(
                        PortainerEndpointResolver.Status.RESOLVED, 99)));
    }

    @Test
    void marksDuplicateEndpointNamesAmbiguousInsteadOfChoosingOne() throws Exception {
        PortainerApiClient client = mock(PortainerApiClient.class);
        when(client.listEndpoints()).thenReturn(mapper.readTree("""
                [{"Id": 1, "Name": "docker-host-a"}, {"Id": 2, "Name": "docker-host-a"}]
                """));

        PortainerEndpointResolver.EndpointResolution result =
                resolver(client, "docker-host-a").resolveAll().get("docker-host-a");

        assertEquals(PortainerEndpointResolver.Status.AMBIGUOUS, result.status());
        assertTrue(result.endpointId() == null);
    }

    @Test
    void rejectsAnInvalidIdForAnOtherwiseMatchingEndpoint() throws Exception {
        PortainerApiClient client = mock(PortainerApiClient.class);
        when(client.listEndpoints()).thenReturn(mapper.readTree("""
                [{"Id": "not-a-number", "Name": "docker-host-a"}]
                """));

        PortainerEndpointResolver.EndpointResolution result =
                resolver(client, "docker-host-a").resolveAll().get("docker-host-a");

        assertEquals(PortainerEndpointResolver.Status.INVALID_ENDPOINT_ID, result.status());
        assertTrue(result.endpointId() == null);
    }

    @Test
    void rejectsNonArrayDiscoveryResponse() throws Exception {
        PortainerApiClient client = mock(PortainerApiClient.class);
        when(client.listEndpoints()).thenReturn(mapper.readTree("{\"data\": []}"));

        assertThrows(IllegalStateException.class, () -> resolver(client, "docker-host-a").resolveAll());
    }

    private PortainerEndpointResolver resolver(PortainerApiClient client, String... hostKeys) {
        PortainerProperties properties = new PortainerProperties(
                URI.create("https://portainer.test/"),
                "test-api-key",
                3000,
                8000,
                2048,
                new PortainerProperties.Environments(List.of(hostKeys)));
        return new PortainerEndpointResolver(client, properties);
    }
}
