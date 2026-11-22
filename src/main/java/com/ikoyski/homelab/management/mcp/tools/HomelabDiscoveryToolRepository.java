package com.ikoyski.homelab.management.mcp.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;
import com.ikoyski.homelab.management.mcp.proxmox.ProxmoxInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Read-only inventory tools for homelab capabilities and Proxmox. */
@Component
public class HomelabDiscoveryToolRepository implements HomelabToolProvider {

    private static final Logger log = LoggerFactory.getLogger(HomelabDiscoveryToolRepository.class);
    private static final String CAPABILITIES = "get_homelab_capabilities";

    private final Environment environment;
    private final ProxmoxInventoryService proxmox;
    private final ObjectMapper mapper;
    private final Map<String, String> toolDescriptions;

    public HomelabDiscoveryToolRepository(Environment environment, ProxmoxInventoryService proxmox,
            ObjectMapper mapper) {
        this.environment = environment;
        this.proxmox = proxmox;
        this.mapper = mapper;
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put(CAPABILITIES, "Describe the Homelab MCP capabilities, configuration presence, safe discovery-scope labels, read-only boundary, and how to retrieve the AI client guidance. Does not test upstream connectivity.");
        definitions.put("list_proxmox_nodes",
                "List Proxmox cluster nodes and safe status/resource fields. CPU is a 0-1 fraction; cpuUsagePercent is the derived percentage. mem, maxmem, disk, and maxdisk are byte counts; byteUnit identifies the unit. memoryUsagePercent is derived from mem/maxmem. Missing metrics are omitted, not reported as zero.");
        definitions.put("list_proxmox_guests",
                "List discovered QEMU virtual machines and LXC containers. CPU is a 0-1 fraction; cpuUsagePercent is the derived percentage. mem, maxmem, disk, and maxdisk are byte counts; byteUnit identifies the unit. memoryUsagePercent is derived from mem/maxmem. QEMU disk is Proxmox root-image usage and is not guest filesystem usage; QEMU filesystem metrics are reported separately when the guest agent is available. Missing metrics are omitted, not reported as zero.");
        definitions.put("list_proxmox_storage",
                "List Proxmox storage inventory and safe capacity/status fields. totalBytes, usedBytes, and availableBytes are byte counts (byteUnit=bytes); usagePercent is calculated from Proxmox-reported usedBytes/totalBytes. Do not infer or relabel units.");
        definitions.put("get_proxmox_inventory",
                "Get combined Proxmox node, QEMU VM, LXC container, and storage inventory. CPU fields are fractions from 0 to 1 and cpuUsagePercent is the derived percentage. Memory and disk byte fields use byteUnit=bytes; memoryUsagePercent and storage/filesystem usagePercent are deterministic derived percentages. QEMU disk is not guest filesystem usage. Missing metrics are omitted, not reported as zero. Partial results are returned when a section is unavailable.");
        this.toolDescriptions = Collections.unmodifiableMap(definitions);
    }

    @Override
    public List<String> toolNames() {
        return List.copyOf(toolDescriptions.keySet());
    }

    @Override
    public String toolDescription(String name) {
        return toolDescriptions.get(name);
    }

    @Override
    public String execute(String name) {
        try {
            Object result = switch (name) {
                case CAPABILITIES -> homelabCapabilities();
                case "list_proxmox_nodes" -> proxmox.nodes();
                case "list_proxmox_guests" -> proxmox.guests();
                case "list_proxmox_storage" -> proxmox.storage();
                case "get_proxmox_inventory" -> proxmox.inventory();
                default -> null;
            };
            if (result == null) {
                return "Error: Unknown tool.";
            }
            log.info("mcp_tool_call_completed tool={}", name);
            return mapper.writeValueAsString(result);
        } catch (RuntimeException ex) {
            log.error("Unable to retrieve or serialize Proxmox inventory tool={}", name);
            return "Error: Unable to retrieve or serialize inventory result.";
        }
    }

    private Map<String, Object> homelabCapabilities() {
        List<String> targets = Binder.get(environment)
                .bind("homelab.discovery.targets", Bindable.listOf(String.class))
                .orElseGet(List::of);
        List<String> safeTargets = targets.stream()
                .filter(target -> target != null
                        && target.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,62}"))
                .toList();

        boolean proxmoxConfigured = hasRequiredProperties(
                "homelab.proxmox.api-url",
                "homelab.proxmox.token-id",
                "homelab.proxmox.token-secret");
        boolean portainerConfigured = hasRequiredProperties("homelab.docker.portainer.base-url");

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("proxmoxInventory", Map.of(
                "implemented", true,
                "enabled", true));
        capabilities.put("dockerInventory", Map.of(
                "implemented", true,
                "enabled", portainerConfigured));
        capabilities.put("boundedDockerLogs", Map.of(
                "implemented", true,
                "enabled", portainerConfigured));

        Map<String, Object> integrations = new LinkedHashMap<>();
        integrations.put("proxmox", Map.of(
                "configured", proxmoxConfigured,
                "connectivityChecked", false));
        integrations.put("portainer", Map.of(
                "configured", portainerConfigured,
                "connectivityChecked", false));

        Map<String, Object> discoveryScope = new LinkedHashMap<>();
        discoveryScope.put("configuredTargetCount", safeTargets.size());
        discoveryScope.put("targets", safeTargets);
        discoveryScope.put("targetValuesAreLabelsOnly", true);

        Map<String, Object> operatingBoundary = new LinkedHashMap<>();
        operatingBoundary.put("mode", "read-only");
        operatingBoundary.put("infrastructureMutations", false);
        operatingBoundary.put("shellAndContainerExec", false);

        Map<String, Object> guidance = new LinkedHashMap<>();
        guidance.put("available", true);
        guidance.put("resourceUri", "homelab://guidance/ai-client");
        guidance.put("retrievalTool", "get_ai_client_guidance");
        guidance.put("recommended",
                "Read the AI client guidance before performing homelab diagnostics.");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "read-only");
        result.put("capabilities", capabilities);
        result.put("integrations", integrations);
        result.put("discoveryScope", discoveryScope);
        result.put("operatingBoundary", operatingBoundary);
        result.put("guidance", guidance);
        result.put("message",
                "Configuration presence does not verify upstream connectivity or service health.");
        return result;
    }

    private boolean hasRequiredProperties(String... propertyNames) {
        for (String propertyName : propertyNames) {
            String value = environment.getProperty(propertyName);
            if (value == null || value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
