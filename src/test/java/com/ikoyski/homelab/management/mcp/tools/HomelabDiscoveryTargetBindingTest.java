package com.ikoyski.homelab.management.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class HomelabDiscoveryTargetBindingTest {

    @Test
    void bindsIndexedTargetsFromConfigServerEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("homelab.discovery.targets[0]", "dev-pve")
                .withProperty("homelab.discovery.targets[1]", "media-pve");

        List<String> targets = Binder.get(environment)
                .bind("homelab.discovery.targets", Bindable.listOf(String.class))
                .orElseGet(List::of);

        assertThat(targets).containsExactly("dev-pve", "media-pve");
    }

    @Test
    void returnsEmptyListWhenNoTargetsAreConfigured() {
        MockEnvironment environment = new MockEnvironment();

        List<String> targets = Binder.get(environment)
                .bind("homelab.discovery.targets", Bindable.listOf(String.class))
                .orElseGet(List::of);

        assertThat(targets).isEmpty();
    }
}
