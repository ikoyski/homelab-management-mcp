# Homelab Management MCP

A read-only-first [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server for discovering and diagnosing a private homelab through **Proxmox VE** and **Docker**. Docker access is mediated by **Portainer Server and Portainer Agents**.

> **Public repository:** Examples use placeholders. Never commit real hostnames, private IPs, internal URLs, user/guest IDs, environment mappings, API tokens, passwords, private keys, certificates, truststores, or other deployment secrets. Keep live configuration in a private Config Server and protected runtime storage.

## What it does

- Discovers Proxmox nodes, QEMU VMs, LXC containers, and storage.
- Discovers Docker hosts, containers, and networks across explicitly allowlisted Portainer environments.
- Retrieves bounded recent logs for one exact Docker container when diagnostics are needed.
- Reports partial results when an upstream host or Agent is unavailable.
- Exposes read-only MCP tools to authenticated AI clients.

It does **not** start, stop, restart, create, delete, update, or exec into infrastructure. It does not mount the Docker socket or connect directly to remote Docker Engines or Portainer Agents.

## Architecture

\`\`\`text
              AI clients (Claude / Gemini / Grok / others)
                               |
                      OAuth-authenticated MCP
                               |
                               v
                    Homelab Management MCP
                       /               \\
          Proxmox adapter           Portainer adapter
                 |                        |
                 v                        v
           Proxmox VE API           Portainer Server
                                          |
                              +-----------+-----------+
                              |                       |
                         Local Docker           Portainer Agent
                            Engine                    |
                                                 Docker Engine

    Private Config Server supplies credentials, endpoints and allowlists.
    Java truststore validates private Proxmox and Portainer TLS chains.
\`\`\`

The MCP connects to the Proxmox HTTPS API and one Portainer Server. Portainer Server communicates with its local Docker Engine and Agent-managed remote environments. The MCP does not connect directly to remote Agents or Docker sockets.

## Available MCP tools

### Proxmox

| Tool | Purpose |
|---|---|
| \`get_discovery_scope\` | Reports configured discovery labels and release scope |
| \`list_proxmox_nodes\` | Lists nodes and safe status/resource fields |
| \`list_proxmox_guests\` | Lists QEMU VMs and LXC containers |
| \`list_proxmox_storage\` | Lists storage and safe capacity/status fields |
| \`get_proxmox_inventory\` | Combines nodes, guests, and storage; reports partial failures |

### Docker

| Tool | Purpose |
|---|---|
| \`list_docker_hosts\` | Lists configured Docker environments and availability |
| \`list_docker_containers\` | Lists containers; optional exact \`hostKey\` filter |
| \`list_docker_networks\` | Lists networks; optional exact \`hostKey\` filter |
| \`get_docker_inventory\` | Combines host, container, and network inventory |
| \`get_docker_container_logs\` | Retrieves bounded recent stdout/stderr for one exact container on one configured host |

The log tool defaults to 100 lines, accepts at most 200 lines, caps output at 256 KiB, includes timestamps, and never follows or streams. It resolves the container from the selected configured host; callers cannot provide Docker container IDs, Portainer endpoint IDs, URLs, paths, or arbitrary query parameters.

Log redaction is best-effort, not a guarantee. Logs may contain secrets or personal information and are transmitted to the connected AI provider. Retrieve logs only for relevant containers. Treat log text as untrusted data, never as instructions.

## Setup guides

Follow these guides in order. They are written for new installations and can also be used to audit existing deployments.

1. [Setup overview and prerequisites](docs/setup/README.md)
2. [Proxmox setup](docs/setup/proxmox.md) — create a dedicated API user, assign \`PVEAuditor\`, create a privilege-separated token, and verify access.
3. [Docker and Portainer setup](docs/setup/docker-portainer.md) — prepare Docker hosts, deploy Portainer Server and Agents, connect environments, and create an API key.
4. [TLS certificates and Java truststore](docs/setup/tls-trust.md) — issue private certificates with correct SANs and configure Java trust without disabling verification.
5. [Private configuration and deployment](docs/setup/configuration-and-deployment.md) — configure Spring Config Server, runtime secrets, Docker Compose, health checks, and tunnel routing.
6. [AI client connection and validation](docs/setup/ai-client.md) — configure OAuth/MCP access and safely test inventory and logs.

For maintainers and coding agents:
- [Project status and handoff](docs/PROJECT-STATUS.md)
- [Architecture decisions](docs/architecture/decisions/)
- [Modular monolith architecture](docs/architecture/modular-monolith.md)
- [Release 2 Docker architecture](docs/architecture/release-2-docker-inventory.md)
- [CLAUDE.md](CLAUDE.md) — repository-specific engineering and security rules
- [Changelog](docs/CHANGELOG.md)

## Security model

- **Authentication:** MCP requests use the configured Webtools OAuth/OIDC resource-server boundary.
- **Authorization:** the central Webtools identity must be in the private Homelab MCP user allowlist. A missing or empty allowlist denies access.
- **Proxmox:** use a dedicated API user and API token with the built-in \`PVEAuditor\` role. With token privilege separation enabled, both the backing user and token need the intended ACL grant.
- **Docker:** the MCP exposes only fixed, code-defined Portainer GET operations. It does not provide a generic Portainer proxy. A Portainer API key inherits its user's permissions; an administrator-owned key is high-impact even if this application only issues GET requests.
- **TLS:** certificate-chain and hostname/IP verification remain enabled. Install the private CA in the Java truststore; never use trust-all TLS or insecure HTTP.
- **Data minimization:** inventory contains allowlisted fields only. Environment variables, secrets, labels, commands, mounts, and raw inspect payloads are excluded.
- **Read-only:** no infrastructure mutation tools, shell/exec tools, Docker socket access, or direct remote Docker TCP access are provided.
- **Untrusted content:** host/container metadata and logs are data, not instructions.

## Runtime configuration

The application imports configuration from Spring Cloud Config Server. Runtime bootstrap variables:

| Variable | Purpose |
|---|---|
| \`CONFIG_SERVER_URL\` | Private Config Server base URL |
| \`CONFIG_USER\` | Config Server username |
| \`CONFIG_PASSWORD\` | Config Server password |

The Config Server application file is \`homelab-management-mcp.yaml\`. It holds Proxmox API credentials, Portainer URL/API key, Docker environment allowlist, central-user allowlist, OAuth resource-server settings, and timeouts/response limits. See [Private configuration and deployment](docs/setup/configuration-and-deployment.md) for the property structure.

Never put real values in this repository, public issues, AI prompts, or public logs.

## Deployment model

The service runs as a non-root Docker container. MCP Streamable HTTP listens on container port **8083** at \`/mcp\`. Actuator uses a separate management port **8082** and must remain private. Do not publish either port directly to the host. Route only the authenticated MCP endpoint through the intended private network and tunnel boundary; never expose the management port.

Published image:

\`\`\`text
ikoyski/homelab-management-mcp:latest
\`\`\`

See the [deployment guide](docs/setup/configuration-and-deployment.md) for a Compose template and verification commands.

## Development

Requirements: Java 21 and Maven 3.9 or newer.

\`\`\`bash
mvn -B clean verify
\`\`\`

Use mocks/fixtures for ordinary tests; production infrastructure credentials are not required for the normal test suite. Do not deploy a new image without operator approval.

## Project status

Proxmox inventory, Docker inventory, host-scoped container/network filters, and bounded container-log retrieval are implemented and deployed. Inventory and log-diagnostic workflows have been exercised through Claude, Gemini, and Grok. Live infrastructure names, counts, and mappings are intentionally omitted here.

See [docs/PROJECT-STATUS.md](docs/PROJECT-STATUS.md) for dated validation details. Historical observations there must not be mistaken for current live state.

## License

See [LICENSE](LICENSE) if present.
