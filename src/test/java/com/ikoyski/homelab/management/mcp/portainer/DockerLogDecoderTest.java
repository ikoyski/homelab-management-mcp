package com.ikoyski.homelab.management.mcp.portainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DockerLogDecoderTest {

    @Test
    void preservesPlainTextForTtyEnabledContainers() {
        byte[] payload = "plain tty log\nsecond line\n".getBytes(StandardCharsets.UTF_8);

        assertThat(DockerLogDecoder.decode(payload, false))
                .isEqualTo("plain tty log\nsecond line\n");
    }

    @Test
    void decodesMultiplexedStdoutAndStderrFramesInOrder() {
        byte[] stdout = frame(1, "stdout line\n");
        byte[] stderr = frame(2, "stderr line\n");
        byte[] payload = new byte[stdout.length + stderr.length];
        System.arraycopy(stdout, 0, payload, 0, stdout.length);
        System.arraycopy(stderr, 0, payload, stdout.length, stderr.length);

        assertThat(DockerLogDecoder.decode(payload, false))
                .isEqualTo("[stdout] stdout line\n[stderr] stderr line\n");
    }

    @Test
    void dropsAnIncompleteTrailingFrameWhenTheResponseWasByteTruncated() {
        byte[] complete = frame(1, "complete line\n");
        byte[] partial = frame(2, "partial line that will not fit\n");
        byte[] payload = new byte[complete.length + 10];
        System.arraycopy(complete, 0, payload, 0, complete.length);
        System.arraycopy(partial, 0, payload, complete.length, 10);

        assertThat(DockerLogDecoder.decode(payload, true))
                .isEqualTo("[stdout] complete line\n");
    }

    private static byte[] frame(int streamType, String value) {
        byte[] payload = value.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[payload.length + 8];
        frame[0] = (byte) streamType;
        frame[4] = (byte) (payload.length >>> 24);
        frame[5] = (byte) (payload.length >>> 16);
        frame[6] = (byte) (payload.length >>> 8);
        frame[7] = (byte) payload.length;
        System.arraycopy(payload, 0, frame, 8, payload.length);
        return frame;
    }
}
