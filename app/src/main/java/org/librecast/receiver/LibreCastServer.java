package org.librecast.receiver;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class LibreCastServer extends WebSocketServer {

    public interface ServerEventListener {
        void onServerStarted(int port);
        void onServerStopped();
        void onClientConnected(String clientIp);
        void onClientDisconnected(String clientIp);
        void onVideoChunkReceived(ByteBuffer chunk);
        void onError(Exception ex);
    }

    public static final int DEFAULT_PORT = 8080;

    private ServerEventListener listener;
    private WebSocket activeConnection;

    public LibreCastServer(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
    }

    public LibreCastServer(int port, ServerEventListener listener) {
        this(port);
        this.listener = listener;
    }

    public void setListener(ServerEventListener listener) {
        this.listener = listener;
    }

    public synchronized WebSocket getActiveConnection() {
        return activeConnection;
    }

    @Override
    public synchronized void onOpen(WebSocket conn, ClientHandshake handshake) {
        if (activeConnection != null && activeConnection != conn && activeConnection.isOpen()) {
            try {
                activeConnection.close(1000, "Replaced by new client connection");
            } catch (Exception ignored) {
            }
        }
        activeConnection = conn;
        String clientIp = getClientIp(conn);
        if (listener != null) {
            listener.onClientConnected(clientIp);
        }
    }

    @Override
    public synchronized void onClose(WebSocket conn, int code, String reason, boolean remote) {
        if (conn == activeConnection) {
            String clientIp = getClientIp(conn);
            activeConnection = null;
            if (listener != null) {
                listener.onClientDisconnected(clientIp);
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // Text messages handled or ignored
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        WebSocket currentActive;
        synchronized (this) {
            currentActive = activeConnection;
        }
        if (conn != null && conn == currentActive) {
            if (listener != null) {
                listener.onVideoChunkReceived(message);
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (listener != null) {
            listener.onError(ex);
        }
    }

    @Override
    public void onStart() {
        if (listener != null) {
            listener.onServerStarted(getPort());
        }
    }

    @Override
    public void stop() throws InterruptedException {
        synchronized (this) {
            if (activeConnection != null) {
                try {
                    activeConnection.close(1000, "Server stopping");
                } catch (Exception ignored) {
                }
                activeConnection = null;
            }
        }
        super.stop();
        if (listener != null) {
            listener.onServerStopped();
        }
    }

    private String getClientIp(WebSocket conn) {
        if (conn != null && conn.getRemoteSocketAddress() != null && conn.getRemoteSocketAddress().getAddress() != null) {
            return conn.getRemoteSocketAddress().getAddress().getHostAddress();
        }
        return "Unknown";
    }
}
