# Project Status and Handoff

> **Purpose:** Canonical continuity record for maintainers and AI coding assistants. Update this file whenever a milestone, architecture decision, validation result, or deployment state changes.
>
> **Last updated:** 2026-11-21 (Asia/Manila)
> **Repository:** `ikoyski/homelab-management-mcp`
> **Current focus:** Release 2 — Docker inventory data-quality improvements

## Current project state

| Area | Status | Notes |
|---|---|---|
| Release 1 — Proxmox inventory | Implemented and deployed | Five MCP tools; authenticated live inventory has been exercised by Claude Code, Grok, and Gemini. |
| Claude Code client integration | Validated | OAuth, MCP tool discovery, Proxmox inventory, and Docker inventory across all five configured hosts were successfully exercised. |
| Grok client integration | Validated | Proxmox and Docker inventory tools were independently exercised; Docker results covered all five hosts and the same 43-container baseline. |
| Gemini client integration | Validated | Authenticated MCP connection and live Proxmox inventory succeeded on 2026-11-20. Docker inventory has not been validated with Gemini. |
| Release 2 — Docker inventory | Core implementation deployed and live-validated; enrichment improvements in progress | All five configured hosts return successfully. Claude Code reported 43 containers (39 running, 4 intentionally exited) with no collection errors. Network-side membership and container inspect metadata are gaps in the currently deployed build. |
| Portainer Server/Agent upgrade | Completed and validated | Server and all four Agents are on CE 2.45.4; all five configured environments reconnected and reported resources. |
| Portainer TLS trust and endpoint mapping | Completed and validated | Java trust for the new Portainer certificate is installed in the MCP runtime. The local endpoint is named `util-pve`; IDs are discovered dynamically. |
| Separate Portainer Proxy | Abandoned | The separate repository is no longer part of the target architecture; the Portainer adapter lives inside this MCP. |
| Portainer authorization boundary | Operational; credential risk documented | The adapter exposes only fixed GET operations, but the Portainer API key inherits its user's Administrator permissions. Treat the key as a high-impact credential. |
| Natural-language status/reporting improvements | Deferred | Revisit after Docker inventory enrichment and host-scoped queries are complete. |

## Release 1 — completed

The deployed service provides these read-only tools:

- `get_discovery_scope`
- `list_proxmox_nodes`
- `list_proxmox_guests`
- `list_proxmox_storage`
- `get_proxmox_inventory`

Completed work includes Proxmox node/guest/storage inventory, storage capacity metrics, QEMU guest filesystem metrics through the Guest Agent, safe failure diagnostics, stable response semantics, and partial-failure handling.

The latest live validation confirmed all five tools working and the combined inventory returning no errors, including all six guests. Claude Code and Grok successfully queried the deployed Homelab MCP on 2026-11-19. Gemini successfully queried it on 2026-11-20 and returned a detailed report covering the Proxmox node, all six guests, and storage pools. Claude Code completed Webtools OAuth authorization, discovered/used the Proxmox tools, and produced a natural-language report from live inventory; Grok returned live node, guest, and storage details. These results validate the observed Release 1 client paths; they do not imply Docker inventory has been validated.

Important metric semantics:

- Proxmox node CPU is a fraction from 0 to 1, not a percentage.
- QEMU `disk` from cluster resources is not guest filesystem usage; use the separate `filesystems` data.
- Storage capacity uses Proxmox-reported total, used, and available values.
- Do not infer that a QEMU `disk: 0` means the virtual disk is empty.

## Release 2 — agreed architecture

Read [Release 2 — Docker read-only inventory](architecture/release-2-docker-inventory.md) before implementation.

The MCP will connect to exactly one Portainer Server on `util-pve`, through a dedicated internal Portainer infrastructure adapter. Portainer Server reaches its local Docker Engine and the four remote Docker Engines through Portainer Agents. The MCP must not connect directly to Agents or Docker sockets.

Initial environment allowlist:

| Environment | Proxmox guest | Connection through Portainer |
|---|---:|---|
| `util-pve` | VM 101 | Local Docker endpoint |
| `dev-pve` | VM 107 | Portainer Agent |
| `earn-pve` | VM 105 | Portainer Agent |
| `media-pve` | VM 106 | Portainer Agent |
| `ncloud-pve` | VM 108 | Portainer Agent |

`shared-pve` (VM 104) is intentionally excluded: it is a TurnKey File Server with no Docker Agent. It remains part of Proxmox inventory.

The planned MCP tools are:

- `list_docker_hosts`
- `list_docker_containers` — optional exact `hostKey` filter; omitted means all five hosts
- `list_docker_networks` — optional exact `hostKey` filter; omitted means all five hosts
- `get_docker_inventory` — combined all-host inventory

Internal inventory models must be independent of Portainer response DTOs. Environment IDs are discovered at runtime by the Portainer adapter and matched against the private server-side allowlist. One unavailable Agent must not suppress results from the other environments.

The inventory must expose only explicitly allowlisted fields. Never return container environment variables, secrets, labels, commands, mounts, logs, raw inspect payloads, or arbitrary Docker metadata. No write-capable Docker tools belong in Release 2.

## Release 2 — implementation and live validation

The core Docker inventory implementation is deployed on `util-pve` and has been successfully exercised through authenticated Claude Code and Grok MCP clients.

**Verified live behavior:**
- Portainer Server and the four Agents are on CE 2.45.4; the five approved environments reconnect and return Docker Engine information.
- The configured environment keys are `util-pve`, `dev-pve`, `earn-pve`, `media-pve`, and `ncloud-pve`. The Portainer local environment is named `util-pve`; endpoint IDs remain dynamically discovered.
- Container and network discovery return results from all five hosts. Claude Code's observed baseline was 43 containers: 39 running and four exited.
- The four exited containers in that baseline are intentional inventory states. Do not recommend restarting or deleting them, and do not treat their presence as an inventory failure.
- Container-side network attachment names are available. In the currently deployed build, network objects may have empty `connectedContainers`; restart count, StartedAt, and uptime are also not yet enriched.
- Both Claude Code and Grok returned `status: ok` and empty collection-error lists for the observed five-host validation.

**Current improvement branch:** `feat/docker-inventory-enrichment-and-host-filters`.

The branch adds bounded read-only container inspect enrichment, per-host network membership fallback, and optional exact `hostKey` filters for container and network list tools. These changes are not yet CI-verified, merged, or deployed. Do not claim those new fields or filter arguments are available in the live service until the PR passes CI, is merged, and the operator deploys the new image.

The adapter remains limited to fixed GET operations. The Portainer API key inherits its user's Administrator permissions; protect it as a high-impact credential. Do not add generic URL forwarding or any write-capable Docker operation.

## Portainer upgrade compatibility

**Completed upgrade recorded 2026-11-19:** Portainer Server on `util-pve` and the four remote Agents on `dev-pve`, `earn-pve`, `media-pve`, and `ncloud-pve` were upgraded to Portainer CE **2.45.4**. The Server was upgraded first, followed by the Agents. Afterward, all five Portainer environments were confirmed Up and reporting resources. Docker Engine versions observed during validation were 28.4.0 on the local `util-pve` environment and 29.1.3 on the four remote Agent-managed environments. The Portainer environment counts included intentionally stopped containers; stopped containers were not treated as an upgrade failure. This is an observed homelab validation, not a guarantee against future regressions.

PR #19 formalized the upgrade regression procedure in the Release 2 architecture. For each future supported upgrade:

- Record Server and Agent versions and the five environment connection states.
- Upgrade Server first, then Agents according to official compatibility guidance.
- Confirm all five environment identities and mappings.
- Run the same MCP integration tests and compare inventory semantics.
- Exercise one controlled Agent outage and recovery; other environments must continue returning data.
- Record results. A backward-compatible upgrade should not require MCP code changes; a breaking API change requires explicit adapter compatibility work and regression tests.

## AI client validation

**Claude Code — validated:** Claude Code successfully completed OAuth authorization against the Webtools Auth Server and connected to the deployed Homelab Management MCP. It used the Proxmox inventory tools and produced a natural-language summary from live inventory. It subsequently exercised the deployed Docker inventory tools across all five configured hosts, observing 43 containers (39 running, four intentionally exited) and no collection errors.

**Grok — validated 2026-11-19 and subsequently for Docker:** Grok successfully connected to the deployed Homelab MCP and queried live Proxmox inventory. It also exercised all four Docker inventory tools across the five configured hosts. The Docker report independently confirmed host availability, the expected application containers, and the same network-membership and container-detail gaps observed by Claude Code.

**Gemini — validated 2026-11-20:** Gemini successfully connected to the deployed Homelab MCP and queried live Proxmox inventory. Its response included a detailed breakdown of the Proxmox node, all six guests, and storage pools. This confirms the observed Gemini MCP/tool-execution path for Release 1. The Gemini test did not exercise Docker inventory tools.

Keep OAuth authorization, MCP initialization/tool discovery, and authenticated tool execution as distinct validation stages for future clients; a client registration alone is not proof of end-to-end operation. Do not infer compatibility for Gemini or other untested clients.

## Decisions and constraints

- Authenticate MCP requests through the existing Webtools OAuth boundary and central-user allowlist.
- Keep Release 2 read-only. No Docker lifecycle, exec, image, volume, stack, or network mutation operations.
- Do not mount `/var/run/docker.sock` into this service. A read-only socket mount is not a safe read-only API boundary.
- Do not store credentials, real runtime endpoints, private IPs, truststores, certificates, or passwords in this public repository.
- Keep source configuration free of environment-specific IP addresses and ports.
- Keep homelab and public/test configuration shapes similar where practical; explain any necessary environment-specific differences.
- Do not deploy, upgrade infrastructure, or change runtime configuration without explicit operator approval.
- Natural-language status thresholds, historical change detection, and concise/detailed reporting are follow-up work after Docker support.

## Recent milestones

- PR #18 — merged: Release 2 Docker inventory architecture.
- PR #19 — merged: Portainer upgrade compatibility validation.
- 2026-11-19 — ADR-0003 accepted: use a modular monolith with separate MCP inbound, Proxmox, Portainer, and inventory application/domain modules.
- 2026-11-19 — Homelab Portainer Proxy PR #5 closed unmerged; separate proxy approach abandoned.
- 2026-11-19 — Claude Code OAuth and live Proxmox MCP tool execution validated.
- 2026-11-19 — Grok connection and live Proxmox inventory query validated; Docker inventory was not tested.
- 2026-11-20 — Gemini connection and live Proxmox inventory query validated; Docker inventory was not tested.
- 2026-11-19 — Portainer Server and all four Agents upgraded to CE 2.45.4; all five environments reconnected.
- 2026-11-19 — Portainer client/configuration, response-size limiter, dynamic endpoint resolver, inventory mapping, application use case, and four Docker MCP tool registrations added.
- 2026-11-21 — Claude Code and Grok both validated the deployed Docker inventory across all five hosts; the observed baseline was 43 containers with four intentionally exited states.
- 2026-11-21 — Started `feat/docker-inventory-enrichment-and-host-filters`: bounded inspect enrichment, explicit health semantics, network membership fallback, and optional host-scoped container/network tools. CI and live validation remain pending.

## Recommended next work

1. Run Maven verify and inspect GitHub Actions CI for `feat/docker-inventory-enrichment-and-host-filters`; resolve all failures before merging.
2. Verify both legacy and modern MCP transports advertise the optional `hostKey` argument only on `list_docker_containers` and `list_docker_networks`.
3. After CI passes and the PR is merged, deploy only after explicit operator approval.
4. Validate host-scoped calls and full inventory through Claude Code and Grok. Compare inspect-enriched fields and network membership with Portainer.
5. Preserve the intentional exited-container baseline; do not add restart or cleanup suggestions.
6. Keep the Portainer CA truststore in the MCP runtime and all API credentials in the private Config Server.

## Starting a new engineering conversation

Read these files before proposing or changing implementation:

1. `docs/PROJECT-STATUS.md` — current status, open prerequisites, constraints, and next steps.
2. `CLAUDE.md` — mandatory engineering and security rules.
3. `README.md` — public project overview and verified release status.
4. `docs/architecture/release-2-docker-inventory.md` — Release 2 detailed design.
5. Relevant records under `docs/architecture/decisions/` — rationale behind accepted decisions.

Treat this repository as the project source of truth. Conversation history is supplementary and may be incomplete. Verify current code, merged PRs, CI, and actual deployment state before claiming anything changed or passed.
