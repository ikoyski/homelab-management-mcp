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
    void mapsDirectoryStorageStatusToSafeCapacityFields() throws Exception {
        var response = jsonMapper.readTree("""
                {
                  "type": "dir",
                  "total": 1967846068224,
                  "used": 906400428032,
                  "avail": 961408970752,
                  "active": 1,
                  "enabled": 1,
                  "shared": 0,
                  "path": "/must-not-be-exposed",
                  "content": "must-not-be-exposed"
                }
                """);

        Map<String,Object> storage = ProxmoxInventoryService.safeStorageStatusFields(response);

        assertThat(storage).containsEntry("storageType", "dir")
                .containsEntry("totalBytes", 1967846068224L)
                .containsEntry("usedBytes", 906400428032L)
                .containsEntry("availableBytes", 961408970752L)
                .containsEntry("active", 1)
                .containsEntry("enabled", 1)
                .containsEntry("shared", 0)
                .containsEntry("usagePercent", 46.1)
                .doesNotContainKeys("type", "path", "content");
    }

    @Test
    void mapsLvmThinStorageWithoutInventingUsage() throws Exception {
        var response = jsonMapper.readTree("""
                {"type":"lvmthin","total":52978253824,"used":0,"avail":52978253824,"active":1,"enabled":1,"shared":0}
                """);

        Map<String,Object> storage = ProxmoxInventoryService.safeStorageStatusFields(response);

        assertThat(storage).containsEntry("storageType", "lvmthin")
                .containsEntry("totalBytes", 52978253824L)
                .containsEntry("usedBytes", 0L)
                .containsEntry("availableBytes", 52978253824L)
                .containsEntry("usagePercent", 0.0);
    }

    @Test
    void preservesInactiveStorageAndOmitsUsageWhenCapacityIsZero() throws Exception {
        var response = jsonMapper.readTree("""
                {"type":"dir","total":0,"used":0,"avail":0,"active":0,"enabled":1,"shared":0}
                """);

        Map<String,Object> storage = ProxmoxInventoryService.safeStorageStatusFields(response);

        assertThat(storage).containsEntry("storageType", "dir")
                .containsEntry("totalBytes", 0L)
                .containsEntry("usedBytes", 0L)
                .containsEntry("availableBytes", 0L)
                .containsEntry("active", 0)
                .doesNotContainKey("usagePercent");
    }

    @Test
    void rejectsMalformedStorageStatusResponse() throws Exception {
        var response = jsonMapper.readTree("""["not","an","object"]""");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> ProxmoxInventoryService.safeStorageStatusFields(response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected Proxmox storage status response shape");
    }

    @Test
    void mapsStorageStatusFailuresToStableErrorCodes() {
        assertThat(ProxmoxInventoryService.storageFailureCode(
                new IllegalStateException("Unexpected Proxmox storage status response shape")))
                .isEqualTo("unexpected_response");
        assertThat(ProxmoxInventoryService.storageFailureCode(new RuntimeException("private detail")))
                .isEqualTo("storage_status_request_failed");
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


    @Test
    void mapsActualProxmoxGuestAgentResponseEnvelopeToSafeFilesystemFields() throws Exception {
        var response = jsonMapper.readTree("""
                {
                  "data": {
                    "result": [
                      {
                        "name": "sda2",
                        "mountpoint": "/",
                        "type": "ext4",
                        "total-bytes": 99700789248,
                        "used-bytes": 48474042368,
                        "disk": [{"serial": "must-not-be-exposed"}]
                      }
                    ]
                  }
                }
                """);

        List<Map<String,Object>> filesystems = ProxmoxInventoryService.filesystemEntries(response);

        assertThat(filesystems).hasSize(1);
        assertThat(filesystems.get(0)).containsEntry("name", "sda2")
                .containsEntry("mountpoint", "/")
                .containsEntry("type", "ext4")
                .containsEntry("totalBytes", 99700789248L)
                .containsEntry("usedBytes", 48474042368L)
                .containsEntry("availableBytes", 51226746880L)
                .doesNotContainKey("disk");
    }

    @Test
    void acceptsEmptyGuestAgentFilesystemResult() throws Exception {
        var response = jsonMapper.readTree("""
                {"data":{"result":[]}}
                """);

        assertThat(ProxmoxInventoryService.filesystemEntries(response)).isEmpty();
    }

    @Test
    void rejectsGuestAgentResponseWithoutResultArray() throws Exception {
        var response = jsonMapper.readTree("""
                {"data":[{"name":"sda2"}]}
                """);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> ProxmoxInventoryService.filesystemEntries(response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected QEMU guest-agent response shape");
    }

    @Test
    void mapsMissingGuestIdToStableFilesystemErrorCode() {
        assertThat(ProxmoxInventoryService.filesystemFailureCode(
                new IllegalStateException("QEMU guest ID is unavailable")))
                .isEqualTo("guest_id_unavailable");
    }

    @Test
    void mapsUnexpectedAgentResponseToStableFilesystemErrorCode() {
        assertThat(ProxmoxInventoryService.filesystemFailureCode(
                new IllegalStateException("Unexpected QEMU guest-agent response shape")))
                .isEqualTo("unexpected_response");
    }

    @Test
    void mapsOtherFilesystemFailuresToGenericStableErrorCode() {
        assertThat(ProxmoxInventoryService.filesystemFailureCode(
                new IllegalStateException("internal implementation detail")))
                .isEqualTo("guest_filesystem_request_failed");
        assertThat(ProxmoxInventoryService.filesystemFailureCode(new RuntimeException("failure")))
                .isEqualTo("guest_filesystem_request_failed");
    }

    @Test
    void collectedAtIsAnIso8601UtcTimestamp() {
        assertThat(Instant.parse(ProxmoxInventoryService.collectedAt())).isNotNull();
    }
}
