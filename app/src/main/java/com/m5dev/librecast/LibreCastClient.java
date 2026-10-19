package com.m5dev.librecast;

import android.util.Log;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class LibreCastClient extends WebSocketClient {

    private static final String TAG = "LibreCastClient";

    public interface ClientListener {
        void onConnected();
        void onDisconnected();
        void onError(Exception ex);
    }

    private ClientListener listener;

    public LibreCastClient(URI serverUri) {
        super(serverUri);
    }

    public LibreCastClient(URI serverUri, ClientListener listener) {
        super(serverUri);
        this.listener = listener;
    }

    public void setListener(ClientListener listener) {
        this.listener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logI(TAG, "WebSocket connected to " + getURI());
        if (listener != null) {
            listener.onConnected();
        }
    }

    @Override
    public void onMessage(String message) {
        logD(TAG, "Received message: " + message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logI(TAG, "WebSocket closed: " + reason + " (code: " + code + ")");
        if (listener != null) {
            listener.onDisconnected();
        }
    }

    @Override
    public void onError(Exception ex) {
        logE(TAG, "WebSocket error", ex);
        if (listener != null) {
            listener.onError(ex);
        }
    }

    public void sendFrame(byte[] data) {
        if (isOpen() && data != null && data.length > 0) {
            try {
                send(data);
            } catch (Exception e) {
                logE(TAG, "Error sending frame over WebSocket", e);
            }
        }
    }

    private static void logI(String tag, String msg) {
        try {
            Log.i(tag, msg);
        } catch (Throwable t) {
            System.out.println("[" + tag + "] " + msg);
        }
    }

    private static void logD(String tag, String msg) {
        try {
            Log.d(tag, msg);
        } catch (Throwable t) {
            System.out.println("[" + tag + "] " + msg);
        }
    }

    private static void logE(String tag, String msg, Throwable t) {
        try {
            Log.e(tag, msg, t);
        } catch (Throwable ignored) {
            System.err.println("[" + tag + "] " + msg);
        }
    }
}
