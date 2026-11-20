package com.ikoyski.homelab.management.mcp.portainer.client;

/** Internal bounded response bytes from the Docker logs endpoint. */
public record DockerLogPayload(byte[] bytes, boolean truncated) {
    public DockerLogPayload {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
