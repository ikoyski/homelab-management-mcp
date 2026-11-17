package com.ikoyski.homelab.management.mcp.proxmox;

import static org.assertj.core.api.Assertions.assertThat;

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
}