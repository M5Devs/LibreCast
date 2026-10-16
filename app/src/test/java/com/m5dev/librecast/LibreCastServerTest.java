package com.m5dev.librecast;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.enums.Opcode;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.framing.Framedata;
import org.java_websocket.protocols.IProtocol;
import org.junit.Test;

import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class LibreCastServerTest {

    @Test
    public void testServerLifecycle() throws Exception {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);

        LibreCastServer server = new LibreCastServer(0, new LibreCastServer.ServerEventListener() {
            @Override
            public void onServerStarted(int port) {
                started.set(true);
            }

            @Override
            public void onServerStopped() {
                stopped.set(true);
            }

            @Override
            public void onClientConnected(String clientIp) {}

            @Override
            public void onClientDisconnected(String clientIp) {}

            @Override
            public void onVideoChunkReceived(ByteBuffer chunk) {}

            @Override
            public void onError(Exception ex) {}
        });

        server.start();
        for (int i = 0; i < 20; i++) {
            if (started.get()) break;
            Thread.sleep(100);
        }
        assertTrue("Server should have started", started.get());

        server.stop();
        assertTrue("Server should have stopped", stopped.get());
    }

    @Test
    public void testSingleClientEnforcementAndBinaryChunk() {
        AtomicReference<String> connectedIp = new AtomicReference<>("");
        AtomicReference<ByteBuffer> receivedChunk = new AtomicReference<>(null);

        LibreCastServer server = new LibreCastServer(0, new LibreCastServer.ServerEventListener() {
            @Override
            public void onServerStarted(int port) {}

            @Override
            public void onServerStopped() {}

            @Override
            public void onClientConnected(String clientIp) {
                connectedIp.set(clientIp);
            }

            @Override
            public void onClientDisconnected(String clientIp) {}

            @Override
            public void onVideoChunkReceived(ByteBuffer chunk) {
                receivedChunk.set(chunk);
            }

            @Override
            public void onError(Exception ex) {}
        });

        DummyWebSocket conn1 = new DummyWebSocket("192.168.1.10");
        DummyWebSocket conn2 = new DummyWebSocket("192.168.1.20");

        // First client connects
        server.onOpen(conn1, null);
        assertEquals(conn1, server.getActiveConnection());
        assertEquals("192.168.1.10", connectedIp.get());

        // Send binary data from active client
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{0x00, 0x01, 0x02, 0x03});
        server.onMessage(conn1, buffer);
        assertNotNull("Binary chunk should be received", receivedChunk.get());
        assertEquals(4, receivedChunk.get().remaining());

        // Second client connects, replacing first
        server.onOpen(conn2, null);
        assertEquals(conn2, server.getActiveConnection());
        assertTrue("First connection should be closed", conn1.isClosed());
        assertEquals("192.168.1.20", connectedIp.get());

        // Send binary data from old client conn1 should be ignored
        receivedChunk.set(null);
        server.onMessage(conn1, buffer);
        assertNull("Binary chunk from inactive client should be ignored", receivedChunk.get());

        // Send binary data from active client conn2 should succeed
        server.onMessage(conn2, buffer);
        assertNotNull("Binary chunk from active client should be received", receivedChunk.get());
    }

    private static class DummyWebSocket implements WebSocket {
        private final String ip;
        private boolean closed = false;

        public DummyWebSocket(String ip) {
            this.ip = ip;
        }

        @Override
        public void close(int code, String message) {
            this.closed = true;
        }

        @Override
        public void close(int code) {
            this.closed = true;
        }

        @Override
        public void close() {
            this.closed = true;
        }

        @Override
        public void closeConnection(int code, String message) {
            this.closed = true;
        }

        @Override
        public void send(String text) {}

        @Override
        public void send(ByteBuffer bytes) {}

        @Override
        public void send(byte[] bytes) {}

        @Override
        public void sendFrame(Framedata framedata) {}

        @Override
        public void sendFrame(Collection<Framedata> frames) {}

        @Override
        public void sendPing() {}

        @Override
        public void sendFragmentedFrame(Opcode op, ByteBuffer buffer, boolean fin) {}

        @Override
        public boolean hasBufferedData() {
            return false;
        }

        @Override
        public InetSocketAddress getRemoteSocketAddress() {
            return new InetSocketAddress(ip, 12345);
        }

        @Override
        public InetSocketAddress getLocalSocketAddress() {
            return new InetSocketAddress("0.0.0.0", 8080);
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public boolean isClosing() {
            return false;
        }

        @Override
        public boolean isFlushAndClose() {
            return false;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public Draft getDraft() {
            return null;
        }

        @Override
        public ReadyState getReadyState() {
            return closed ? ReadyState.CLOSED : ReadyState.OPEN;
        }

        @Override
        public String getResourceDescriptor() {
            return "";
        }

        @Override
        public <T> void setAttachment(T attachment) {}

        @Override
        public <T> T getAttachment() {
            return null;
        }

        @Override
        public boolean hasSSLSupport() {
            return false;
        }

        @Override
        public SSLSession getSSLSession() {
            return null;
        }

        @Override
        public IProtocol getProtocol() {
            return null;
        }
    }
}
