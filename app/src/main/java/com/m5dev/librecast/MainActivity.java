package com.m5dev.librecast;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, ReceiverService.VideoChunkListener, H264Decoder.DecoderListener {

    private static final String TAG = "MainActivity";

    private SurfaceView surfaceView;
    private LinearLayout layoutStatus;
    private ImageView imageQrCode;
    private TextView textServerStatus;
    private TextView textConnectionStatus;

    private H264Decoder decoder;
    private ReceiverService receiverService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ReceiverService.LocalBinder binder = (ReceiverService.LocalBinder) service;
            receiverService = binder.getService();
            isBound = true;
            if (receiverService != null) {
                receiverService.setVideoChunkListener(MainActivity.this);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (receiverService != null) {
                receiverService.setVideoChunkListener(null);
            }
            receiverService = null;
            isBound = false;
        }
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ReceiverService.ACTION_STATUS_UPDATE.equals(intent.getAction())) {
                updateUI(intent);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on for TV display and set full screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surface_view);
        layoutStatus = findViewById(R.id.layout_status);
        imageQrCode = findViewById(R.id.image_qr_code);
        textServerStatus = findViewById(R.id.text_server_status);
        textConnectionStatus = findViewById(R.id.text_connection_status);

        surfaceView.getHolder().addCallback(this);

        startReceiverService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ReceiverService.ACTION_STATUS_UPDATE);
        registerReceiver(statusReceiver, filter);

        Intent serviceIntent = new Intent(this, ReceiverService.class);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        // Request current status
        Intent queryIntent = new Intent(this, ReceiverService.class);
        queryIntent.setAction(ReceiverService.ACTION_QUERY_STATUS);
        startService(queryIntent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            if (receiverService != null) {
                receiverService.setVideoChunkListener(null);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
        try {
            unregisterReceiver(statusReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    private void startReceiverService() {
        Intent serviceIntent = new Intent(this, ReceiverService.class);
        serviceIntent.setAction(ReceiverService.ACTION_START_SERVER);
        startService(serviceIntent);
    }

    private void updateUI(Intent intent) {
        boolean isRunning = intent.getBooleanExtra(ReceiverService.EXTRA_IS_RUNNING, false);
        String serverIp = intent.getStringExtra(ReceiverService.EXTRA_SERVER_IP);
        int serverPort = intent.getIntExtra(ReceiverService.EXTRA_SERVER_PORT, LibreCastServer.DEFAULT_PORT);
        boolean isClientConnected = intent.getBooleanExtra(ReceiverService.EXTRA_IS_CLIENT_CONNECTED, false);
        String clientIp = intent.getStringExtra(ReceiverService.EXTRA_CLIENT_IP);

        if (isRunning) {
            if (serverIp == null || serverIp.isEmpty() || "0.0.0.0".equals(serverIp)) {
                serverIp = NetworkUtils.getLocalIpAddress(this);
            }
            textServerStatus.setText(getString(R.string.server_ready_format, serverIp, serverPort));

            if (serverIp != null && !serverIp.isEmpty() && !"0.0.0.0".equals(serverIp)) {
                String wsUri = "ws://" + serverIp + ":" + serverPort;
                Bitmap qrBitmap = QRCodeUtils.generateQRCode(wsUri, 250, 250);
                if (qrBitmap != null && imageQrCode != null) {
                    imageQrCode.setImageBitmap(qrBitmap);
                    imageQrCode.setVisibility(View.VISIBLE);
                }
            }
        } else {
            textServerStatus.setText(getString(R.string.server_stopped));
            if (imageQrCode != null) {
                imageQrCode.setVisibility(View.GONE);
            }
        }

        if (isClientConnected) {
            textConnectionStatus.setText(getString(R.string.client_connected_format, clientIp != null ? clientIp : ""));
        } else {
            textConnectionStatus.setText(getString(R.string.waiting_for_connection));
            // Show status UI again when client disconnects
            showStatusUI();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void showStatusUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (layoutStatus != null) {
                    layoutStatus.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void hideStatusUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (layoutStatus != null) {
                    layoutStatus.setVisibility(View.GONE);
                }
            }
        });
    }

    // SurfaceHolder.Callback

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "Surface created, initializing H264Decoder");
        if (decoder != null) {
            decoder.stop();
        }
        decoder = new H264Decoder(holder.getSurface(), this);
        decoder.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "Surface changed: width=" + width + ", height=" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "Surface destroyed, stopping H264Decoder");
        if (decoder != null) {
            decoder.stop();
            decoder = null;
        }
    }

    // ReceiverService.VideoChunkListener

    @Override
    public void onVideoChunkReceived(ByteBuffer chunk) {
        if (decoder != null) {
            decoder.feedFrame(chunk);
        }
    }

    // H264Decoder.DecoderListener

    @Override
    public void onFirstFrameRendered() {
        Log.i(TAG, "First video frame rendered");
        hideStatusUI();
    }

    @Override
    public void onOutputFormatChanged(MediaFormat format) {
        Log.i(TAG, "Decoder output format changed: " + format);
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "Decoder error", e);
    }
}
