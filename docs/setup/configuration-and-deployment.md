# Private configuration and deployment

## 1. Spring Config Server

The application uses Spring Cloud Config Client. Bootstrap configuration is in \`src/main/resources/application.yaml\` and imports Config Server using:

- \`CONFIG_SERVER_URL\`
- \`CONFIG_USER\`
- \`CONFIG_PASSWORD\`

The Spring application name is \`homelab-management-mcp\`; configure private application file \`homelab-management-mcp.yaml\`.

Keep the Config Server repository private. Never put live endpoints, private IPs, host mappings, central UUIDs, API credentials, certificates, truststores, or passwords in this public repository.

## 2. Configuration shape

Placeholder template only; replace privately and validate property names against the exact application version:

\`\`\`yaml
homelab:
  security:
    allowed-users:
      - <authorized-central-webtools-user-uuid>
  discovery:
    targets:
      - <non-sensitive-display-label>
    mode: read-only
  proxmox:
    api-url: https://<proxmox-dns-name>:8006
    token-id: <service-user>@<realm>!<token-name>
    token-secret: <proxmox-api-token-secret>
    connect-timeout-ms: 3000
    read-timeout-ms: 8000
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

Notes:
- \`homelab.security.allowed-users\` lists central Webtools UUIDs; missing/empty denies access.
- Proxmox and Portainer credentials belong only in private configuration.
- Docker host keys must be unique lowercase keys matching \`[a-z][a-z0-9-]{0,62}\`.
- \`homelab.discovery.targets\` is informational output for \`get_discovery_scope\`, not an endpoint list, SSH target list, or authorization boundary.
- \`homelab.discovery.mode\` is present for compatibility/documentation; the tool currently reports \`read-only\`.
- OAuth issuer/audience and resource-server properties must match the private Webtools Auth deployment; do not guess them.

## 3. Runtime environment file

Example only; store outside Git and restrict permissions:

\`\`\`dotenv
CONFIG_SERVER_URL=http://<private-config-server-name>:<port>
CONFIG_USER=<config-server-username>
CONFIG_PASSWORD=<config-server-password>
JAVA_TRUSTSTORE_PASSWORD=<truststore-password>
\`\`\`

\`\`\`bash
chmod 600 .env
\`\`\`

Prefer protected deployment secrets over plaintext files. Never print environment values.

## 4. Docker Compose template

Adapt network, image tag, truststore path, and health-check tooling. Do not publish container ports on the host.

\`\`\`yaml
services:
  homelab-management-mcp:
    image: ikoyski/homelab-management-mcp:<approved-tag>
    container_name: homelab-management-mcp
    restart: unless-stopped
    env_file:
      - .env
    environment:
      MANAGEMENT_PORT: "8082"
      JAVA_TOOL_OPTIONS: >-
        -Djavax.net.ssl.trustStore=/run/secrets/homelab-truststore.p12
        -Djavax.net.ssl.trustStoreType=PKCS12
        -Djavax.net.ssl.trustStorePassword=\${JAVA_TRUSTSTORE_PASSWORD}
    volumes:
      - /private/path/homelab-truststore.p12:/run/secrets/homelab-truststore.p12:ro
    expose:
      - "8083"
      - "8082"
    networks:
      - <existing-private-management-network>
    security_opt:
      - no-new-privileges:true

networks:
  <existing-private-management-network>:
    external: true
\`\`\`

The current image runs as non-root UID/GID 10001. MCP listens on 8083; Actuator uses separate management port 8082. Attach the container to a private Docker network that can reach Config Server, Proxmox, Portainer, and tunnel/ingress as required.

Do not add host-published \`ports:\` for 8082/8083, Docker socket mounts, direct Agent endpoints, or direct Docker Engine endpoints.

If Cloudflare Tunnel runs in another container, attach it to the same private network and route its origin to \`http://homelab-management-mcp:8083\`. Never route to port 8082.

## 5. Ingress

Route the external MCP hostname to container port 8083 and path \`/mcp\`. Preserve OAuth flow and bearer-token headers.

Do not expose Actuator, Config Server, Proxmox, Portainer, Agent listener, or Docker Engine API publicly. A tunnel is a network path, not an authentication/authorization substitute.

## 6. Deploy and verify

From the private Compose directory:

\`\`\`bash
docker compose config --quiet
docker compose pull homelab-management-mcp
docker compose up -d --no-deps --force-recreate --pull never homelab-management-mcp
docker compose ps homelab-management-mcp
docker compose logs --tail=80 homelab-management-mcp
\`\`\`

Check internal health from a container on the same private Docker network:

\`\`\`bash
docker run --rm --network <existing-private-management-network> \\
  curlimages/curl:latest --fail --silent --show-error \\
  http://homelab-management-mcp:8082/actuator/health
\`\`\`

Actuator must not be reachable through public ingress. An unauthenticated request to protected MCP should be rejected (normally HTTP 401 with an OAuth bearer challenge).

## Checklist and troubleshooting

- [ ] Config Server app name/file and bootstrap credentials are correct.
- [ ] Proxmox token and Portainer key are correct and stored privately.
- [ ] Central identity and Docker host allowlists contain only intended entries.
- [ ] URLs match certificate SANs; truststore includes public roots and private CAs.
- [ ] Truststore is read-only and readable by UID/GID 10001.
- [ ] MCP has no Docker socket mount and no host-published ports.
- [ ] Tunnel routes only to 8083; 8082 remains private.
- [ ] Internal health succeeds and unauthenticated MCP access is rejected.

| Symptom | Likely area |
|---|---|
| Config import fails | URL, credentials, application name, network |
| Startup fails | Missing/invalid private property |
| TLS error | CA missing or URL/SAN mismatch |
| Tools fail while health is UP | Upstream credentials, allowlists, reachability |
| MCP returns 401/403 | OAuth issuer/audience, claims, central-user allowlist |
| Tunnel returns 502 | Tunnel network, origin name/port, MCP availability |

Do not fix TLS by disabling verification, authorization by broadening permissions blindly, or Docker discovery by mounting the socket into MCP.
