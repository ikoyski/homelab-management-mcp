package com.ikoyski.homelab.management.mcp.proxmox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ProxmoxInventoryServiceTest {

    @Test
    void sortsStorageContentTypesAlphabeticallyAndRemovesWhitespace() {
        assertThat(ProxmoxInventoryService.sortContentTypes("images, rootdir,backup"))
                .isEqualTo("backup,images,rootdir");
    }

    @Test
    void handlesEmptyAndDuplicateContentTypesDeterministically() {
        assertThat(ProxmoxInventoryService.sortContentTypes("rootdir,,images,images"))
                .isEqualTo("images,images,rootdir");
    }

    @Test
    void sortsStorageEntriesByStorageName() {
        Map<String,Object> result = new LinkedHashMap<>();
        Map<String,Object> localLvm = Map.of("storage", "local-lvm");
        Map<String,Object> local = Map.of("storage", "local");
        Map<String,Object> backup = Map.of("storage", "backup");
        result.put("status", "ok");
        result.put("count", 3);
        result.put("items", List.of(localLvm, local, backup));

        Map<String,Object> sorted = ProxmoxInventoryService.sortStorageEntries(result);

        assertThat(sorted.get("items")).isEqualTo(List.of(backup, local, localLvm));
        assertThat(sorted.get("count")).isEqualTo(3);
        assertThat(sorted.get("status")).isEqualTo("ok");
    }

    @Test
    void leavesStorageResultWithoutItemsUnchanged() {
        Map<String,Object> result = Map.of("status", "error", "count", 0);

        assertThat(ProxmoxInventoryService.sortStorageEntries(result)).isSameAs(result);
    }

    @Test
    void collectedAtIsAnIso8601UtcTimestamp() {
        assertThat(Instant.parse(ProxmoxInventoryService.collectedAt())).isNotNull();
    }
}