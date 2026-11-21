# Proxmox VE setup

The MCP reads Proxmox inventory through the HTTPS API. It does not use SSH, execute commands on guests, or connect to each VM/LXC individually.

## 1. Create a dedicated API user

In the Proxmox UI, open **Datacenter → Permissions → Users** and add a dedicated service user such as \`<service-user>@<realm>\`. Choose the appropriate realm for your installation (for example PAM or Proxmox VE). Do not reuse a human administrator account or grant this service user administrator privileges.

## 2. Assign the built-in read-only role

Proxmox includes \`PVEAuditor\` for read-only auditing.

1. Open **Datacenter → Permissions → Add → User Permission**.
2. Select the service user.
3. Set **Path** to \`/\` for cluster-wide inventory visibility.
4. Set **Role** to \`PVEAuditor\`.
5. Enable propagation if shown, then save.

If your policy requires narrower scope, validate that all required inventory API calls still work before deployment.

## 3. Create an API token

1. Open **Datacenter → Permissions → API Tokens**.
2. Select the service user and choose **Add**.
3. Use a descriptive token name, e.g. \`inventory\`.
4. Keep **Privilege Separation enabled**.
5. Create the token and copy its secret immediately to a password manager/private secret store. Proxmox displays the secret only once.

Token ID format:

\`\`\`text
<service-user>@<realm>!<token-name>
\`\`\`

The token ID and secret are separate values. Never paste the secret into this public document, an issue, or an AI prompt.

## 4. Assign token permissions

With privilege separation enabled, effective permissions are constrained by both the backing user and the token ACL.

1. Open **Datacenter → Permissions → Add → API Token Permission**.
2. Select the exact API token.
3. Set **Path** to \`/\`.
4. Set **Role** to \`PVEAuditor\`.
5. Enable propagation if shown, then save.

Verify both ACL entries:

| ACL subject | Path | Role |
|---|---|---|
| Dedicated API user | \`/\` | \`PVEAuditor\` |
| Dedicated API token | \`/\` | \`PVEAuditor\` |

Assigning the role only to the user may not be sufficient when token privilege separation is enabled.

## 5. Verify token access

From a trusted workstation, avoid putting the secret in shell history:

\`\`\`bash
export PROXMOX_API_URL="https://<proxmox-dns-name>:8006"
export PROXMOX_TOKEN_ID="<service-user>@<realm>!<token-name>"
read -rsp "Proxmox API token secret: " PROXMOX_TOKEN_SECRET
echo

curl --fail --silent --show-error \\
  --cacert /path/to/private-ca.pem \\
  -H "Authorization: PVEAPIToken=\${PROXMOX_TOKEN_ID}=\${PROXMOX_TOKEN_SECRET}" \\
  "\${PROXMOX_API_URL}/api2/json/version"

unset PROXMOX_TOKEN_SECRET
\`\`\`

Replace the CA path with the trusted issuing CA. If the workstation already trusts it, omit \`--cacert\`. Never use \`-k\` or disable TLS verification. A successful version response confirms authentication/connectivity, not every inventory permission; complete validation through the MCP tools.

## 6. Configure the MCP

In private \`homelab-management-mcp.yaml\`:

\`\`\`yaml
homelab:
  proxmox:
    api-url: https://<proxmox-dns-name>:8006
    token-id: <service-user>@<realm>!<token-name>
    token-secret: <secret-from-private-secret-store>
    connect-timeout-ms: 3000
    read-timeout-ms: 8000
\`\`\`

Keep actual values in the private Config Server or protected secret source.

## 7. What the MCP reads

- Cluster/node status and safe resource metrics.
- QEMU and LXC guest inventory.
- Storage inventory and per-node storage status.
- QEMU guest filesystem information through the Guest Agent where available.

No SSH credentials, guest passwords, guest login accounts, or guest filesystem access are needed. QEMU filesystem metrics depend on the Guest Agent; base guest inventory can work without it.

## Troubleshooting

| Symptom | Checks |
|---|---|
| HTTP 401 | Token ID, secret, realm, token name |
| HTTP 403 | Both user and token ACL grants, privilege separation, path \`/\`, \`PVEAuditor\` |
| TLS/PKIX error | Correct CA in Java truststore and URL/SAN match |
| Partial inventory | API reachability and effective read permissions |
| Missing QEMU filesystem data | Guest Agent installation, enablement, and response |

Do not solve permission errors by granting Administrator or disabling token privilege separation.
