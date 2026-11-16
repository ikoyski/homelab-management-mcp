# Homelab Management MCP

A read-only-first Model Context Protocol (MCP) server for discovering and monitoring a private homelab. This repository is public; runtime endpoints, credentials, private keys, and sensitive infrastructure details must remain outside Git.

## Current release: Release 1 — Proxmox inventory

The service exposes read-only MCP tools for discovering Proxmox nodes, QEMU virtual machines, LXC containers, and storage. Guest and node names are discovered dynamically from Proxmox; they are not hardcoded.

| MCP tool | Purpose |
|---|---|
| `get_discovery_scope` | Reports configured discovery labels and release scope |
| `list_proxmox_nodes` | Lists nodes and safe status/resource fields |
| `list_proxmox_guests` | Lists all QEMU VMs and LXC containers |
| `list_proxmox_storage` | Lists storage and safe capacity/status fields |
| `get_proxmox_inventory` | Combines nodes, guests, and storage; reports partial failures |

Docker discovery and all infrastructure-changing operations are outside Release 1.

## Security boundaries

- Use a dedicated Proxmox API token with only the read privileges required for inventory.
- TLS certificate verification remains enabled; do not disable hostname or certificate validation.
- The adapter extracts explicit allowlisted fields and never returns raw Proxmox API responses.
- Request failures do not include response bodies, authorization headers, exception messages, or credentials in logs/tool output.
- All inventory calls are GET requests. No start, stop, restart, migrate, clone, snapshot, delete, update, or configuration mutation tools are included.
- No SSH, arbitrary shell execution, Docker socket, or per-guest connection configuration is used in Release 1.
- MCP requests continue to require Webtools authentication and the Homelab MCP central-user allowlist.
- Store runtime endpoints and token material only in the private Webtools Config Server or protected runtime environment.

## Private runtime configuration

Configure the following properties in the private Config Server under `homelab-management-mcp.yaml`:

| Property | Purpose |
|---|---|
| `homelab.proxmox.api-url` | Proxmox API base URL, including HTTPS scheme and port |
| `homelab.proxmox.token-id` | Proxmox API token identifier (user/realm and token name) |
| `homelab.proxmox.token-secret` | Proxmox API token secret |
| `homelab.proxmox.connect-timeout-ms` | Connection timeout; defaults to 3000 ms |
| `homelab.proxmox.read-timeout-ms` | Read timeout; defaults to 8000 ms |

Do not commit real values to this repository. The token must be scoped to the minimum required read-only Proxmox privileges. If the Proxmox certificate is not trusted by the Java runtime, install the correct CA certificate rather than disabling TLS verification.

## Technology

- Java 21
- Spring Boot 4
- Spring Cloud Config Client
- Proxmox VE HTTPS API
- Model Context Protocol Java SDK
- Streamable HTTP transport at `/mcp`
- Spring Security OAuth2 Resource Server
- Spring Boot Actuator

## Deployment

The service is deployed as a Docker container on `util-pve` in the existing `utility_default` network. MCP listens on port 8083; Actuator uses the separate internal management port 8082. Keep the Cloudflare Tunnel origin pointed only at the MCP endpoint. Do not publish either container port directly to the host.

After CI publishes the image, update only this service:

```bash
cd ~/myhomelab/pve/util-pve
sudo docker compose pull homelab-management-mcp
sudo docker compose up -d --no-deps --force-recreate --pull never homelab-management-mcp
sudo docker compose ps homelab-management-mcp
sudo docker compose logs --tail=80 homelab-management-mcp
```

Verify health from the internal Docker network:

```bash
sudo docker run --rm --network utility_default curlimages/curl:latest \
  -fsS http://homelab-management-mcp:8082/actuator/health
```

## Local development

Requires Java 21 and Maven 3.9+.

```bash
mvn -B clean verify
```

The Config Server must be reachable using protected bootstrap settings supplied by the runtime environment.

## Future releases

- Release 2: Docker discovery and management visibility.
- Later releases: separately designed, explicitly authorized operational actions with approval and audit controls.

Future write-capable tools must be separate from discovery tools and enforced server-side. Destructive actions must never be bundled into general inventory operations.
