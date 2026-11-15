# Homelab Management MCP

A read-only-first Model Context Protocol (MCP) server for discovering and monitoring services across a private homelab.

The source repository is public. Runtime configuration, credentials, private keys, host addresses, and other sensitive infrastructure details must remain outside this repository.

## Current status

**Bootstrap phase.** The Spring Boot application, MCP HTTP transport, health endpoint, configuration-server integration, and initial discovery-scope tool are being established. Remote Proxmox and Docker discovery are not implemented yet.

## Design principles

- Read-only discovery by default.
- No start, stop, restart, update, pull, delete, or configuration mutation tools in the initial release.
- Never return environment-variable values, secrets, tokens, private keys, or credential material.
- A failed or unreachable target must not prevent reporting results from other targets.
- Keep infrastructure-specific values in the private Webtools Config Server or runtime environment, never in Git.
- Restrict network access to trusted homelab/Tailscale paths and require explicit authentication and authorization before exposing MCP to remote AI clients.
- Do not mount the Docker socket into the MCP container. Docker access will use a separately secured, read-only remote access method.

## Planned discovery scope

Initial known Docker hosts (configured outside this repository):

| Target | Address | Role |
|---|---|---|
| dev-pve | private configuration | Development and Webtools services |
| util-pve | private configuration | Utilities, monitoring, automation, MCP |
| media-pve | private configuration | Media and Arr stack |
| earn-pve | to be discovered | Unknown |
| ncloud-pve | to be discovered | Unknown |

The target names above are inventory labels, not connection details. Actual endpoints and access credentials belong in private configuration.

## Technology

- Java 21
- Spring Boot
- Spring Cloud Config Client
- Model Context Protocol Java SDK
- HTTP Streamable MCP transport
- Spring Boot Actuator

## Configuration

The application imports its private configuration from Spring Cloud Config Server. Supply connection settings through environment variables:

| Variable | Purpose |
|---|---|
| `CONFIG_SERVER_URI` | Internal Config Server base URL |
| `CONFIG_SERVER_USERNAME` | Config Server Basic Auth username |
| `CONFIG_SERVER_PASSWORD` | Config Server Basic Auth password |
| `SPRING_PROFILES_ACTIVE` | Runtime profile, default `homelab` |

Do not commit actual values. Keep encrypted properties in the private Config Server repository and keep the Config Server encryption key outside Git.

The remote configuration can define the non-sensitive target labels:

```yaml
homelab:
  discovery:
    mode: read-only
    targets:
      - dev-pve
      - util-pve
      - media-pve
```

Add `earn-pve` and `ncloud-pve` after their workloads and access paths have been discovered.

## Diagram

<img width="2720" height="2200" alt="homelab_mcp_architecture_generic_names" src="https://github.com/user-attachments/assets/036c5c2f-4d8e-4fe4-a95e-a54609c7f89a" />

## Local development

Requires Java 21 and Maven 3.9+.

```bash
mvn spring-boot:run
```

For local development without Config Server, activate the `local` profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

By default, the application listens on port `8083` (overridable with the `PORT` environment variable). The MCP endpoint is `/mcp`; actuator health is available at `/actuator/health`.

## Safety boundary

The initial discovery implementation must be strictly observational. Any future write-capable tools require separate design, explicit user confirmation, authorization checks, audit logging, and safeguards appropriate to the operation. Destructive actions must never be bundled into general discovery tools.
