# Release 1 — Proxmox read-only inventory

## Scope

Release 1 connects to the Proxmox VE HTTPS API and exposes dynamically discovered cluster nodes, QEMU virtual machines, LXC containers, and storage. It is strictly read-only.

No per-guest host list is required. No SSH, Docker socket, shell execution, or guest-name hardcoding is part of this release.

## Proxmox API calls

- `GET /api2/json/nodes` — node inventory.
- `GET /api2/json/cluster/resources?type=vm` — QEMU and LXC guest inventory.
- `GET /api2/json/cluster/resources?type=storage` — storage inventory.
- `GET /api2/json/nodes/{node}/storage/{storage}/status` — capacity and status enrichment for each discovered storage entry.

Storage status exposes the backend as `storageType`, while preserving the
cluster-resource `type` field. Reported `total`, `used`, and `avail` values
are mapped to byte fields without recalculating them. `usagePercent` is
calculated only when a positive total is available. Individual status-request
failures mark only the affected storage entry unavailable, retain other
entries, and are included in the storage section's sanitized `errors` list.


The adapter extracts explicit safe fields and does not return raw API payloads. A failure in one section does not suppress successful sections; combined inventory is marked partial and includes a sanitized error summary.

## Metric semantics

The `cpu` field from Proxmox is a utilization fraction in the range `0` to
`1`; it is rounded to six decimal places and preserved as a fraction.
`cpuUsagePercent` is derived as `cpu * 100`; do not divide by `maxcpu`.
The memory and disk capacity fields (`mem`, `maxmem`, `disk`, `maxdisk`)
are byte counts and carry `byteUnit: "bytes"`. `memoryUsagePercent` is
derived from `mem / maxmem * 100` only when both values are present and
`maxmem` is positive. Missing or invalid source metrics do not produce a
derived percentage.

For QEMU and LXC guest entries, `maxdisk` is the reported root/boot disk
capacity and `disk` is Proxmox's reported used root-image space. QEMU
`disk` may remain zero because the cluster inventory endpoint does not
provide actual in-guest filesystem usage for a VM. LXC disk reporting differs.
A zero QEMU `disk` value must not be interpreted as an empty virtual disk.

QEMU in-guest filesystem usage is reported separately through the QEMU Guest
Agent `filesystems` enrichment. It describes mounted filesystems inside the
guest and must not be substituted into the Proxmox `disk` field.

## MCP tools

- `list_proxmox_nodes`
- `list_proxmox_guests`
- `list_proxmox_storage`
- `get_proxmox_inventory`

Tools accept no caller-supplied host, node, VMID, URL, or command arguments. Inventory scope is determined by the server-side Proxmox API configuration and token.

## Private configuration

Store these only in the private Config Server or protected runtime environment:

- `homelab.proxmox.api-url`
- `homelab.proxmox.token-id`
- `homelab.proxmox.token-secret`
- Optional `homelab.proxmox.connect-timeout-ms` and `homelab.proxmox.read-timeout-ms`.

Use a dedicated API token with minimum read-only privileges. Keep TLS certificate and hostname verification enabled. Fix trust-chain problems by configuring the correct CA in the Java runtime.

## Java truststore for private Proxmox CA

The adapter uses the JVM's standard TLS validation. In a deployment where Proxmox presents a certificate issued by a private cluster CA, the operator must provision a custom Java truststore at runtime.

The truststore should be created from the JRE image's original `cacerts` so normal public roots remain trusted, then extended with the Proxmox root CA. Mount it read-only and set the JVM properties through `JAVA_TOOL_OPTIONS`:

```text
-Djavax.net.ssl.trustStore=<mounted-truststore-path>
-Djavax.net.ssl.trustStorePassword=<truststore-password>
```

Do not commit the CA/truststore or bake a site-specific CA into this public image. Do not disable certificate or hostname verification. The host-specific steps belong in the private homelab deployment runbook. Do not commit deployment paths, addresses, or certificate material to this public repository.

## Failure and data handling

Each API section is isolated. Errors contain no response body, exception message, authorization header, or credential. Results include only allowlisted inventory fields. No environment variables, secrets, private keys, or arbitrary Proxmox response fields are returned.

## Release validation checklist

1. Verify token permissions in Proxmox.
2. Verify API URL reachability and certificate trust from the MCP container network.
3. Run Maven tests and inspect CI output.
4. Deploy only after CI passes.
5. Use Claude to call each inventory tool and compare nodes, guests, and storage with Proxmox.
6. Verify partial-result behavior by testing an unavailable API section in a controlled test environment.
7. Confirm no write-capable tools are advertised and no credentials appear in tool output or logs.
