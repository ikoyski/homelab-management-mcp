package com.ikoyski.homelab.management.mcp.inventory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ikoyski.homelab.management.mcp.inventory.model.DockerContainerStats;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DockerInventoryUseCaseTest {

    @Test
    void delegatesResourceSnapshotFiltersToTheProvider() {
        DockerInventoryProvider provider = mock(DockerInventoryProvider.class);
        DockerContainerStats sample = new DockerContainerStats("dev-pve", "sample", "abcdef123456",
                "ok", Instant.now(), 0.0, 1024L, 768L, 4096L, 18.75, 256L,
                100L, 200L, 300L, 400L, null);
        when(provider.collectStats("dev-pve", "sample")).thenReturn(List.of(sample));
        DockerInventoryUseCase useCase = new DockerInventoryUseCase(provider);

        List<DockerContainerStats> result = useCase.collectStats("dev-pve", "sample");

        assertEquals(List.of(sample), result);
        verify(provider).collectStats("dev-pve", "sample");
    }
}
