package com.ikoyski.homelab.management.mcp.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceOutcome;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.modern.McpSchema.ResourceContents;
import io.modelcontextprotocol.modern.McpSchema.TextResourceContents;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GuidanceResourceRepositoryTest {

    @Test
    void listsAndReadsTheCanonicalMarkdownGuide() {
        AiClientGuidanceDocument document =
                new AiClientGuidanceDocument(new ClassPathResource("guidance/AI-CLIENT-GUIDANCE.md"));
        GuidanceResourceRepository repository = new GuidanceResourceRepository(document);

        var page = repository.list(null, null);
        assertThat(page.resources()).hasSize(1);
        assertThat(page.resources().get(0).uri()).isEqualTo(GuidanceResourceRepository.RESOURCE_URI);
        assertThat(page.resources().get(0).mimeType()).isEqualTo("text/markdown");

        McpSyncResponse<ReadResourceOutcome> response = repository.read(
                null, ReadResourceRequest.builder(GuidanceResourceRepository.RESOURCE_URI).build());
        assertThat(response).isInstanceOf(McpSyncResponse.Result.class);

        ReadResourceOutcome outcome = ((McpSyncResponse.Result<ReadResourceOutcome>) response).result();
        assertThat(outcome).isInstanceOf(ReadResourceResult.class);
        List<ResourceContents> contents = ((ReadResourceResult) outcome).contents();
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0)).isInstanceOf(TextResourceContents.class);

        TextResourceContents text = (TextResourceContents) contents.get(0);
        assertThat(text.uri()).isEqualTo(GuidanceResourceRepository.RESOURCE_URI);
        assertThat(text.mimeType()).isEqualTo("text/markdown");
        assertThat(text.text())
                .contains("Inventory-first diagnostic workflow")
                .contains("Read-only boundary and operator approval");
    }

    @Test
    void rejectsUnknownResourceUris() {
        GuidanceResourceRepository repository = new GuidanceResourceRepository(
                new AiClientGuidanceDocument(new ClassPathResource("guidance/AI-CLIENT-GUIDANCE.md")));

        assertThatThrownBy(() -> repository.read(
                null, ReadResourceRequest.builder("homelab://guidance/unknown").build()))
                .isInstanceOf(McpException.class);
    }

    @Test
    void rejectsUnexpectedResourceListCursor() {
        GuidanceResourceRepository repository = new GuidanceResourceRepository(
                new AiClientGuidanceDocument(new ClassPathResource("guidance/AI-CLIENT-GUIDANCE.md")));

        assertThatThrownBy(() -> repository.list(null, "unexpected"))
                .isInstanceOf(McpException.class);
    }
}
