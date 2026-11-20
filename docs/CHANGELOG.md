# Changelog

Notable project changes are recorded here in reverse chronological order. This is a concise project-level history, not a replacement for Git commit history or release notes.

## 2026-11-21

### Validated
- Claude Code and Grok independently exercised the deployed Docker inventory across all five configured Portainer environments. The observed baseline was 43 containers (39 running and four intentionally exited) with no collection errors. The exited containers are intentional and are not cleanup/restart recommendations.
- The live inventory exposes container-side network attachments, while network-side `connectedContainers`, restart count, StartedAt, and uptime remain data-quality gaps in the deployed build.

### In progress
- Branch `feat/docker-inventory-enrichment-and-host-filters` adds bounded fixed-GET container detail enrichment, explicit health-check semantics, same-host network membership fallback, and optional exact `hostKey` filters for the container and network list tools.
- CI, merge, and deployed-client validation for these improvements are pending. The live service must not be described as supporting the new host filters or enriched detail fields until deployment is verified.
## 2026-11-20

### Validated
- Gemini successfully connected to the deployed Homelab MCP and queried live Proxmox inventory, returning a detailed report covering the node, all six guests, and storage pools. Docker inventory was not tested.

## 2026-11-19

### Validated
- Claude Code successfully completed Webtools OAuth authorization, connected to the deployed Homelab Management MCP, discovered and used the Proxmox inventory tools, and generated a natural-language report from live inventory data.
- Grok successfully connected to the deployed Homelab MCP and queried live Proxmox inventory, returning node, guest, and storage details. This validates the observed Grok MCP/tool-execution path for Release 1 only; Docker inventory was not tested.
- Gemini successfully connected to the deployed Homelab MCP and returned a detailed live Proxmox inventory report covering the node, all six guests, and storage pools. This validates the observed Gemini MCP/tool-execution path for Release 1 only; Docker inventory was not tested.
- Upgraded the Portainer Server and its Agent-managed Docker environments to a compatible Portainer CE release. All configured environments reconnected and reported resources; deployment-specific environment names are kept in private configuration.
- Docker Engine information was returned successfully for both the local Engine and Agent-managed Engines. Per-host versions are omitted from this public changelog.

### Architecture decision
- Accepted ADR-0003: keep the MCP as a modular monolith with distinct inbound MCP, Proxmox infrastructure, Portainer infrastructure, and application-owned inventory/domain modules. The separate Portainer Proxy deployment approach was abandoned; PR #5 was closed unmerged.

### Added
- Ported the Portainer HTTPS client foundation, API-key handling, bounded response-size interceptor, exact environment allowlist validation, dynamic endpoint resolver, and focused tests into the MCP's dedicated Portainer infrastructure module.
- Added application-owned Docker host, container, network, published-port, and aggregate inventory models, a Docker inventory application port/use case, a Portainer adapter with per-environment failure isolation, and four conditional MCP Docker inventory tools.
- Added modular-monolith architecture documentation and updated the Release 2 handoff and engineering instructions.

### Still outstanding for Release 2
- Maven build and automated tests have not yet been run against this branch; verify CI before merge.
- Add/verify container detail enrichment for restart count, StartedAt, and uptime; keep unavailable values null rather than inventing them.
- Add private Portainer runtime properties to the MCP Config Server configuration and provision the self-signed certificate truststore in the MCP runtime. No Config Server secrets were changed in this work.
- Validate the complete integration through an authenticated MCP client. No deployment or live Portainer connection has been performed.

## 2026-11-17

### Added
- Release 2 Docker read-only inventory architecture (PR #18).
- Portainer Server/Agent upgrade compatibility validation procedure (PR #19).
- Canonical project status and handoff documentation.
- Architecture decision records for the Portainer adapter boundary and read-only security requirements.

### Status
- Release 1 Proxmox inventory is implemented and deployed; authenticated MCP validation has been observed for all five Proxmox tools.
- Release 2 remains in architecture/prerequisite stage; no Docker inventory implementation or deployment is claimed.
