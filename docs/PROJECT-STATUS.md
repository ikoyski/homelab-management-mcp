# Project Status and Handoff

> **Purpose:** Canonical continuity record for maintainers and AI coding assistants. Update this file whenever a milestone, architecture decision, validation result, or deployment state changes.
>
> **Last updated:** 2026-11-19 (Asia/Manila)
> **Repository:** `ikoyski/homelab-management-mcp`
> **Current focus:** Release 2 — Docker read-only inventory

## Current project state

| Area | Status | Notes |
|---|---|---|
| Release 1 — Proxmox inventory | Implemented and deployed | Five MCP tools; live inventory was exercised through an authenticated MCP client. |
| Release 2 — Docker inventory | Architecture documented; implementation not started | PR #18 merged the architecture; PR #19 merged Portainer upgrade-compatibility requirements. |
| Portainer Server/Agent upgrade | Completed and validated | Server and all four Agents are on CE 2.45.4; all five configured environments reconnected and reported healthy. |
| Enforceable read-only Portainer access | Open implementation prerequisite | Portainer CE token permissions are not assumed to be sufficiently restrictive. Implement and test the fixed-operation private read-only proxy before connecting the MCP. |
| Portainer TLS trust | Open deployment prerequisite | Provision and verify Java trust for the self-signed Portainer certificate; retain certificate-chain and hostname verification. |
| Natural-language status/reporting improvements | Deferred | Revisit after Docker support is complete. |

## Release 1 — completed

The deployed service provides these read-only tools:

- `get_discovery_scope`
- `list_proxmox_nodes`
- `list_proxmox_guests`
- `list_proxmox_storage`
- `get_proxmox_inventory`

Completed work includes Proxmox node/guest/storage inventory, storage capacity metrics, QEMU guest filesystem metrics through the Guest Agent, safe failure diagnostics, stable response semantics, and partial-failure handling.

The latest live validation confirmed all five tools working and the combined inventory returning no errors, including all six guests. Claude Code has now successfully completed the Webtools OAuth flow, connected to the Homelab MCP, discovered/used its tools, and produced a natural-language report from live Proxmox inventory data. This validates the Claude Code client path; it does not imply Docker inventory is implemented.

Important metric semantics:

- Proxmox node CPU is a fraction from 0 to 1, not a percentage.
- QEMU `disk` from cluster resources is not guest filesystem usage; use the separate `filesystems` data.
- Storage capacity uses Proxmox-reported total, used, and available values.
- Do not infer that a QEMU `disk: 0` means the virtual disk is empty.

## Release 2 — agreed architecture

Read [Release 2 — Docker read-only inventory](architecture/release-2-docker-inventory.md) before implementation.

The MCP will connect to exactly one Portainer Server on `util-pve`. Portainer Server reaches its local Docker Engine and the four remote Docker Engines through Portainer Agents. The MCP must not connect directly to Agents or Docker sockets.

Initial environment allowlist:

| Environment | Proxmox guest | Connection through Portainer |
|---|---:|---|
| `util-pve (local)` | VM 101 | Local Docker endpoint |
| `dev-pve` | VM 107 | Portainer Agent |
| `earn-pve` | VM 105 | Portainer Agent |
| `media-pve` | VM 106 | Portainer Agent |
| `ncloud-pve` | VM 108 | Portainer Agent |

`shared-pve` (VM 104) is intentionally excluded: it is a TurnKey File Server with no Docker Agent. It remains part of Proxmox inventory.

The planned MCP tools are:

- `list_docker_hosts`
- `list_docker_containers`
- `list_docker_networks`
- `get_docker_inventory`

Internal inventory DTOs must be independent of Portainer response DTOs. Environment IDs are discovered at runtime and matched against a server-side allowlist. One unavailable Agent must not suppress results from the other environments.

The inventory must expose only explicitly allowlisted fields. Never return container environment variables, secrets, labels, commands, mounts, logs, raw inspect payloads, or arbitrary Docker metadata. No write-capable Docker tools belong in Release 2.

## Release 2 — prerequisites and implementation status

1. **Completed:** Upgrade Portainer Server first, then all Agents, to the compatible Portainer CE 2.45.4 release. All five environments were verified Up and reporting resources after the upgrade.
2. **Open:** Implement and test a genuinely enforceable read-only access boundary. A token used only with GET requests is not sufficient by itself. Portainer CE token permissions are not assumed to provide the required restriction; use a private fixed-operation read-only proxy and ensure the MCP cannot bypass it.
3. **Open:** Provision and verify Java trust for the self-signed Portainer certificate, preserving normal certificate-chain and hostname/IP verification.
4. After the read-only boundary and TLS trust are verified, implement and enable the Portainer adapter.

## Portainer upgrade compatibility

**Completed upgrade recorded 2026-11-19:** Portainer Server on `util-pve` and the four remote Agents on `dev-pve`, `earn-pve`, `media-pve`, and `ncloud-pve` were upgraded to Portainer CE **2.45.4**. The Server was upgraded first, followed by the Agents. Afterward, all five Portainer environments were confirmed Up and reporting resources. Docker Engine versions observed during validation were 28.4.0 on the local `util-pve` environment and 29.1.3 on the four remote Agent-managed environments. The Portainer environment counts included intentionally stopped containers; stopped containers were not treated as an upgrade failure. This is an observed homelab validation, not a guarantee against future regressions.

PR #19 formalized the upgrade regression procedure in the Release 2 architecture. For each future supported upgrade:

- Record Server and Agent versions and the five environment connection states.
- Upgrade Server first, then Agents according to official compatibility guidance.
- Confirm all five environment identities and mappings.
- Run the same MCP integration tests and compare inventory semantics.
- Exercise one controlled Agent outage and recovery; other environments must continue returning data.
- Record results. A backward-compatible upgrade should not require MCP code changes; a breaking API change requires explicit adapter compatibility work and regression tests.

## Claude Code client validation

**Validated 2026-11-19:** Claude Code successfully completed OAuth authorization against the Webtools Auth Server and connected to the deployed Homelab Management MCP. It was able to use the Proxmox inventory tools and produce a natural-language summary from the returned live inventory. This confirms the Claude Code OAuth/client integration path is working. Keep OAuth authorization, MCP initialization/tool discovery, and authenticated tool execution as distinct validation stages for future clients; a client registration alone is not proof of end-to-end operation.

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
- Current documentation work — establish canonical project status, decision records, and changelog.

## Recommended next work

1. Complete the Portainer security upgrade and record verified versions.
2. Prove the read-only authorization boundary and Portainer TLS trust from the MCP runtime network.
3. Implement the Portainer transport behind an application-owned interface, with fixture-based tests and sanitized error handling.
4. Implement environment discovery and allowlist matching, then `list_docker_hosts`.
5. Add container, network, and combined inventory incrementally.
6. Validate through an authenticated MCP client; deploy only after tests pass and the operator explicitly approves.

## Starting a new engineering conversation

Read these files before proposing or changing implementation:

1. `docs/PROJECT-STATUS.md` — current status, open prerequisites, constraints, and next steps.
2. `CLAUDE.md` — mandatory engineering and security rules.
3. `README.md` — public project overview and verified release status.
4. `docs/architecture/release-2-docker-inventory.md` — Release 2 detailed design.
5. Relevant records under `docs/architecture/decisions/` — rationale behind accepted decisions.

Treat this repository as the project source of truth. Conversation history is supplementary and may be incomplete. Verify current code, merged PRs, CI, and deployment state before claiming anything changed or passed.
