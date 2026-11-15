package com.ikoyski.homelab.management.mcp.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AllowedUserAuthorizationServiceTest {

    private static final String ALLOWED_UUID =
            "a454ad56-1f74-4644-bb31-8d7c1447ec25";

    @Test
    void allowsSubjectWhenUuidIsInAllowlist() {
        var environment = new MockEnvironment()
                .withProperty("homelab.security.allowed-users", ALLOWED_UUID);
        var service = new AllowedUserAuthorizationService(environment);

        assertTrue(service.isAllowed(ALLOWED_UUID));
    }

    @Test
    void rejectsSubjectWhenUuidIsNotInAllowlist() {
        var environment = new MockEnvironment()
                .withProperty("homelab.security.allowed-users", ALLOWED_UUID);
        var service = new AllowedUserAuthorizationService(environment);

        assertFalse(service.isAllowed("some-other-user-uuid"));
    }

    @Test
    void rejectsEverySubjectWhenAllowlistIsEmpty() {
        var environment = new MockEnvironment()
                .withProperty("homelab.security.allowed-users", "");
        var service = new AllowedUserAuthorizationService(environment);

        assertFalse(service.isAllowed(ALLOWED_UUID));
    }

    @Test
    void rejectsEverySubjectWhenAllowlistIsMissing() {
        var service = new AllowedUserAuthorizationService(new MockEnvironment());

        assertFalse(service.isAllowed(ALLOWED_UUID));
    }

    @Test
    void rejectsNullOrBlankSubject() {
        var environment = new MockEnvironment()
                .withProperty("homelab.security.allowed-users", ALLOWED_UUID);
        var service = new AllowedUserAuthorizationService(environment);

        assertFalse(service.isAllowed(null));
        assertFalse(service.isAllowed(""));
        assertFalse(service.isAllowed("   "));
    }

    @Test
    void matchesEntriesInCommaSeparatedAllowlist() {
        var environment = new MockEnvironment()
                .withProperty("homelab.security.allowed-users",
                        "another-user-uuid, " + ALLOWED_UUID);
        var service = new AllowedUserAuthorizationService(environment);

        assertTrue(service.isAllowed(ALLOWED_UUID));
    }
}
