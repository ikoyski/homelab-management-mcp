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

The MCP uses only Spring's default profile; it does not define separate `public` or `homelab` profiles.

- `application.yaml` contains bootstrap settings only: application name, Config Server import, and Config Server credentials.
- The Config Server serves the default configuration from `homelab-management-mcp.yaml`.
- The private Config Server owns runtime settings, including application/Actuator ports, endpoint exposure, authentication issuer/JWK/audience, allowed users, and discovery targets.

The Config Server URI and credentials are supplied through environment variables in deployment. For local development, the defaults point to `localhost:8089` with the local Config Server credentials.

Keep the Config Server credentials in the existing runtime env file and never commit their values. Keep the Config Server encryption key outside Git.

## Docker deployment (util-pve)

The Compose service is maintained in the `myhomelab` repository at
`pve/util-pve/docker-compose.yaml`. It runs as part of the Compose project
`utility`, on the `utility_default` network. It uses the default Spring
profile and fetches the default application configuration from Config Server.

After the Maven/Sonar job and Docker image publication complete successfully,
update the running container on `util-pve`:

```bash
cd ~/myhomelab/pve/util-pve

sudo docker compose pull homelab-management-mcp
sudo docker compose up -d --no-deps --force-recreate --pull never homelab-management-mcp
```

The `--no-deps` option leaves the other services in the `utility` project
untouched. The `--pull never` option makes the recreation use the image already
downloaded by the preceding `pull` command.

Verify the deployment:

```bash
sudo docker compose ps homelab-management-mcp
sudo docker compose logs --tail=80 homelab-management-mcp

# The service is internal to utility_default; no host port is published.
sudo docker run --rm --network utility_default curlimages/curl:latest \
  -fsS http://homelab-management-mcp:8082/actuator/health
```

The health endpoint should return `{"status":"UP"}`. The MCP Streamable HTTP
endpoint is `/mcp` on port `8083`. Actuator runs on the separate internal
management port `8082`; keep the Cloudflare Tunnel origin pointed only at
port `8083`. Neither container port should be published to the host.

## Diagram

<img width="2720" height="2200" alt="homelab_mcp_architecture_generic_names" src="https://github.com/user-attachments/assets/036c5c2f-4d8e-4fe4-a95e-a54609c7f89a" />

## Local development

Requires Java 21 and Maven 3.9+.

Run the application with the default profile and a local Config Server:

```bash
mvn spring-boot:run
```

The MCP application and Actuator ports, plus the authentication and discovery
settings, are obtained from the default Config Server configuration. Set
`CONFIG_SERVER_URI`, `CONFIG_SERVER_USERNAME`, and
`CONFIG_SERVER_PASSWORD` if your local Config Server differs from the defaults.

## Safety boundary

The initial discovery implementation must be strictly observational. Any future write-capable tools require separate design, explicit user confirmation, authorization checks, audit logging, and safeguards appropriate to the operation. Destructive actions must never be bundled into general discovery tools.
