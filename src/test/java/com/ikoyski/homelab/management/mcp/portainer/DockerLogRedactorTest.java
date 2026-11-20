package com.ikoyski.homelab.management.mcp.portainer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DockerLogRedactorTest {

    @Test
    void redactsCommonAuthorizationKeyValueAndUrlCredentialPatterns() {
        String input = "Authorization: Bearer abc.def.ghi\n"
                + "{\"password\":\"password-value\",\"api_key\":\"api-key-value\"}\n"
                + "connecting to https://service-user:url-password@internal.example/path";
        DockerLogRedactor.Redaction redaction = DockerLogRedactor.redact(input);

        assertThat(redaction.value())
                .contains("Bearer [REDACTED]", "\"password\":[REDACTED]", "\"api_key\":[REDACTED]",
                        "https://service-user:[REDACTED]@internal.example/path")
                .doesNotContain("abc.def.ghi", "password-value", "api-key-value", "url-password");
        assertThat(redaction.redactedValueCount()).isEqualTo(4);
    }
}
