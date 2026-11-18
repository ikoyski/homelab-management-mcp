# ADR-0002: Enforce Read-Only Docker Inventory at the Server Boundary

- **Status:** Accepted
- **Date:** 2026-11-17
- **Related:** [Release 2 Docker inventory](../release-2-docker-inventory.md)

## Context

Release 2 is intended to expose Docker inventory only. Portainer API tokens inherit the permissions of their associated Portainer user; an API token is not inherently read-only. Merely coding the MCP to send GET requests does not prevent a compromised or misconfigured client from reaching write-capable operations if the credential and network path permit them.

Portainer Community Edition capabilities must be checked for the exact supported version in use. The project must not assume that a standard user or API key can enforce all required read-only restrictions.

## Decision

Before enabling the integration, establish and test a server-enforced least-privilege boundary.

1. Use a Portainer identity with the minimum permissions the installed edition can actually enforce, if those permissions are sufficient for every required inventory operation.
2. If that is not possible, put a private read-only API proxy between the MCP and Portainer. The proxy must expose only fixed inventory operations, allow only the required GET routes, reject unknown paths and query parameters, and keep the Portainer credential private.
3. Ensure the MCP has no alternate network path that bypasses the restricted proxy.
4. Keep TLS certificate and hostname verification enabled.
5. Do not mount the Docker socket into the MCP.

Do not describe the integration as securely read-only until the selected boundary has been tested.

## Consequences

- The read-only boundary is a release prerequisite, not an implementation convention.
- Additional infrastructure may be required if Portainer CE permissions are insufficient.
- Security tests must prove that write methods and unapproved paths are rejected.
- Inventory functionality must remain limited to explicitly allowlisted fields and operations.

## Rejected alternatives

- **Rely only on GET calls in application code:** rejected because it is not a server-enforced authorization boundary.
- **Mount Docker socket read-only:** rejected because a read-only filesystem mount does not make the Docker API safely read-only.
