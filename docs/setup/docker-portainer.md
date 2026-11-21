# Docker and Portainer setup

The MCP discovers Docker resources through one Portainer Server. Portainer Server connects to its local Docker Engine and remote Docker Engines through Portainer Agents.

**Boundary:** the MCP does not mount \`/var/run/docker.sock\`, connect directly to Agent port 9001, or connect directly to remote Docker TCP/Unix sockets.

## 1. Prepare Docker hosts

For every Linux host to include:

1. Install and maintain a supported Docker Engine and Compose plugin.
2. Confirm the daemon is healthy.
3. Keep Docker's remote API disabled unless another independently secured requirement needs it; this MCP does not use it.
4. Restrict host firewall access to the management traffic required by Portainer.

References: [Install Docker Engine](https://docs.docker.com/engine/install/) · [Install Docker Compose](https://docs.docker.com/compose/install/linux/).

## 2. Deploy Portainer Server

Deploy one Portainer Server on a stable private management host. Follow the current official instructions:

- [Portainer CE installation](https://docs.portainer.io/start/install-ce)
- [Portainer Docker standalone installation](https://docs.portainer.io/start/install-ce/server/docker/linux)

Use persistent storage for Portainer data. Protect the UI/API with strong administrator credentials and keep it on the private management network. Do not publish its API to the public Internet.

Portainer Server needs its documented local Docker connection. The Docker socket may be used by Portainer Server in its supported local deployment; **it must not be mounted into Homelab Management MCP**. Portainer's installation may generate a self-signed HTTPS certificate by default. For a managed homelab, consider installing a server certificate issued by your private CA so the MCP can validate a stable CA chain and the URL's SAN.

## 3. Connect remote hosts using Agents

For each remote Docker host:

1. Deploy Portainer Agent using the official instructions for a compatible Server/Agent release.
2. Restrict the Agent listener to the private management network.
3. Permit the Agent listener (commonly TCP 9001) only from Portainer Server or the required private network.
4. In Portainer, add the environment using the Agent connection method and the private address reachable from Server.
5. Confirm the environment reports **Up** and Portainer can display containers and networks.

References: [Portainer Agent installation](https://docs.portainer.io/admin/environments/add/docker/agent) · [Portainer environments](https://docs.portainer.io/admin/environments).

Never expose the Agent listener publicly. Keep Server and Agents on mutually compatible supported versions.

## 4. Configure the MCP environment allowlist

Portainer may manage more environments than the MCP should expose. The MCP uses a private server-side allowlist. Only configured environment identities are eligible.

In private Config Server:

\`\`\`yaml
homelab:
  docker:
    portainer:
      environments:
        allowlist:
          - <host-key-one>
          - <host-key-two>
\`\`\`

Use stable lowercase keys. Do not publish actual hostnames, guest IDs, private IPs, endpoint IDs, or environment mappings. The adapter discovers Portainer endpoint IDs at runtime; do not hard-code them or accept them from MCP callers.

Adding an environment in Portainer does not automatically authorize MCP discovery. The private allowlist controls scope.

## 5. Create a Portainer API key

1. Sign in to Portainer over HTTPS as the intended API user.
2. Select the username in the top-right corner and open **My account**.
3. Scroll to **Access tokens** and choose **Add access token**.
4. Enter a recognizable description. Portainer may require you to re-enter the account password.
5. Create the token and copy it immediately to a password manager/private secret store. It cannot be viewed again after leaving the creation screen.
6. Do not put it in source code, committed Compose files, logs, or AI prompts.

See the official [Accessing the Portainer API](https://docs.portainer.io/api/access) guide for screenshots and current UI details. Portainer API access is per-user; the token has the same Portainer permissions as that user.

A Portainer API key inherits its user's permissions. It is not intrinsically read-only. The MCP adapter exposes fixed, code-defined GET operations only, but this does not reduce the upstream privileges of the credential.

**Current authorization limitation:** the validated deployment uses a key belonging to an administrator-level Portainer account. Treat it as high-impact. If the selected Portainer CE release supports a dedicated account with sufficiently narrow visibility/permissions for required operations, prefer it and test it. Do not assume a non-admin role is adequate without testing.

## 6. Configure Portainer in Config Server

Add to private \`homelab-management-mcp.yaml\`:

\`\`\`yaml
homelab:
  docker:
    portainer:
      base-url: https://<portainer-dns-name>:9443
      api-key: <portainer-api-key>
      connect-timeout-ms: 3000
      read-timeout-ms: 8000
      max-response-bytes: <positive-byte-limit>
      environments:
        allowlist:
          - <host-key-one>
          - <host-key-two>
\`\`\`

The base URL must be HTTPS and contain no user info, query, or fragment. The allowlist must contain at least one unique lowercase key matching \`[a-z][a-z0-9-]{0,62}\`. Use limits supported by the deployed application version.

## 7. Validate and troubleshoot

- [ ] Server UI is reachable from private management network.
- [ ] Server certificate SAN matches the URL used by MCP.
- [ ] Local Docker environment is Up.
- [ ] Intended Agent environments are Up.
- [ ] Agent listener is not publicly reachable.
- [ ] API key works from MCP network and is stored privately.
- [ ] Private allowlist contains only intended keys.
- [ ] An unlisted Portainer environment is excluded from MCP results.

| Symptom | Checks |
|---|---|
| Environment Down | Agent state, Agent-to-Server reachability, version compatibility |
| No matching environment | Allowlist key and Portainer environment identity |
| HTTP 401 | API key validity and owning user |
| HTTP 403 | Permissions inherited from API key owner |
| TLS/PKIX failure | Certificate SAN and Java truststore CA |
| Logs unavailable | Logging driver; standard endpoint supports \`json-file\` and \`journald\` |

Do not add shell/exec, sidecar, direct socket, or direct Docker API fallbacks when Portainer cannot provide an operation.
