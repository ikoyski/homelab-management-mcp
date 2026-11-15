package com.ikoyski.homelab.management.mcp.security;

import java.util.Arrays;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Provider-independent authorization against the central Webtools subject UUID allowlist.
 * An absent, blank, or empty allowlist denies access.
 */
@Component
public class AllowedUserAuthorizationService {

    private static final String ALLOWED_USERS_PROPERTY = "homelab.security.allowed-users";

    private final Environment environment;

    public AllowedUserAuthorizationService(Environment environment) {
        this.environment = environment;
    }

    public boolean isAllowed(String subject) {
        if (!StringUtils.hasText(subject)) {
            return false;
        }

        String[] allowedUsers = environment.getProperty(
                ALLOWED_USERS_PROPERTY, String[].class, new String[0]);

        return Arrays.stream(allowedUsers)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(subject.trim()::equals);
    }
}
