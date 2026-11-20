# Release 2 — Docker read-only inventory

> Current implementation state and next actions: [Project Status](../PROJECT-STATUS.md). This document is the detailed Release 2 design.

## Scope

Release 2 adds read-only Docker discovery to the existing Homelab Management MCP. It complements Release 1 (Proxmox inventory) and does not replace or change the Proxmox adapter.

The MCP connects directly to **one Portainer Server** through its dedicated, internal Portainer infrastructure adapter. Portainer Server is the control-plane entry point for its local Docker Engine and the remote Docker Engines reached through Portainer Agents. The MCP does not connect directly to remote Docker Engines or Agents. The adapter is the only component in the application that calls the Portainer API.

The Docker environment allowlist and any environment-to-guest mappings are supplied only by the private Config Server. The public repository does not enumerate live host names, Proxmox guest identifiers, environment counts, or mappings. Only exact identities present in the private allowlist are discovered; any unlisted environment is excluded.

Portainer environment IDs are discovered at runtime. Do not hard-code Portainer endpoint IDs or accept endpoint IDs, URLs, host names, Docker API paths, or commands from MCP callers. Match discovered environments against the server-side allowlist and associate them with Proxmox guests using private configuration.

Release 2 is strictly read-only. It does not start, stop, restart, create, update, remove, exec into, or otherwise modify containers, images, networks, volumes, stacks, or Docker hosts.

## Architecture

    AI clients
        |
        | authenticated MCP requests
        v
    Homelab Management MCP
        |
        | dedicated Portainer adapter (fixed GET operations, verified TLS)
        v
    Portainer Server
        |-- local Docker Engine (private management environment)
        |-- Portainer Agent --> Docker Engine (remote environment A)
        |-- Portainer Agent --> Docker Engine (remote environment B)
        |-- Portainer Agent --> Docker Engine (remote environment C)
        '-- Portainer Agent --> Docker Engine (additional configured environments)

Only the Portainer Server is a network dependency for Docker inventory. The MCP must not connect directly to Agent port 9001, remote Docker TCP sockets, or remote Docker Unix sockets. The internal Portainer adapter discovers environment IDs, resolves them against its private configured allowlist, and issues fixed upstream requests to Portainer.

The Portainer integration is an infrastructure adapter, not the domain model. Internal Docker host, container, network, port-binding, and inventory result models must be defined by this application and must not expose Portainer response DTOs. The adapter maps Portainer responses into application-owned allowlisted inventory models; raw Portainer/Docker payloads are never forwarded. A separate diagnostic tool may return bounded container log text under the explicit contract below; log contents are not included in inventory results. This keeps the MCP inventory contract independent of Portainer.

## Portainer API access

The Portainer HTTPS base URL and API key are configured only in the private runtime configuration of `homelab-management-mcp`. The Portainer adapter discovers environments using the Portainer endpoint-list API, resolves IDs against the server-side allowlist, and uses fixed Docker API operations. No generic proxy endpoint or caller-controlled upstream path exists.

The implementation should use only the documented Portainer API and Docker Engine API read operations needed for inventory. Candidate operations to validate against the deployed Portainer CE API specification include:

- **GET /api/endpoints** — discover configured Portainer environments.
- **GET /api/endpoints/{endpointId}/docker/info** — Docker Engine information for an environment.
- **GET /api/endpoints/{endpointId}/docker/containers/json?all=true** — running and stopped containers.
- **GET /api/endpoints/{endpointId}/docker/containers/{containerId}/json** — allowlisted container detail enrichment where required.
- **GET /api/endpoints/{endpointId}/docker/networks** — network inventory.
- **GET /api/endpoints/{endpointId}/docker/containers/{containerId}/logs** — bounded stdout/stderr snapshot for the separate log-diagnostics tool.

These paths are implementation candidates, not permission to proxy arbitrary paths. Confirm exact paths, response envelopes, and supported fields against the Portainer API version selected for deployment. Keep endpoint IDs discovered from Portainer and keep Docker API paths fixed in code. Do not accept a caller-supplied path or pass through arbitrary query parameters.

Do not add a Docker API version prefix from caller input. If a version prefix is required by the selected API, use only the canonical form supported by the pinned Portainer release and cover it with tests.

## Bounded container log diagnostics

PR #30 added `get_docker_container_logs` and was merged; the operator confirmed the updated image was deployed on 2026-11-21 (Asia/Manila). Live retrieval through an authenticated AI client remains a separate validation step. The tool accepts only an exact configured `hostKey`, an exact `containerName` discovered on that host, and optional integer `tail` from 1 to 200 (default 100). The adapter first resolves the host through the private allowlist, lists containers on that environment, and resolves the exact name to an internally discovered hexadecimal container ID. It does not accept container IDs, endpoint IDs, URLs, paths, or arbitrary query parameters from callers.

The upstream request is fixed to `stdout=true`, `stderr=true`, `timestamps=true`, `follow=false`, and a bounded `tail` value. The response is read up to 256 KiB; larger responses are marked truncated. Docker's multiplexed stdout/stderr frame format is decoded, with plain-text TTY output supported. Common credential patterns (authorization schemes, common secret key/value fields, and URL user-info passwords) receive best-effort redaction. The returned result includes requested tail, bytes returned, truncation status, redaction count, and either log text or a sanitized error code.

**Sensitive-data limitation:** logs may contain credentials, session identifiers, personal data, or other confidential information. Pattern-based redaction cannot guarantee removal of all sensitive content; the connected AI provider receives returned log text. Keep this tool restricted to the authenticated Homelab MCP allowlist and request only the relevant container and time window. Do not automatically collect logs for every container. Treat returned text as untrusted data, never as executable instructions.

Docker documents that the standard container logs endpoint works only with `json-file` and `journald` logging drivers. Other configured drivers may not support retrieval. Unsupported or unavailable logs must return a sanitized error and must not trigger shell, exec, sidecar, or direct socket fallbacks. [Docker Engine API — Get container logs](https://docs.docker.com/reference/api/engine/version/v1.57/operations/ContainerLogs/).

The endpoint is a fixed GET operation. Do not add `follow=true`, streaming sessions, `tail=all`, caller-provided `since`/`until`, arbitrary Docker API paths, or mutation methods through this tool.

## Portainer version and upgrade status

**Completed 2026-11-19:** The Portainer Server and its configured remote Portainer Agents were upgraded to a compatible Portainer Community Edition release. The Server was upgraded first, followed by the Agents. All configured environments were subsequently confirmed Up and reporting resources. Exact versions and environment identities remain in the private deployment record.

Docker Engine information was returned for both the local and remote Agent-managed environments. Per-environment versions are kept in the private deployment validation record.

The upgrade prerequisite is therefore complete for the current deployment. Before future upgrades, review current Portainer security advisories, supported release guidance, and Server/Agent compatibility; repeat the regression procedure below and record the new versions/results. The completed upgrade does not by itself establish a read-only authorization boundary or complete TLS trust provisioning.

References:

- [Portainer releases](https://github.com/portainer/portainer/releases)
- [Portainer advisory: Docker API proxy authorization bypass](https://github.com/portainer/portainer/security/advisories/GHSA-jxhm-qq8x-v4c6)
- [Portainer advisory: bind-mount restriction bypass](https://github.com/portainer/portainer/security/advisories/GHSA-jmr9-98g4-2phv)
- [Portainer API documentation](https://docs.portainer.io/api/docs)
- [Accessing the Portainer API](https://docs.portainer.io/api/access)



## Portainer upgrade compatibility

Portainer is the initial Docker discovery transport, but the MCP inventory contract must not be coupled to a single Portainer release. Release 2 must demonstrate that a supported, compatible Portainer Server and Agent upgrade does not require changes to the MCP application for ordinary backward-compatible API changes.

The previous Portainer CE **2.39.6 LTS** installation is historical only. The current observed homelab baseline after the 2026-11-19 upgrade is Portainer CE **2.45.4** on the Server and all four Agents. This version record is an operational observation, not a substitute for checking current advisories and compatibility guidance before future upgrades.

### Upgrade validation procedure

1. Before upgrading, record the Portainer Server version, each Agent version, the five environment names and connection states, and representative inventory results from the same MCP build where it is safe to run the baseline. If the old version is not safe for integration testing, capture only the Portainer UI/environment baseline and perform MCP testing after the security upgrade.
2. Back up Portainer data and record the current deployment configuration using the operator's normal maintenance procedure.
3. Upgrade the Portainer Server first, then upgrade the Agents to the matching compatible release, following the official upgrade guidance for that release.
4. Confirm that all five configured environments reconnect and that their environment identities still map to the expected Proxmox guests.
5. Run the same MCP integration and regression tests against the upgraded Server and Agents. Compare host availability, Docker Engine information, container state/health/restart/uptime, networks, and published-port results with the expected inventory. Differences caused by legitimate workload changes must be distinguished from API compatibility regressions.
6. Exercise partial-failure behavior by making a test Agent unavailable in a controlled maintenance window, verify the other environments still return inventory, then restore the Agent and verify recovery.
7. Record Server and Agent versions, test results, any API response differences, and whether an MCP code/configuration change was required.

The upgrade test passes when the upgraded Portainer Server and Agents support the documented API operations, all five configured environments are discovered and correctly mapped, inventory fields retain their documented semantics, and a single unavailable Agent does not suppress results from other environments. A compatible upgrade should not require an MCP code change. If Portainer introduces a breaking API change, treat it as an explicit adapter compatibility update with fixtures and regression tests; do not silently weaken validation or expose raw Portainer payloads.

Repeat this procedure for future Portainer upgrades. Check the release notes, security advisories, API documentation, and Server/Agent compatibility guidance before each upgrade. Keep Server and Agents on mutually compatible supported versions, and retain the last known-good MCP test results for comparison.

References:
- [Portainer upgrade instructions](https://docs.portainer.io/start/upgrade)
- [Portainer releases](https://github.com/portainer/portainer/releases)
- [Portainer API documentation](https://docs.portainer.io/api/docs)


## Authentication and authorization boundary

Portainer API access tokens inherit the permissions of the Portainer user that created them. An API token is not intrinsically read-only merely because this application intends to issue GET requests.

Portainer CE access-control capabilities must be verified for the selected release. Do not assume that a standard Portainer user or an API token provides a sufficiently narrow read-only permission set for all required Docker proxy operations.

Before implementation is enabled, choose and document an enforceable least-privilege boundary:

- Prefer a Portainer account with only the minimum environment visibility and read permissions that the installed CE release can actually enforce.
- If CE cannot enforce the required read-only access, enforce the boundary inside the dedicated Portainer adapter in the MCP: expose only fixed inventory methods that issue GET requests, reject caller-controlled paths/URLs/endpoint IDs and arbitrary query parameters, and keep the Portainer credential private. Do not add generic HTTP forwarding or write-capable Portainer methods.
- Do not expose Portainer API publicly for this integration.
- Do not store the Portainer credential in source control, tool arguments, logs, or returned inventory.

A client-side convention to issue only GET requests is not, by itself, a server-side authorization boundary. The implementation must not describe the integration as securely read-only until the chosen boundary has been tested.

## TLS trust

Use HTTPS to Portainer and keep normal certificate-chain and hostname/IP verification enabled.

The Portainer certificate is currently self-signed. Provision the appropriate self-signed certificate or issuing CA into the Java truststore used by the MCP. If the certificate is accessed by IP address, it must contain that IP address in its subject alternative names; otherwise use a DNS name present in the certificate and resolve it internally.

Build the runtime truststore from the JRE original public CA bundle and add the Portainer CA/certificate. Mount the truststore read-only and configure the JVM truststore properties through protected runtime configuration. Keep the CA, generated truststore, and passwords in the private deployment environment.

Do not use trust-all TLS settings, disable hostname verification, use an insecure HTTP URL, or use curl -k as a workaround. Keep the Proxmox CA trust and Portainer CA trust together in the runtime truststore when practical, without committing site-specific trust material to this public repository. Document certificate renewal and truststore replacement in the private deployment runbook.

## Inventory data model

### Docker hosts

Each configured environment reports:

- Stable internal host key and configured display name.
- Associated Proxmox guest identifier and display name.
- Environment type (local Docker or Agent-managed Docker).
- Availability status.
- Docker Engine version and operating-system information when safely available.
- Collection timestamp.
- Sanitized per-environment error information when unavailable or partially collected.

Portainer endpoint IDs are implementation details and should not be treated as stable public identifiers.

### Containers

Return only explicitly allowlisted fields:

- Container ID (full or consistently truncated according to the tool contract).
- Container name.
- Image reference.
- State and status.
- Health status from structured Docker inspect data: `healthy`, `unhealthy`, or `starting`; use `not_configured` when the inspect response has no health check, and leave it null when detail enrichment is unavailable.
- Restart count from the container inspect summary.
- Created timestamp and StartedAt timestamp.
- Calculated uptime from StartedAt and the collection timestamp only when Docker reports the container is running and StartedAt is valid.
- Attached Docker network names from the container-list response.
- Published port bindings (container port, host port, and protocol).

Container detail enrichment uses the fixed read-only inspect operation for each discovered container. At most four inspect requests run concurrently per collection. If an inspect request fails, retain the base container summary, leave unavailable detail fields null, and mark that host's result as partial with a sanitized `container_details_unavailable` error.

An empty published-port list is meaningful and must be returned as an empty list, not omitted.

Do not return environment variables, labels, commands, entrypoints, mounts, bind paths, secrets, raw inspect payloads, or arbitrary Docker metadata in inventory/inspect responses. Inventory excludes logs; log text is available only through the separate bounded get_docker_container_logs tool described below. Avoid returning container IP addresses, MAC addresses, or other network details unless a later requirement explicitly justifies them.

### Networks

Return only allowlisted network fields:

- Network ID and name.
- Driver and scope.
- Internal and attachable flags when available.
- Names/IDs of connected containers, without exposing raw endpoint configuration.

When Portainer's network response omits connected-container details, derive membership by matching each network name to the same host's container-side network attachments. Keep this fallback scoped by host; do not infer membership across Docker Engines.

Do not return network IPAM configuration, subnets, gateways, container IP addresses, options, labels, or raw API payloads in the initial release.

## MCP tools

The planned tools are:

- **list_docker_hosts** — reports the configured Docker environments and their availability.
- **list_docker_containers** — lists allowlisted container details across all configured hosts, or one host when the optional `hostKey` argument is supplied.
- **list_docker_networks** — lists Docker networks and connected container identities across all configured hosts, or one host when the optional `hostKey` argument is supplied.
- **get_docker_inventory** — combines host, container, and network inventory with collection timestamps and partial-result information.
- **get_docker_container_logs** — bounded recent stdout/stderr logs for one exact container on one configured host (merged and deployed via PR #30).

The optional `hostKey` argument is supported by `list_docker_containers` and `list_docker_networks`. It must exactly match a configured internal host key; an invalid key is rejected before any Portainer request. Omitting it returns results across all configured hosts. The log tool requires `hostKey` and `containerName` and accepts only an optional bounded `tail` argument. It is merged and deployed; verify live retrieval through an authenticated client before claiming end-to-end log validation.

Tool inputs must not accept arbitrary URLs, Portainer endpoint IDs, Docker API paths, commands, container exec instructions, or credentials.

No write-capable Docker tools are included in Release 2.

## Private runtime configuration

Store these only in the private Config Server or protected runtime environment:

- **homelab.docker.portainer.base-url** — internal HTTPS Portainer Server URL.
- **homelab.docker.portainer.api-key** — protected Portainer API token used only by the Portainer infrastructure adapter.
- **homelab.docker.portainer.connect-timeout-ms**.
- **homelab.docker.portainer.read-timeout-ms**.
- **homelab.docker.portainer.tls** — truststore configuration supplied through protected runtime settings.
- **homelab.docker.environments** — explicit allowlist mapping configured environment names to internal host keys and Proxmox guest identifiers.

The Portainer API key inherits the permissions of its Portainer user. The adapter exposes only fixed GET operations, but an administrator-owned API key retains administrator-level upstream privileges if compromised; treat it as a high-impact credential and keep it only in the private Config Server/runtime environment.

Do not commit real URLs, private IP addresses, API keys, truststores, passwords, or certificate material to this repository. Do not place environment-specific Portainer endpoint IDs in configuration; discover them from Portainer and match by the configured environment identity.

## Failure handling and data safety

Each Docker environment is collected independently. A failed or disconnected Agent must not suppress inventory from other environments. Report the affected environment as unavailable and continue with the others.

Use stable sanitized error codes, for example:

- **portainer_unavailable**
- **portainer_authentication_failed**
- **environment_not_found**
- **environment_unavailable**
- **unexpected_response**
- **docker_inventory_request_failed**

Do not include response bodies, exception messages, authorization headers, API keys, environment variables, labels, or raw Docker/Portainer payloads in errors or logs.

The combined inventory status is **ok**, **partial**, or **unavailable** based on the configured environment results. Include collection timestamps. Do not claim the combined result is an atomic snapshot across hosts.

## Implementation sequence

1. **Completed:** Upgrade and verify Portainer Server and Agents on Portainer CE 2.45.4; all five configured environments reconnected and reported resources.
2. Implement a dedicated Portainer infrastructure module inside Homelab Management MCP with fixed GET-only operations, dynamic endpoint discovery, strict allowlisting, bounded responses, verified TLS, and tests.
3. Provision and verify Java TLS trust for the self-signed Portainer certificate in the MCP runtime.
4. Map Portainer responses into application-owned Docker inventory models, then implement the Docker inventory use cases and MCP tools.
5. Implement environment discovery and configured-host matching.
6. Implement **list_docker_hosts** and test all five environments.
7. Implement container inventory and allowlisted detail enrichment.
8. Implement network inventory.
9. Implement combined inventory, collection timestamps, and partial-failure behavior.
10. Add tests for authentication failures, TLS failures, missing environments, unavailable Agents, malformed responses, and sensitive-field exclusion.
11. Verify all Docker tools through an authenticated MCP client.
12. Deploy only after CI passes and the operator explicitly approves the deployment.

## Release validation checklist

1. Confirm the recorded Portainer Server and all four Agents remain on a supported patched, compatible release; the 2026-11-19 validated baseline is CE 2.45.4.
2. Confirm the Portainer Server API is reachable from the MCP internal network only.
3. Confirm TLS certificate-chain and hostname/IP verification succeed from the MCP runtime.
4. Verify the chosen read-only boundary rejects write methods and unapproved API paths.
5. Verify only the environments supplied by the private allowlist are discovered; confirm unlisted environments are excluded.
6. Verify environment IDs are discovered dynamically and map to the expected Proxmox guests.
7. Compare Docker host, container, network, health, restart, uptime, and published-port data with Portainer.
8. Test an unavailable Agent and confirm the other environments still return results.
9. Run Maven tests and inspect CI output.
10. Confirm no write-capable tools are advertised and no secrets, environment variables, labels, or raw inspect payloads appear in inventory output or service logs. For the separate log tool, verify bounded retrieval, best-effort redaction, explicit sensitive-data warning, and no follow/streaming behavior.
11. After a supported Portainer Server and Agent upgrade, repeat the same tests and verify all five environments, inventory semantics, partial-failure handling, and Agent recovery; record versions and results. A backward-compatible upgrade should not require an MCP code change.
12. Deploy only after CI passes and explicit approval; verify through an authenticated MCP client.

## Out of scope

- Starting, stopping, restarting, creating, deleting, updating, or executing commands in containers.
- Docker image pulls, builds, pruning, volume changes, stack deployments, and network changes.
- Docker logs, environment variables, secrets, labels, mounts, and arbitrary inspect output.
- Direct SSH access, direct Docker socket access, or direct remote Docker TCP access from the MCP.
- Docker discovery on environments excluded from the private allowlist.
- Natural-language status thresholds, historical change detection, and short/detailed homelab reporting. These remain a follow-up after Docker support is complete.
