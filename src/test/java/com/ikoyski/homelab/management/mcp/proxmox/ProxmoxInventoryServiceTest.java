package com.ikoyski.homelab.management.mcp.proxmox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class ProxmoxInventoryServiceTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

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
    void mapsGuestFilesystemUsageToSafeStableFields() throws Exception {
        var source = jsonMapper.readTree("""
                {
                  "name": "sda2",
                  "mountpoint": "/",
                  "type": "ext4",
                  "total-bytes": 99700789248,
                  "used-bytes": 48389709824,
                  "disk": [{"serial": "must-not-be-exposed"}]
                }
                """);

        Map<String,Object> filesystem = ProxmoxInventoryService.safeFilesystemFields(source);

        assertThat(filesystem).containsEntry("name", "sda2")
                .containsEntry("mountpoint", "/")
                .containsEntry("type", "ext4")
                .containsEntry("totalBytes", 99700789248L)
                .containsEntry("usedBytes", 48389709824L)
                .containsEntry("availableBytes", 51311079424L)
                .containsEntry("usagePercent", 48.5)
                .doesNotContainKey("disk");
    }

    @Test
    void omitsUsagePercentageWhenFilesystemCapacityIsZero() throws Exception {
        var source = jsonMapper.readTree("""
                {"name":"empty","mountpoint":"/empty","type":"tmpfs","total-bytes":0,"used-bytes":0}
                """);

        Map<String,Object> filesystem = ProxmoxInventoryService.safeFilesystemFields(source);

        assertThat(filesystem).containsEntry("totalBytes", 0L)
                .containsEntry("usedBytes", 0L)
                .containsEntry("availableBytes", 0L)
                .doesNotContainKey("usagePercent");
    }
}
    void collectedAtIsAnIso8601UtcTimestamp() {
        assertThat(Instant.parse(ProxmoxInventoryService.collectedAt())).isNotNull();
    }
}
