package com.ikoyski.homelab.management.mcp.portainer.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PortainerApiClientTest {

    private static final String CONTAINER_ID = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    void usesOnlyFixedGetOperationsAndThePortainerApiKeyHeader() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://portainer.test")
                .defaultHeader("X-API-Key", "test-api-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PortainerApiClient client = new PortainerApiClient(builder.build());

        server.expect(requestTo("https://portainer.test/api/endpoints"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "test-api-key"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://portainer.test/api/endpoints/7/docker/info"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "test-api-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://portainer.test/api/endpoints/7/docker/containers/json?all=true"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "test-api-key"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://portainer.test/api/endpoints/7/docker/containers/" + CONTAINER_ID + "/json"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "test-api-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://portainer.test/api/endpoints/7/docker/networks"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "test-api-key"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertNotNull(client.listEndpoints());
        assertNotNull(client.getDockerInfo(7));
        assertNotNull(client.listContainers(7));
        assertNotNull(client.inspectContainer(7, CONTAINER_ID));
        assertNotNull(client.listNetworks(7));
        server.verify();
    }


    @Test
    void retrievesBoundedTimestampedStdoutAndStderrWithoutFollowing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://portainer.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PortainerApiClient client = new PortainerApiClient(builder.build());

        server.expect(requestTo("https://portainer.test/api/endpoints/7/docker/containers/"
                        + CONTAINER_ID + "/logs?stdout=true&stderr=true&timestamps=true&follow=false&tail=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("2026-11-20T10:00:00Z sample log\n", MediaType.TEXT_PLAIN));

        DockerLogPayload payload = client.getContainerLogs(7, CONTAINER_ID, 100);

        assertNotNull(payload);
        assertEquals(false, payload.truncated());
        assertEquals("2026-11-20T10:00:00Z sample log\n",
                new String(payload.bytes(), java.nio.charset.StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void rejectsInvalidLogTailAndContainerIdsBeforeMakingRequests() {
        PortainerApiClient client = new PortainerApiClient(
                RestClient.builder().baseUrl("https://portainer.test").build());

        assertThrows(IllegalArgumentException.class, () -> client.getContainerLogs(7, CONTAINER_ID, 0));
        assertThrows(IllegalArgumentException.class, () -> client.getContainerLogs(7, CONTAINER_ID, 201));
        assertThrows(IllegalArgumentException.class, () -> client.getContainerLogs(0, CONTAINER_ID, 100));
        assertThrows(IllegalArgumentException.class, () -> client.getContainerLogs(7, "../info", 100));
    }

    @Test
    void rejectsInvalidEndpointAndContainerIdsBeforeMakingRequests() {
        PortainerApiClient client = new PortainerApiClient(
                RestClient.builder().baseUrl("https://portainer.test").build());

        assertThrows(IllegalArgumentException.class, () -> client.getDockerInfo(0));
        assertThrows(IllegalArgumentException.class, () -> client.listContainers(-1));
        assertThrows(IllegalArgumentException.class, () -> client.listNetworks(0));
        assertThrows(IllegalArgumentException.class, () -> client.inspectContainer(1, "../info"));
        assertThrows(IllegalArgumentException.class, () -> client.inspectContainer(1, "short"));
    }
}
