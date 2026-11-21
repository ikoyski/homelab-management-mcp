# TLS certificates and Java truststore

The MCP uses HTTPS with normal certificate-chain and hostname/IP verification for Proxmox and Portainer. The homelab deployment uses a private internal root CA (self-signed) to issue service certificates; the Portainer server certificate must include the address used by the MCP in its SAN. Private certificates must be trusted by Java. Never work around trust errors by disabling verification.

## 1. Use a private CA

Recommended pattern:

1. Create or use an offline/private root CA controlled by the operator.
2. Keep the CA private key offline or in a protected CA system. The MCP does not need that private key.
3. Issue separate server certificates for Proxmox and Portainer.
4. Include the exact DNS names and/or IP addresses used by clients in each certificate's **Subject Alternative Name (SAN)**.
5. Install each service certificate and private key only on its corresponding service.
6. Distribute only public root/intermediate CA certificates to clients.

A Common Name alone is not sufficient for modern hostname verification. If MCP connects to \`https://<service-ip>:<port>\`, that exact IP must be an IP SAN. If it connects by DNS, that DNS name must be a DNS SAN. Prefer stable internal DNS names.

## 2. Example server certificate request

This template creates a private key and CSR for an existing CA to sign. Replace placeholders privately; this does not create a CA or sign the certificate.

\`\`\`ini
# server-san.cnf
subjectAltName = @alt_names
extendedKeyUsage = serverAuth
keyUsage = critical, digitalSignature, keyEncipherment

[alt_names]
DNS.1 = <service-dns-name>
IP.1 = <service-ip-address>
\`\`\`

\`\`\`bash
openssl req -new -newkey rsa:3072 -nodes \\
  -keyout <service-name>.key \\
  -out <service-name>.csr \\
  -subj "/CN=<service-dns-name>"
\`\`\`

Have the private CA sign the CSR using the SAN extensions. Protect the private key and install it only on the relevant service. Do not commit keys or site-specific certificates. A directly self-signed server certificate still needs correct SANs; for multiple services, a private CA with separate leaf certificates is easier to renew.

## 3. Verify certificate identity and chain

\`\`\`bash
openssl s_client \\
  -connect <service-dns-name>:<port> \\
  -servername <service-dns-name> \\
  -CAfile <private-root-or-intermediate-ca.pem> \\
  -verify_return_error </dev/null

openssl x509 -in <service-certificate.pem> \\
  -noout -subject -issuer -dates -ext subjectAltName
\`\`\`

For IP-based URLs, verify the certificate includes that IP as an IP SAN. Do not use \`curl -k\`, \`--insecure\`, trust-all SSL contexts, or hostname-verification bypasses.

## 4. Build a truststore preserving public roots

The truststore must contain the JRE's normal public CA roots **plus** private CA certificates required for Proxmox and Portainer. Do not create a truststore containing only the private CA if the application also needs public TLS endpoints.

On a trusted administration host with compatible Java 21:

\`\`\`bash
set -eu
JAVA_HOME="<path-to-java-21>"
TRUSTSTORE_DIR="<private-truststore-output-directory>"
TRUSTSTORE="$TRUSTSTORE_DIR/homelab-truststore.p12"

mkdir -p "$TRUSTSTORE_DIR"
cp "$JAVA_HOME/lib/security/cacerts" "$TRUSTSTORE"
chmod 600 "$TRUSTSTORE"
read -rsp "New truststore password: " TRUSTSTORE_PASSWORD
echo

keytool -importcert -noprompt \\
  -alias homelab-private-root-ca \\
  -file <private-root-ca.pem> \\
  -keystore "$TRUSTSTORE" \\
  -storetype PKCS12 \\
  -storepass "$TRUSTSTORE_PASSWORD"

# Import other required CAs using distinct aliases.
keytool -list -keystore "$TRUSTSTORE" -storetype PKCS12 \\
  -storepass "$TRUSTSTORE_PASSWORD"
unset TRUSTSTORE_PASSWORD
\`\`\`

Verify each CA fingerprint independently before import. Adapt the source cacerts path if the Java distribution differs; validate against the exact Java 21 image used for deployment.

## 5. Mount read-only into the MCP container

Keep the truststore outside this public repository:

\`\`\`yaml
services:
  homelab-management-mcp:
    volumes:
      - /private/path/homelab-truststore.p12:/run/secrets/homelab-truststore.p12:ro
    environment:
      JAVA_TOOL_OPTIONS: >-
        -Djavax.net.ssl.trustStore=/run/secrets/homelab-truststore.p12
        -Djavax.net.ssl.trustStoreType=PKCS12
        -Djavax.net.ssl.trustStorePassword=\${JAVA_TRUSTSTORE_PASSWORD}
\`\`\`

Supply \`JAVA_TRUSTSTORE_PASSWORD\` through a protected runtime secret. Ensure the truststore is readable by the non-root application process (UID/GID 10001 in the current image), while remaining read-only. Never commit the password or truststore.

## 6. Renewal

When a leaf certificate is renewed under the same CA, the truststore normally does not need to change. When the CA changes:

1. Obtain the new CA certificate through a trusted channel.
2. Verify its fingerprint independently.
3. Import it into a replacement truststore while preserving public roots and still-required CA entries.
4. Mount the replacement read-only.
5. Restart/recreate MCP in a maintenance window.
6. Verify Proxmox/Portainer connectivity and authenticated inventory.
7. Remove the old CA only after all dependent services have migrated.

Keep CA/private service keys, truststore passwords, generated truststores, and actual fingerprints in private PKI/deployment records.
