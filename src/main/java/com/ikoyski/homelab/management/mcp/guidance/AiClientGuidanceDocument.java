package com.ikoyski.homelab.management.mcp.guidance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Loads the canonical, repository-root AI client guide packaged into the application JAR.
 *
 * <p>The Markdown file remains the single source of truth; Maven copies it into the
 * application classpath without maintaining a second source copy.</p>
 */
@Component
public final class AiClientGuidanceDocument {

    private final String content;

    public AiClientGuidanceDocument(
            @Value("classpath:guidance/AI-CLIENT-GUIDANCE.md") Resource source) {
        try (var input = source.getInputStream()) {
            this.content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load AI client guidance document", ex);
        }
        if (this.content.isBlank()) {
            throw new IllegalStateException("AI client guidance document is empty");
        }
    }

    public String content() {
        return content;
    }
}
