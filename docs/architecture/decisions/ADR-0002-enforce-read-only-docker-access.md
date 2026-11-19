# ADR-0002: Enforce Read-Only Docker Inventory at the Server Boundary

- **Status:** Superseded by [ADR-0003](ADR-0003-modular-monolith-infrastructure-boundaries.md)
- **Date:** 2026-11-17
- **Related:** [Release 2 Docker inventory](../release-2-docker-inventory.md), [homelab-portainer-proxy](https://github.com/ikoyski/homelab-portainer-proxy)

## Context

Release 2 is intended to expose Docker inventory only. Portainer API tokens inherit the permissions of their associated Portainer user; an API token is not inherently read-only. Merely coding the MCP to send GET requests does not prevent a compromised or misconfigured client from reaching write-capable operations if the credential and network path permit them.

Portainer Community Edition capabilities must be checked for the exact supported version in use. The project must not assume that a standard user or API key can enforce all required read-only restrictions.

The earlier design proposed a separate `homelab-portainer-proxy` service. That service approach was abandoned in ADR-0003; the read-only boundary remains required but is now implemented inside the dedicated Portainer infrastructure adapter in the MCP.

## Decision

Before enabling the integration, establish and test a server-enforced least-privilege boundary.

1. Use a Portainer identity with the minimum permissions the installed edition can actually enforce, if those permissions are sufficient for every required inventory operation.
2. If that is not possible, enforce the read-only boundary in the MCP's dedicated Portainer adapter. It must expose only fixed inventory methods that issue GET requests, reject caller-controlled paths/URLs/endpoint IDs and arbitrary query parameters, and keep the Portainer credential private.
3. Keep TLS certificate and hostname verification enabled for the MCP-to-Portainer connection.
4. Do not mount the Docker socket into the MCP.

Do not describe the integration as securely read-only until the selected boundary has been implemented and tested.

## Consequences

- The read-only boundary is a release prerequisite, not an implementation convention.
- The Portainer adapter is an internal module with its own tests, configuration, TLS trust and credential boundary; it shares the MCP deployment lifecycle.
- Security tests must prove that write methods and unapproved paths/query parameters are rejected and that sensitive upstream fields are not returned.
- Inventory functionality must remain limited to explicitly allowlisted fields and operations.

## Rejected alternatives

- **Rely only on GET calls in application code:** rejected because it is not a server-enforced authorization boundary.
- **Mount Docker socket read-only:** rejected because a read-only filesystem mount does not make the Docker API safely read-only.
