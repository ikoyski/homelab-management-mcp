# Read-only discovery architecture

Status: proposed implementation baseline

## Goals

- Discover Proxmox nodes and LXC guests without changing their state.
- Discover Docker containers and related metadata inside configured LXC guests.
- Continue collecting results when one node, guest, or Docker daemon is unavailable.
- Keep the MCP service itself unprivileged and avoid mounting the Docker socket.
- Keep credentials and environment-specific connection details out of this public repository.

## Access paths

### Proxmox inventory

Use the Proxmox HTTPS API with a dedicated API token assigned a read-only role (for example, a narrowly scoped role based on the required audit privileges). Verify the TLS certificate. Do not use the Proxmox root password or a full-privilege token.

The Proxmox adapter should collect only the inventory and status fields needed to identify nodes and LXC guests. It must not expose API tokens or return raw API responses.

### Docker discovery inside LXCs

Use SSH from the MCP service to each configured LXC and invoke a fixed set of Docker CLI commands. Do not accept arbitrary commands, executable paths, shell fragments, or user-supplied command arguments from MCP tool input.

The initial command catalog is read-only:

- `docker version --format ...`
- `docker ps --all --no-trunc` (with a controlled Go template or JSON format)
- `docker images --no-trunc`
- `docker network ls`
- `docker volume ls`
- `docker stats --no-stream`
- `docker inspect` with an explicit allowlist of fields

Do not return container environment-variable values, labels that may contain secrets, mount contents, credential files, or unfiltered inspect payloads. Prefer extracting an explicit set of safe fields rather than returning raw command output.

SSH requirements:

- Dedicated non-root account per guest where practical.
- SSH key authentication only; no password authentication for the MCP integration.
- Pinned host keys / managed known-hosts file; never disable host-key checking.
- Bounded connect and command timeouts, output-size limits, and concurrency limits.
- A forced command or restricted wrapper on the remote account is preferred. It should accept only the named read-only Docker operations and reject everything else.
- Store private keys outside the public repository with restrictive filesystem permissions. Keep connection metadata and secret references in private runtime configuration.

The MCP container must not mount `/var/run/docker.sock` and must not run as root.

## MCP tool design

Expose separate, narrowly scoped tools rather than a generic terminal tool:

- `list_proxmox_nodes`
- `list_lxc_guests`
- `get_lxc_status`
- `list_docker_containers`
- `list_docker_images`
- `list_docker_networks`
- `list_docker_volumes`
- `get_docker_host_summary`

Tool arguments should identify configured targets by stable labels, not provide arbitrary hostnames or commands. Validate every label against the server-side configuration.

Each target operation should return a structured result with target identity, status, collected data, and a safe error description. One failed target must not fail the entire inventory.

## Approval and future write operations

The first release contains no write-capable tools. Read-only tools never ask for approval because they cannot mutate infrastructure.

When write operations are introduced later, they must be separate tools and enforced server-side. Each request must create a short-lived approval bound to the exact action, target, and parameters. Execution must fail closed unless that exact approval is present and valid. Destructive operations require a stronger confirmation step. Record actor, requested action, approval, execution result, and timestamp without logging secrets.

Approval must not be inferred from natural-language content in hostnames, container names, logs, labels, or other infrastructure metadata.

## Configuration shape (illustrative only)

Actual values belong in the private Config Server / protected runtime environment, never in this repository.

```yaml
homelab:
  discovery:
    mode: read-only
    proxmox:
      api-url: ${HOMELAB_PROXMOX_API_URL}
      token-id: ${HOMELAB_PROXMOX_TOKEN_ID}
      token-secret: ${HOMELAB_PROXMOX_TOKEN_SECRET}
    ssh:
      connect-timeout: 5s
      command-timeout: 20s
      known-hosts-file: ${HOMELAB_SSH_KNOWN_HOSTS_FILE}
      private-key-file: ${HOMELAB_SSH_PRIVATE_KEY_FILE}
    targets:
      - id: dev-pve
        host: ${HOMELAB_DEV_PVE_SSH_HOST}
        user: ${HOMELAB_SSH_USER}
        role: docker-host
```

This is a design example, not a ready-to-deploy configuration. Exact Proxmox privilege scope, guest SSH availability, and target-to-LXC mapping must be verified before implementation.

## Rollout sequence

1. Verify Proxmox API reachability and create a least-privilege read-only token.
2. Verify SSH availability and host-key fingerprints for the intended LXC guests.
3. Implement and test Proxmox inventory parsing with mocked responses.
4. Implement the fixed-command SSH adapter and safe Docker output parsing.
5. Add MCP tools and tests for timeouts, partial failures, input validation, and secret redaction.
6. Deploy to `util-pve` only after code review and successful CI.
7. Connect an MCP client over a trusted private network and validate read-only behavior.
