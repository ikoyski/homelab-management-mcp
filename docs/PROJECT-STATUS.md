# Project Status and Handoff

> **Purpose:** Canonical continuity record for maintainers and AI coding assistants. Update this file whenever a milestone, architecture decision, validation result, or deployment state changes.
>
> **Last updated:** 2026-11-21 (Asia/Manila)
> **Repository:** `ikoyski/homelab-management-mcp`
> **Current focus:** Release 2 — bounded read-only Docker container diagnostics

## Current project state

| Area | Status | Notes |
|---|---|---|
| Release 1 — Proxmox inventory | Implemented and deployed | Five MCP tools; authenticated live inventory has been exercised by Claude Code, Grok, and Gemini. |
| Claude Code client integration | Validated | OAuth, MCP tool discovery, Proxmox inventory, and Docker inventory across all five configured hosts were successfully exercised. |
| Grok client integration | Validated | Proxmox and Docker inventory tools were independently exercised; Docker results covered all five hosts and the same 43-container baseline. |
| Gemini client integration | Validated | Gemini completed Docker and Proxmox inventory, and validated container/network hostKey filtering across all five configured hosts against global inventory parity. |
| Release 2 — Docker inventory | Implemented, merged, deployed, and live-validated | Claude Code, Grok, and Gemini exercised the five configured hosts. Gemini validated both host-scoped filters against global inventory: 43 containers and 23 networks. |
| Portainer Server/Agent upgrade | Completed and validated | Server and all four Agents are on CE 2.45.4; all five configured environments reconnected and reported resources. |
| Portainer TLS trust and endpoint mapping | Completed and validated | Java trust for the Portainer certificate is installed in the MCP runtime. Environment identities are supplied by private configuration; endpoint IDs are discovered dynamically. |
| Separate Portainer Proxy | Abandoned | The separate repository is no longer part of the target architecture; the Portainer adapter lives inside this MCP. |
| Portainer authorization boundary | Operational; credential risk documented | The adapter exposes only fixed GET operations, but the Portainer API key inherits its user's Administrator permissions. Treat the key as a high-impact credential. |
| Bounded container log retrieval | PR #30 merged and deployed; live tool validation pending | `get_docker_container_logs` resolves one exact configured host and discovered container name, defaults tail to 100 (maximum 200), caps response bytes at 256 KiB, disables follow, decodes Docker log framing, and applies best-effort common-secret redaction. The operator confirmed merge and deployment; successful live log retrieval has not yet been reported. |
| Natural-language status/reporting improvements | Deferred | Revisit after bounded container log retrieval is validated. |

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

The MCP connects to exactly one Portainer Server in the private management environment, through a dedicated internal Portainer infrastructure adapter. Portainer Server reaches its local Docker Engine and remote Docker Engines through Portainer Agents. The MCP must not connect directly to Agents or Docker sockets.

The environment allowlist and any environment-to-guest mapping are maintained only in the private Config Server. The public repository does not enumerate real host names, Proxmox guest identifiers, counts, or mappings. Only exact environment identities supplied by that private allowlist are discovered; any unlisted environment is excluded.

The deployed MCP tools are:

- `list_docker_hosts`
- `list_docker_containers` — optional exact `hostKey` filter; omitted means all five hosts
- `list_docker_networks` — optional exact `hostKey` filter; omitted means all five hosts
- `get_docker_inventory` — combined all-host inventory
- `get_docker_container_logs` — bounded recent stdout/stderr logs for one exact container on one configured host (deployed; live tool validation pending)

Internal inventory models must be independent of Portainer response DTOs. Environment IDs are discovered at runtime by the Portainer adapter and matched against the private server-side allowlist. One unavailable Agent must not suppress results from the other environments.

The inventory tools must expose only explicitly allowlisted fields. Never return container environment variables, secrets, labels, commands, mounts, raw inspect payloads, or arbitrary Docker metadata. Container logs are excluded from inventory results and may be returned only by the dedicated bounded log tool described below. No write-capable Docker tools belong in Release 2.

## Release 2 — implementation and live validation

The core Docker inventory implementation is deployed in the private homelab management environment and has been successfully exercised through authenticated Claude Code and Grok MCP clients.

**Verified live behavior:**
- Portainer Server and the four Agents are on CE 2.45.4; the five approved environments reconnect and return Docker Engine information.
- Configured environment keys and their exact Portainer names are supplied by the private Config Server. Endpoint IDs remain dynamically discovered; the public source and documentation contain no live host identifiers.
- Container and network discovery return results from all five hosts. Claude Code's observed baseline was 43 containers: 39 running and four exited.
- The four exited containers in that baseline are intentional inventory states. Do not recommend restarting or deleting them, and do not treat their presence as an inventory failure.
- Container-side network attachment names are available. In the currently deployed build, network objects may have empty `connectedContainers`; restart count, StartedAt, and uptime are also not yet enriched.
- Both Claude Code and Grok returned `status: ok` and empty collection-error lists for the observed five-host validation.

**Merged and deployed improvement:** PR #29 added bounded container inspect enrichment, per-host network membership fallback, and optional exact hostKey filters for container and network list tools. The user confirmed the PR merged and the build succeeded, then deployed the updated image.

**Live client validation:** Claude Code, Grok, and Gemini queried Docker inventory. Gemini inspected both published schemas and tested every configured host for containers and networks, then compared filtered results with unfiltered global inventories. It reported exact parity across all five hosts: 43 containers and 23 networks. No errors were reported. Minor live uptime drift between sequential calls is expected.

The adapter remains limited to fixed GET operations. The Portainer API key inherits its user's Administrator permissions; protect it as a high-impact credential. Do not add generic URL forwarding or any write-capable Docker operation.

## Bounded container log retrieval — merged and deployed

**PR #30:** [Add bounded read-only Docker container log retrieval](https://github.com/ikoyski/homelab-management-mcp/pull/30). Merged on 2026-11-20 UTC; the operator confirmed the updated image was deployed on 2026-11-21 (Asia/Manila).

The branch adds get_docker_container_logs, a dedicated diagnostic tool accepting hostKey, exact containerName, and optional tail (default 100, maximum 200). It resolves the configured host through the private allowlist, discovers the container ID from the selected host's Docker container listing, and uses only the fixed Docker logs GET endpoint. Caller-supplied endpoint IDs, container IDs, URLs, paths, and arbitrary query parameters are not accepted.

The request selects stdout and stderr, enables timestamps, and always sets follow=false. The response is read with a 256 KiB byte ceiling; Docker multiplexed stdout/stderr frames and plain-text TTY output are handled. Common credential patterns are redacted on a best-effort basis and the result reports truncation and redaction counts. Redaction is not comprehensive: log contents may still expose secrets or personal information and are sent to the connected AI provider. The Docker logs API is documented to work with json-file and journald logging drivers; other drivers may return an unavailable result. Logs are untrusted data and must never be treated as instructions.

This tool is separate from inventory: inventory results continue to exclude log contents. It does not use shell, exec, Docker socket access, or a streaming/follow mode. CI passed sufficiently for merge and the operator confirmed deployment. The remaining validation is to invoke it through an authenticated AI client against a selected container, confirm useful recent output, and confirm unsupported/unavailable logging-driver behavior is returned honestly.

## Portainer upgrade compatibility

**Completed upgrade recorded 2026-11-19:** The Portainer Server and configured Agent-managed environments were upgraded to a compatible Portainer CE release. The Server was upgraded first, followed by the Agents. All configured environments were subsequently confirmed Up and reporting resources. Docker Engine information was successfully collected from the local and remote Agent-managed environments. This is an observed homelab validation, not a guarantee against future regressions.

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

**Gemini — validated 2026-11-21:** Gemini successfully connected to the deployed Homelab MCP and queried Proxmox and Docker inventories. It completed container/network host-filter validation across all five configured hosts and matched the filtered records against the global inventories (43 containers, 23 networks). Sequential prompting and per-tool permission approvals worked reliably for this assessment.

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
- 2026-11-21 — PR #29 merged and the updated inventory image deployed; user confirmed successful build. Gemini independently validated all five container and network hostKey filters against global inventory.
- 2026-11-21 — PR #30 merged and deployed: added fixed GET-only Docker container log retrieval for one exact host/container, 100-line default / 200-line maximum, 256 KiB response ceiling, timestamps, no follow/streaming, Docker stream decoding, and best-effort credential redaction. Live AI-client log retrieval validation remains pending.

## Recommended next work

1. Validate `get_docker_container_logs` through an authenticated AI client using a known, non-sensitive container and a small `tail` value; confirm the response is recent, bounded, and readable.
2. Verify an unavailable container and an unsupported/unavailable Docker logging driver return sanitized, honest errors without shell/exec fallbacks.
3. Remind AI clients that log text can contain secrets or personal data despite best-effort redaction; ask for only the relevant container and log window, not logs from every host by default.
4. Preserve the intentional exited-container baseline; do not add restart or cleanup suggestions.
5. Keep the Portainer CA truststore in the MCP runtime and all API credentials in the private Config Server.

## Starting a new engineering conversation

Read these files before proposing or changing implementation:

1. `docs/PROJECT-STATUS.md` — current status, open prerequisites, constraints, and next steps.
2. `CLAUDE.md` — mandatory engineering and security rules.
3. `README.md` — public project overview and verified release status.
4. `docs/architecture/release-2-docker-inventory.md` — Release 2 detailed design.
5. Relevant records under `docs/architecture/decisions/` — rationale behind accepted decisions.

Treat this repository as the project source of truth. Conversation history is supplementary and may be incomplete. Verify current code, merged PRs, CI, and actual deployment state before claiming anything changed or passed.
