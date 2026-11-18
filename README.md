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

Storage inventory is enriched with a per-node storage status request:
`GET /api2/json/nodes/{node}/storage/{storage}/status`. The existing resource
`type` remains unchanged; the Proxmox backend type is exposed separately as
`storageType`. Capacity is returned in bytes as `totalBytes`, `usedBytes`,
and `availableBytes`, with `usagePercent` calculated from the reported total
and used values when total capacity is positive. The Proxmox-reported values
are preserved rather than deriving available capacity from total minus used.

Each storage entry reports `storageStatus` as `ok` or `unavailable`.
A failed status request adds a sanitized entry to the storage section's
`errors` array and changes that section's status to `partial`; other storage
entries remain available. The backend type and status fields are allowlisted;
storage paths and other configuration details are not returned.

### Metric semantics

The Proxmox `cpu` field is a utilization fraction from `0` to `1` (for example,
`0.125` means 12.5% of the available CPU capacity). The inventory rounds this
fraction to six decimal places and keeps it as a decimal fraction; it does not
convert it to a percentage.

For guest disk fields from `/cluster/resources`, `maxdisk` is the reported
root/boot disk capacity. The `disk` value is Proxmox's reported used root-image
space; for QEMU VMs it may remain zero because Proxmox does not know actual
in-guest filesystem usage from this cluster inventory endpoint. LXC disk
usage has different reporting behavior. Do not interpret a QEMU `disk: 0`
as proof that its virtual disk is empty.

For QEMU in-guest filesystem capacity and usage, use the separate `filesystems`
data collected through the QEMU Guest Agent. Those values describe mounted
guest filesystems and are not interchangeable with the Proxmox virtual-disk
fields. The original `disk` and `maxdisk` fields are retained unchanged.

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
| `homelab.security.allowed-users` | Allowlist of central Webtools identity UUIDs; an absent or empty list denies access |
| `homelab.discovery.targets` | Informational target labels read by `get_discovery_scope`; not SSH targets or connection endpoints |
| `homelab.discovery.mode` | Present in configuration but not currently read by the tool; `get_discovery_scope` hardcodes `read-only` in its response |
| `homelab.proxmox.api-url` | Proxmox API base URL, including HTTPS scheme and port |
| `homelab.proxmox.token-id` | Proxmox API token identifier (user/realm and token name) |
| `homelab.proxmox.token-secret` | Proxmox API token secret |
| `homelab.proxmox.connect-timeout-ms` | Connection timeout; defaults to 3000 ms |
| `homelab.proxmox.read-timeout-ms` | Read timeout; defaults to 8000 ms |

Do not commit real values to this repository. The token must be scoped to the minimum required read-only Proxmox privileges. If the Proxmox certificate is not trusted by the Java runtime, install the correct CA certificate rather than disabling TLS verification.

## Proxmox TLS trust

The Proxmox adapter uses HTTPS with normal JVM certificate and hostname verification. If the Proxmox API certificate chains to a private Proxmox cluster CA, the deployment must provide a Java truststore that contains both the image's normal public CA roots and the Proxmox CA.

- Build the truststore from the JRE's original `cacerts`, then import the Proxmox CA under a dedicated alias.
- Mount the resulting truststore read-only into the container and set `JAVA_TOOL_OPTIONS` with `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`.
- Keep the CA and generated truststore on the private deployment host; never bake environment-specific private CA material into this public image or commit the truststore.
- Do not use trust-all TLS settings, disable hostname verification, or use `curl -k` as a workaround.

The private deployment procedure is maintained in `ikoyski/myhomelab`, at `pve/util-pve/docs/proxmox-ca-trust.md`. It records the host paths, Docker mounts, import/verification commands and CA renewal procedure.

An unauthenticated request to the protected `/mcp` endpoint is expected to return HTTP 401 with an OAuth bearer challenge. The service port should remain unpublished on the host and reachable only through the intended private Docker network / Cloudflare Tunnel boundary. Do not expose the actuator management port publicly.

## Current Release 1 verification status

As of 2026-11-17, the following homelab-side checks have been observed:

- Proxmox API HTTPS request with the CA bundle returned HTTP 401 when unauthenticated, confirming TLS validation and reachability.
- A Proxmox API token request to the nodes endpoint returned HTTP 200.
- The mounted Java truststore contains the `proxmox-cluster-ca` trusted certificate entry, and `keytool` can read it.
- The MCP container starts with the custom truststore configured.
- The local protected `/mcp` endpoint returns the expected OAuth 401 challenge.
- End-to-end MCP tool invocation through an AI client remains pending after the TLS fix. Do not mark the four inventory tools as end-to-end verified until they return real inventory through MCP.

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
