# Changelog

Notable project changes are recorded here in reverse chronological order. This is a concise project-level history, not a replacement for Git commit history or release notes.

## 2026-11-22

### Merged, deployed, and validated — MCP guidance exposure
- PR #34 merged the rename to `get_homelab_capabilities`, the canonical AI-client guide as a read-only MCP resource (`homelab://guidance/ai-client`), the fallback `get_ai_client_guidance` tool, and provider-neutral server instructions. PR #35 aligned the guide's workflow wording with the renamed capabilities tool.
- The homelab Docker image was recreated. Startup logs confirmed Spring Boot loaded the private Config Server configuration and started the MCP (8083) and management (8082) HTTP servers.
- Claude, Gemini, and Grok each validated the capabilities response, retrieved the complete five-section guide through `get_ai_client_guidance`, confirmed the updated Section 2 workflow, and confirmed `get_discovery_scope` is absent from their available tool lists.
- None of the three tested client surfaces exposes an MCP resource-read operation. Therefore direct reads from `homelab://guidance/ai-client` remain unverified; this is a tested-client limitation, not evidence of a server failure. A resource-capable client or protocol-level test is still needed to validate `resources/list` and `resources/read`. The fallback-tool responses were not byte-compared with the resource content.

### Documentation
- Added provider-neutral `AI-CLIENT-GUIDANCE.md` covering privacy, inventory-first diagnostics, conservative state interpretation, optional standalone HTML reporting, untrusted-content handling, and the read-only boundary.
- Clarified the separation between MCP-facing AI-client guidance and repository-agent instructions. Repository continuity remains in `CLAUDE.md` and `docs/PROJECT-STATUS.md`; the redundant `GEMINI.md` file was removed, with Gemini CLI able to use `CLAUDE.md` through its context-file configuration.
- Updated the project handoff to record successful operator-directed inventory-to-report exercises through Claude, Gemini, and Grok. Report generation remains client-side; no MCP tool or server-side reporting feature was introduced.

## 2026-11-21

### Documentation
- Rewrote the public README and added end-to-end human setup guides for Proxmox, Docker/Portainer, private CA and Java truststore, Config Server/deployment, and AI-client validation.

### Merged and deployed
- PR #30 merged and deployed the dedicated read-only `get_docker_container_logs` tool. It resolves one exact configured host and discovered container name, defaults to 100 log lines (maximum 200), caps response bytes at 256 KiB, requests stdout/stderr with timestamps and `follow=false`, decodes Docker multiplexed and TTY output, and applies best-effort common-credential redaction. Live log retrieval was subsequently confirmed through Claude, Gemini, and Grok.
- PR #29 merged the bounded container inspect enrichment, explicit health-check semantics, same-host network membership fallback, and optional exact `hostKey` filters for container and network list tools. The updated image was deployed and host filtering was independently validated by Gemini.

### Validated
- Claude Code, Grok, and Gemini exercised the deployed Docker inventory across all five configured Portainer environments. Gemini validated both host-scoped filters against global inventory parity: 43 containers and 23 networks. Four exited containers are intentional and are not cleanup/restart recommendations.
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
