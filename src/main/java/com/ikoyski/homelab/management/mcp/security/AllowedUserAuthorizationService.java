package com.ikoyski.homelab.management.mcp.security;

import java.util.List;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
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

        List<String> allowedUsers = Binder.get(environment)
                .bind(ALLOWED_USERS_PROPERTY, Bindable.listOf(String.class))
                .orElseGet(List::of);

        return allowedUsers.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(subject.trim()::equals);
    }
}
