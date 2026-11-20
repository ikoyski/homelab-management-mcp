# ADR-0001: Use Portainer as the Initial Docker Transport

- **Status:** Accepted
- **Date:** 2026-11-17
- **Related:** [Release 2 Docker inventory](../release-2-docker-inventory.md)

## Context

The deployment uses one Portainer Server to manage its local Docker Engine and remote Docker Engines through Portainer Agents. The MCP needs a maintainable way to discover Docker hosts, containers, and networks across the configured environments.

Direct connections from the MCP to every Docker Engine or Agent would spread network access and transport-specific behavior through the application. Coupling the public inventory contract to Portainer response objects would also make a future transport replacement unnecessarily invasive.

## Decision

Use the single Portainer Server API as the initial discovery and transport layer. The MCP connects only to the Portainer Server. Portainer Server communicates with the local Engine and remote Agents.

Keep the Docker inventory domain model, application services, and MCP response DTOs owned by this application. Isolate Portainer API calls and response mapping in an infrastructure adapter. Do not expose Portainer endpoint IDs as stable public identifiers.

The configured environment allowlist is maintained in private runtime configuration. Only exact environment identities present in that allowlist are discovered; unlisted environments are excluded. The public repository does not enumerate environment names or mappings.

## Consequences

- Only the Portainer Server is a Docker transport network dependency of the MCP.
- Portainer API response changes are isolated behind the adapter and covered by fixtures/tests.
- A future transport can replace Portainer without redesigning the inventory domain model.
- The Portainer Server/Agent versions and API compatibility must be validated.
- Portainer's use does not itself establish read-only authorization; that is a separate security prerequisite.

## Rejected alternatives

- **Direct Docker Engine / Agent connections:** rejected because they multiply MCP network dependencies and transport coupling.
- **Portainer DTOs as the application model:** rejected because they couple tool contracts to Portainer implementation details.
