# ADR-0003: Keep Infrastructure Integrations in a Modular Monolith

- **Status:** Accepted
- **Date:** 2026-11-19
- **Supersedes:** The separate Portainer proxy deployment approach in ADR-0002
- **Related:** [ADR-0001](ADR-0001-portainer-as-initial-docker-transport.md), [Release 2 Docker inventory](../release-2-docker-inventory.md)

## Context

Homelab Management MCP has three distinct trust and communication boundaries:

1. AI agents connect inbound to the MCP over authenticated MCP transport.
2. The MCP connects outbound to the Proxmox VE API for node, guest, and storage inventory.
3. The MCP connects outbound to one Portainer Server for Docker inventory across the local Engine and Agent-managed Engines.

The earlier Release 2 design proposed a separately deployed Homelab Portainer Proxy to enforce fixed read-only operations. That would add another service, network hop, credential boundary, deployment lifecycle, and operational surface even though the MCP is currently the only intended consumer.

The codebase is a single Spring Boot application and can keep these responsibilities separated through module/package boundaries without separate deployment units.

## Decision

Keep the system as one deployable **modular monolith**. Implement the Portainer read-only boundary inside Homelab Management MCP, alongside (but independent from) the existing Proxmox integration.

Use these logical modules:

- **MCP inbound module:** MCP transport, OAuth resource-server authentication, central-user authorization, tool input validation, tool registration, and response serialization.
- **Proxmox infrastructure module:** Proxmox configuration, dedicated HTTP client, API operations, Proxmox response parsing, and Proxmox-specific error mapping.
- **Portainer infrastructure module:** Portainer configuration, dedicated HTTP client, TLS trust, response-size limits, API-key handling, dynamic endpoint discovery, environment allowlisting, fixed GET-only operations, Portainer response parsing, and Portainer-specific error mapping.
- **Inventory application/domain module:** application-owned use cases and host/container/network inventory models. It must not depend on Portainer or Proxmox transport DTOs.
- **Shared configuration/observability module:** only genuinely cross-cutting concerns; do not turn it into a dumping ground for integration-specific behavior.

The Proxmox and Portainer modules must not depend on one another. Their credentials, HTTP clients, DTOs, error types, and configuration properties remain separate. The MCP inbound layer calls application use cases and must not make upstream HTTP requests itself.

The Portainer adapter may issue only fixed, code-defined GET operations required for inventory. It must not expose generic URL/path forwarding, arbitrary query parameters, caller-supplied Portainer endpoint IDs, or write-capable methods. Resolve endpoint IDs dynamically from Portainer and match exact configured environment identities against the private allowlist. Do not hardcode deployment-specific names or mappings in this public repository; unlisted environments are excluded.

The Portainer API key is held only in private runtime configuration. Keep TLS certificate-chain and hostname/IP verification enabled, use the runtime JVM truststore for the self-signed certificate, and bound connection/read timeouts and response sizes. Map only explicitly allowlisted fields into application-owned inventory models. Isolate failures per environment.

## Consequences

- One application and deployment unit; no separate Portainer Proxy service or MCP-to-proxy shared token.
- The MCP has two independent outbound infrastructure adapters: Proxmox and Portainer.
- Security boundaries remain explicit and testable inside the application.
- Portainer integration can later be extracted into a separate service if multiple consumers, independent scaling, or a stronger process/network isolation requirement justifies it.
- A future replacement of Portainer changes the infrastructure adapter, not the inventory domain or MCP tool contract.
- Tests must prove fixed-operation read-only behavior, endpoint allowlisting, TLS verification assumptions, response-size limits, sensitive-field exclusion, and per-environment failure isolation.

## Rejected alternatives

- **Separate Portainer Proxy now:** rejected because there is one intended consumer and the extra service boundary adds operational complexity without a current reuse requirement.
- **Put Portainer HTTP logic in MCP tool handlers:** rejected because it mixes transport, authorization, application orchestration, and infrastructure concerns.
- **Share a generic HTTP client or DTOs between Proxmox and Portainer:** rejected because the APIs have different authentication, response semantics, and trust boundaries.
- **Rely only on the convention that code sends GET requests:** rejected; the Portainer adapter itself must expose only fixed GET operations and have no generic forwarding capability.