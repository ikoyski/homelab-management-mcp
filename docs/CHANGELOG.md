# Changelog

Notable project changes are recorded here in reverse chronological order. This is a concise project-level history, not a replacement for Git commit history or release notes.

## 2026-11-19

### Validated
- Claude Code successfully completed Webtools OAuth authorization, connected to the deployed Homelab Management MCP, discovered and used the Proxmox inventory tools, and generated a natural-language report from live inventory data.
- Upgraded Portainer Server on `util-pve` and all four remote Portainer Agents (`dev-pve`, `earn-pve`, `media-pve`, `ncloud-pve`) to Portainer CE 2.45.4. All five environments reconnected and were confirmed Up/reporting resources.
- Docker Engine versions observed during the Portainer upgrade validation: 28.4.0 on local `util-pve`; 29.1.3 on the four remote Agent-managed environments.

### Added
- Created the separate [homelab-portainer-proxy](https://github.com/ikoyski/homelab-portainer-proxy) repository with an initial Java/Spring Boot skeleton, project documentation, and a proposed fixed-operation read-only proxy ADR. Its README reports a successful initial local Maven build.

### Still outstanding for Release 2
- Proxy functionality and the server-enforced read-only boundary are not implemented or verified. The proxy must expose only fixed inventory operations, reject non-GET methods, unknown paths and unapproved query parameters, and prevent MCP-to-Portainer bypass.
- Java trust for the self-signed Portainer certificate must be provisioned and verified without disabling TLS certificate or hostname checks.
- Docker inventory adapter and MCP tool implementation have not started.

## 2026-11-17

### Added
- Release 2 Docker read-only inventory architecture (PR #18).
- Portainer Server/Agent upgrade compatibility validation procedure (PR #19).
- Canonical project status and handoff documentation.
- Architecture decision records for the Portainer adapter boundary and read-only security requirements.

### Status
- Release 1 Proxmox inventory is implemented and deployed; authenticated MCP validation has been observed for all five Proxmox tools.
- Release 2 remains in architecture/prerequisite stage; no Docker inventory implementation or deployment is claimed.
