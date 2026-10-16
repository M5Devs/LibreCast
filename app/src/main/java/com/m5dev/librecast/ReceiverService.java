package com.m5dev.librecast;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.nio.ByteBuffer;

public class ReceiverService extends Service implements LibreCastServer.ServerEventListener {

    private static final String TAG = "ReceiverService";

    public interface VideoChunkListener {
        void onVideoChunkReceived(ByteBuffer chunk);
    }

    public static final String ACTION_STATUS_UPDATE = "com.m5dev.librecast.ACTION_STATUS_UPDATE";
    public static final String ACTION_START_SERVER = "com.m5dev.librecast.ACTION_START_SERVER";
    public static final String ACTION_STOP_SERVER = "com.m5dev.librecast.ACTION_STOP_SERVER";
    public static final String ACTION_QUERY_STATUS = "com.m5dev.librecast.ACTION_QUERY_STATUS";

    public static final String EXTRA_IS_RUNNING = "extra_is_running";
    public static final String EXTRA_SERVER_IP = "extra_server_ip";
    public static final String EXTRA_SERVER_PORT = "extra_server_port";
    public static final String EXTRA_IS_CLIENT_CONNECTED = "extra_is_client_connected";
    public static final String EXTRA_CLIENT_IP = "extra_client_ip";
    public static final String EXTRA_ERROR_MESSAGE = "extra_error_message";

    private final IBinder binder = new LocalBinder();
    private LibreCastServer server;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;

    private boolean isRunning = false;
    private boolean isStarting = false;
    private String serverIp = "0.0.0.0";
    private int serverPort = LibreCastServer.DEFAULT_PORT;
    private boolean isClientConnected = false;
    private String clientIp = "";

    private volatile VideoChunkListener videoChunkListener;

    public class LocalBinder extends Binder {
        public ReceiverService getService() {
            return ReceiverService.this;
        }
    }

    public void setVideoChunkListener(VideoChunkListener listener) {
        this.videoChunkListener = listener;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        acquireLocks();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_STOP_SERVER.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_QUERY_STATUS.equals(action)) {
                broadcastStatus();
            } else if (ACTION_START_SERVER.equals(action)) {
                if (!isRunning && !isStarting) {
                    startWebSocketServer();
                } else {
                    broadcastStatus();
                }
            }
        } else {
            if (!isRunning && !isStarting) {
                startWebSocketServer();
            } else {
                broadcastStatus();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void acquireLocks() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LibreCast:WifiLock");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire WifiLock", e);
        }

        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LibreCast:WakeLock");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire WakeLock", e);
        }
    }

    private void releaseLocks() {
        if (wifiLock != null && wifiLock.isHeld()) {
            try {
                wifiLock.release();
            } catch (Exception ignored) {
            }
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
        }
    }

    private synchronized void startWebSocketServer() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
            }
        }
        isStarting = true;
        serverIp = NetworkUtils.getLocalIpAddress(this);
        serverPort = LibreCastServer.DEFAULT_PORT;
        server = new LibreCastServer(serverPort, this);
        server.start();
    }

    private synchronized void stopWebSocketServer() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping server", e);
            }
            server = null;
        }
        isStarting = false;
        isRunning = false;
        isClientConnected = false;
        clientIp = "";
        broadcastStatus();
    }

    public synchronized void broadcastStatus() {
        Intent intent = new Intent(ACTION_STATUS_UPDATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_IS_RUNNING, isRunning);
        intent.putExtra(EXTRA_SERVER_IP, serverIp);
        intent.putExtra(EXTRA_SERVER_PORT, serverPort);
        intent.putExtra(EXTRA_IS_CLIENT_CONNECTED, isClientConnected);
        intent.putExtra(EXTRA_CLIENT_IP, clientIp);
        sendBroadcast(intent);
    }

    // LibreCastServer.ServerEventListener Callbacks

    @Override
    public void onServerStarted(int port) {
        isStarting = false;
        isRunning = true;
        serverPort = port;
        serverIp = NetworkUtils.getLocalIpAddress(this);
        Log.i(TAG, "WebSocket server started at ws://" + serverIp + ":" + serverPort);
        broadcastStatus();
    }

    @Override
    public void onServerStopped() {
        isStarting = false;
        isRunning = false;
        isClientConnected = false;
        clientIp = "";
        Log.i(TAG, "WebSocket server stopped");
        broadcastStatus();
    }

    @Override
    public void onClientConnected(String ip) {
        isClientConnected = true;
        clientIp = ip;
        Log.i(TAG, "Client connected: " + ip);
        broadcastStatus();
    }

    @Override
    public void onClientDisconnected(String ip) {
        isClientConnected = false;
        clientIp = "";
        Log.i(TAG, "Client disconnected: " + ip);
        broadcastStatus();
    }

    @Override
    public void onVideoChunkReceived(ByteBuffer chunk) {
        VideoChunkListener listener = this.videoChunkListener;
        if (listener != null) {
            listener.onVideoChunkReceived(chunk);
        }
    }

    @Override
    public void onError(Exception ex) {
        Log.e(TAG, "WebSocket server error", ex);
        Intent intent = new Intent(ACTION_STATUS_UPDATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_IS_RUNNING, isRunning);
        intent.putExtra(EXTRA_SERVER_IP, serverIp);
        intent.putExtra(EXTRA_SERVER_PORT, serverPort);
        intent.putExtra(EXTRA_IS_CLIENT_CONNECTED, isClientConnected);
        intent.putExtra(EXTRA_CLIENT_IP, clientIp);
        if (ex != null) {
            intent.putExtra(EXTRA_ERROR_MESSAGE, ex.getMessage());
        }
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        stopWebSocketServer();
        releaseLocks();
        super.onDestroy();
    }
}
