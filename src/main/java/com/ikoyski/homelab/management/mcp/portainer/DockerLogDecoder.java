package com.ikoyski.homelab.management.mcp.portainer;

import java.nio.charset.StandardCharsets;

/** Decodes Docker's multiplexed stdout/stderr frames, with support for TTY plain-text logs. */
final class DockerLogDecoder {

    private DockerLogDecoder() {
    }

    static String decode(byte[] payload, boolean truncated) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        String multiplexed = decodeMultiplexed(payload, truncated);
        if (multiplexed != null) {
            return multiplexed;
        }

        String plain = new String(payload, StandardCharsets.UTF_8);
        if (truncated) {
            int lastNewline = plain.lastIndexOf('\n');
            return lastNewline < 0 ? "" : plain.substring(0, lastNewline + 1);
        }
        return plain;
    }

    private static String decodeMultiplexed(byte[] bytes, boolean truncated) {
        if (!hasFrameHeader(bytes, 0)) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        int offset = 0;
        int frames = 0;
        while (offset + 8 <= bytes.length && hasFrameHeader(bytes, offset)) {
            long frameLength = ((long) (bytes[offset + 4] & 0xff) << 24)
                    | ((long) (bytes[offset + 5] & 0xff) << 16)
                    | ((long) (bytes[offset + 6] & 0xff) << 8)
                    | (long) (bytes[offset + 7] & 0xff);
            long end = offset + 8L + frameLength;
            if (end > bytes.length) {
                return truncated && frames > 0 ? result.toString() : null;
            }
            String stream = (bytes[offset] & 0xff) == 1 ? "stdout" : "stderr";
            result.append('[').append(stream).append("] ")
                    .append(new String(bytes, offset + 8, (int) frameLength, StandardCharsets.UTF_8));
            offset = (int) end;
            frames++;
        }

        if (frames == 0 || (!truncated && offset != bytes.length)) {
            return null;
        }
        return result.toString();
    }

    private static boolean hasFrameHeader(byte[] bytes, int offset) {
        if (offset + 8 > bytes.length) {
            return false;
        }
        int streamType = bytes[offset] & 0xff;
        return (streamType == 1 || streamType == 2)
                && bytes[offset + 1] == 0
                && bytes[offset + 2] == 0
                && bytes[offset + 3] == 0;
    }
}
