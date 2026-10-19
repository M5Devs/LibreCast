package com.m5dev.librecast;

import org.junit.Test;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class LibreCastClientTest {

    @Test
    public void testClientInitializationAndCallbacks() throws Exception {
        URI testUri = new URI("ws://127.0.0.1:8080");
        AtomicBoolean connected = new AtomicBoolean(false);
        AtomicBoolean disconnected = new AtomicBoolean(false);
        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        LibreCastClient client = new LibreCastClient(testUri, new LibreCastClient.ClientListener() {
            @Override
            public void onConnected() {
                connected.set(true);
            }

            @Override
            public void onDisconnected() {
                disconnected.set(true);
            }

            @Override
            public void onError(Exception ex) {
                errorOccurred.set(true);
            }
        });

        assertEquals(testUri, client.getURI());

        // Simulate callbacks
        client.onOpen(null);
        assertTrue("onConnected should be invoked", connected.get());

        client.onClose(1000, "Normal closure", false);
        assertTrue("onDisconnected should be invoked", disconnected.get());

        client.onError(new Exception("Test error"));
        assertTrue("onError should be invoked", errorOccurred.get());
    }

    @Test
    public void testSendFrameWhenClosedDoesNotThrow() throws Exception {
        URI testUri = new URI("ws://127.0.0.1:8080");
        LibreCastClient client = new LibreCastClient(testUri);

        byte[] frameData = new byte[]{0x00, 0x00, 0x00, 0x01, 0x67};
        // Should handle safely without throwing WebsocketNotConnectedException
        client.sendFrame(frameData);
    }
}
