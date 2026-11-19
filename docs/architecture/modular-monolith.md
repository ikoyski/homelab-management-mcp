# Modular Monolith Architecture

## Deployment shape

Homelab Management MCP remains one Spring Boot application, one container, and one deployment lifecycle. The modular monolith separates three independent network connections and the application responsibilities around them; it does not create another service.

```text
AI agents
   |  (1) inbound MCP: OAuth authentication + central-user authorization
   v
MCP inbound / tools
   |
   v
Inventory application use cases + application-owned inventory models
   |                                      |
   | (2) Proxmox port                    | (3) Portainer port
   v                                      v
Proxmox infrastructure adapter          Portainer infrastructure adapter
   |                                      |
   v                                      v
Proxmox VE API                          Portainer Server API
                                          |
                                          +-- local Docker Engine
                                          +-- Agent --> Docker Engine (dev-pve)
                                          +-- Agent --> Docker Engine (earn-pve)
                                          +-- Agent --> Docker Engine (media-pve)
                                          '-- Agent --> Docker Engine (ncloud-pve)
```

## Module responsibilities

| Module | Owns | Must not own |
|---|---|---|
| MCP inbound (`tools`, `transport`, `security`) | MCP transport, OAuth validation, central-user allowlist, tool schemas, input validation, response serialization | Proxmox/Portainer HTTP calls or upstream credentials |
| Proxmox infrastructure (`proxmox`) | Proxmox API client, token authentication, Proxmox paths, Proxmox response parsing and sanitized errors | Portainer client/configuration/DTOs or Docker inventory mapping |
| Portainer infrastructure (`portainer`) | Portainer API key, dedicated HTTP client, TLS trust via JVM truststore, response-size limit, endpoint discovery, exact host allowlist, fixed GET operations, Portainer response parsing | MCP transport concerns, Proxmox credentials/client/DTOs, application inventory contracts |
| Inventory application/domain (`inventory`) | Application-owned host/container/network/port models, inventory use cases, partial-result semantics | Portainer or Proxmox JSON types and transport-specific identifiers |
| Shared configuration/observability | Cross-cutting configuration and metrics only | Integration-specific behavior or a generic upstream HTTP proxy |

## Dependency direction

- MCP tool handlers call application use cases and serialize application results.
- Application use cases depend on narrow application ports/interfaces, not infrastructure implementations.
- Proxmox and Portainer adapters implement their own outbound ports and map upstream payloads into application-owned models.
- Proxmox and Portainer adapters never import or call one another.
- No infrastructure DTO or discovered Portainer endpoint ID crosses into MCP tool input/output.
- Do not create a generic HTTP proxy abstraction shared by Proxmox and Portainer; their authentication, APIs, and trust policies are intentionally distinct.

## Portainer read-only boundary

The Portainer adapter exposes only fixed methods for endpoint discovery, Docker info, container listing, and network listing. Each upstream request is a code-defined GET. Do not add arbitrary URL/path/query forwarding, caller-provided endpoint IDs, generic HTTP methods, or write operations.

The adapter resolves endpoint IDs dynamically from the Portainer endpoint list and matches exact endpoint names to the configured allowlist. The allowlist is exactly `util-pve`, `dev-pve`, `earn-pve`, `media-pve`, and `ncloud-pve`; `shared-pve` is excluded. Duplicate names are ambiguous, invalid IDs are rejected, and missing environments are reported without selecting another endpoint.

Portainer credentials are supplied only through private runtime configuration and are never available to MCP tool handlers. Use the runtime JVM truststore for the self-signed certificate while preserving normal certificate-chain and hostname/IP validation. Apply bounded connect/read timeouts and response-size limits. Never log API keys, authorization headers, response bodies, or sensitive Docker metadata.

## Failure isolation

Each upstream connection has independent configuration, timeouts, credentials, client, error mapping, and tests. Proxmox failures must not affect Portainer collection, and Portainer environment failures must not suppress healthy environments. Combined inventory is a best-effort collection, not an atomic cross-host snapshot.

## Extraction threshold

Keep Portainer inside the modular monolith while it has one consumer and shares the MCP deployment lifecycle. Reconsider extraction into a separate service only if there is a real second consumer, independent scaling need, separate operational ownership, or a process/network isolation requirement that cannot be met inside the MCP.

## Current implementation note

The existing Proxmox integration remains in its established package and continues to serve Release 1. The Portainer client/configuration/response-limit/endpoint-resolution foundation and application-owned Docker inventory records are being introduced incrementally. The remaining work is to connect those foundations through an application use case and Portainer adapter, expose MCP tools, complete integration/security tests, and configure the private runtime values. Do not claim Release 2 is complete until those steps and live validation are done.

See [ADR-0003](decisions/ADR-0003-modular-monolith-infrastructure-boundaries.md) for the accepted architecture decision.