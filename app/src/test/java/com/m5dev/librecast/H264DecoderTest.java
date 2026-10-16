package com.m5dev.librecast;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertFalse;

public class H264DecoderTest {

    @Test
    public void testDecoderInitialState() {
        H264Decoder decoder = new H264Decoder(null);
        assertFalse(decoder.isRunning());
    }

    @Test
    public void testFeedFrameWhenNotRunning() {
        H264Decoder decoder = new H264Decoder(null);
        byte[] spsNal = new byte[]{0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x0a};
        decoder.feedFrame(spsNal);
        decoder.feedFrame(ByteBuffer.wrap(spsNal));
        assertFalse(decoder.isRunning());
    }
}
