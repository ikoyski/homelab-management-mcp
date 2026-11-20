package com.ikoyski.homelab.management.mcp.portainer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort redaction for common credential formats in returned log text. */
final class DockerLogRedactor {

    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)([\\\"']?(?:password|passwd|pwd|token|secret|api[_-]?key|client[_-]?secret|access[_-]?key|private[_-]?key)[\\\"']?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;]+)");
    private static final Pattern URL_CREDENTIALS = Pattern.compile(
            "(?i)(https?://[^:/\\s]+:)[^@/\\s]+(@)");

    private DockerLogRedactor() {
    }

    static Redaction redact(String value) {
        String result = value == null ? "" : value;
        int count = 0;
        Matcher auth = AUTHORIZATION.matcher(result);
        count += count(auth);
        result = auth.replaceAll("$1 [REDACTED]");

        Matcher keyValue = KEY_VALUE.matcher(result);
        count += count(keyValue);
        result = keyValue.replaceAll("$1[REDACTED]");

        Matcher urlCredentials = URL_CREDENTIALS.matcher(result);
        count += count(urlCredentials);
        result = urlCredentials.replaceAll("$1[REDACTED]$2");
        return new Redaction(result, count);
    }

    private static int count(Matcher matcher) {
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    record Redaction(String value, int redactedValueCount) {
    }
}
