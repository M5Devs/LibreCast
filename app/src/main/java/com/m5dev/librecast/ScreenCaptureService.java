package com.m5dev.librecast;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import androidx.core.app.NotificationCompat;

import java.net.URI;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service implements LibreCastClient.ClientListener {

    private static final String TAG = "ScreenCaptureService";

    public static final String ACTION_START = "com.m5dev.librecast.action.START_CAST";
    public static final String ACTION_STOP = "com.m5dev.librecast.action.STOP_CAST";
    public static final String ACTION_STATUS_CHANGED = "com.m5dev.librecast.action.CAST_STATUS_CHANGED";

    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_DATA = "extra_result_data";
    public static final String EXTRA_TARGET_URI = "extra_target_uri";

    public static final String EXTRA_IS_CASTING = "extra_is_casting";
    public static final String EXTRA_STATUS_MESSAGE = "extra_status_message";

    private static final String CHANNEL_ID = "LibreCastCaptureChannel";
    private static final int NOTIFICATION_ID = 1001;

    private static boolean isRunning = false;

    private MediaProjection mediaProjection;
    private MediaCodec encoder;
    private VirtualDisplay virtualDisplay;
    private LibreCastClient client;
    private Thread encoderThread;

    private volatile boolean isEncoding = false;

    public static boolean isCasting() {
        return isRunning;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
                Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
                String targetUri = intent.getStringExtra(EXTRA_TARGET_URI);

                if (resultCode != 0 && resultData != null && targetUri != null) {
                    startForegroundWithNotification();
                    startCasting(resultCode, resultData, targetUri);
                } else {
                    Log.e(TAG, "Invalid parameters for starting cast");
                    stopSelf();
                }
            } else if (ACTION_STOP.equals(action)) {
                stopCasting();
            }
        }
        return START_NOT_STICKY;
    }

    private void startForegroundWithNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("LibreCast")
                .setContentText("Screen casting in progress...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "LibreCast Screen Capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private synchronized void startCasting(int resultCode, Intent resultData, String targetUriStr) {
        if (isRunning) {
            return;
        }
        isRunning = true;
        notifyStatus(true, "Connecting to target TV...");

        try {
            URI uri = new URI(targetUriStr);
            client = new LibreCastClient(uri, this);
            client.connect();

            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (projectionManager != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
            }

            if (mediaProjection == null) {
                Log.e(TAG, "Failed to obtain MediaProjection");
                stopCasting();
                return;
            }

            setupEncoderAndVirtualDisplay();

        } catch (Exception e) {
            Log.e(TAG, "Error starting cast", e);
            stopCasting();
        }
    }

    private void setupEncoderAndVirtualDisplay() {
        try {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = 1280;
            int height = 720;
            if (metrics.widthPixels < metrics.heightPixels) {
                width = 720;
                height = 1280;
            }
            int densityDpi = metrics.densityDpi > 0 ? metrics.densityDpi : DisplayMetrics.DENSITY_HIGH;

            MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 3000000); // 3 Mbps
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30); // 30 fps
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1 sec

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
                format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LATENCY, 0);
            }

            encoder = MediaCodec.createEncoderByType("video/avc");
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface inputSurface = encoder.createInputSurface();
            encoder.start();

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "LibreCastCapture",
                    width, height, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface,
                    null, null
            );

            isEncoding = true;
            encoderThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    encoderLoop();
                }
            }, "ScreenCaptureEncoderThread");
            encoderThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to setup MediaCodec encoder or VirtualDisplay", e);
            stopCasting();
        }
    }

    private void encoderLoop() {
        ByteBuffer[] outputBuffers = null;
        try {
            outputBuffers = encoder.getOutputBuffers();
        } catch (Exception e) {
            Log.e(TAG, "Error getting output buffers", e);
            return;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (isEncoding && !Thread.currentThread().isInterrupted()) {
            try {
                int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000); // 10ms
                if (outputBufferIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        ByteBuffer outBuffer = outputBuffers[outputBufferIndex];
                        outBuffer.position(bufferInfo.offset);
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size);

                        byte[] data = new byte[bufferInfo.size];
                        outBuffer.get(data);

                        if (client != null && client.isOpen()) {
                            client.sendFrame(data);
                        }
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false);
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = encoder.getOutputBuffers();
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    Log.d(TAG, "Encoder format changed: " + newFormat);
                    if (newFormat.containsKey("csd-0")) {
                        ByteBuffer sps = newFormat.getByteBuffer("csd-0");
                        if (sps != null && client != null && client.isOpen()) {
                            byte[] spsBytes = new byte[sps.remaining()];
                            sps.get(spsBytes);
                            sps.rewind();
                            client.sendFrame(spsBytes);
                        }
                    }
                    if (newFormat.containsKey("csd-1")) {
                        ByteBuffer pps = newFormat.getByteBuffer("csd-1");
                        if (pps != null && client != null && client.isOpen()) {
                            byte[] ppsBytes = new byte[pps.remaining()];
                            pps.get(ppsBytes);
                            pps.rewind();
                            client.sendFrame(ppsBytes);
                        }
                    }
                }
            } catch (Exception e) {
                if (isEncoding) {
                    Log.e(TAG, "Error during encoding loop", e);
                }
                break;
            }
        }
    }

    private synchronized void stopCasting() {
        isEncoding = false;
        if (encoderThread != null) {
            encoderThread.interrupt();
            try {
                encoderThread.join(500);
            } catch (InterruptedException ignored) {
            }
            encoderThread = null;
        }

        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception ignored) {
            }
            virtualDisplay = null;
        }

        if (encoder != null) {
            try {
                encoder.stop();
            } catch (Exception ignored) {
            }
            try {
                encoder.release();
            } catch (Exception ignored) {
            }
            encoder = null;
        }

        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception ignored) {
            }
            mediaProjection = null;
        }

        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
            client = null;
        }

        isRunning = false;
        notifyStatus(false, "Cast stopped");
        stopForeground(true);
        stopSelf();
    }

    private void notifyStatus(boolean isCasting, String message) {
        Intent intent = new Intent(ACTION_STATUS_CHANGED);
        intent.putExtra(EXTRA_IS_CASTING, isCasting);
        intent.putExtra(EXTRA_STATUS_MESSAGE, message);
        sendBroadcast(intent);
    }

    // LibreCastClient.ClientListener

    @Override
    public void onConnected() {
        Log.i(TAG, "Connected to receiver WebSocket");
        notifyStatus(true, "Connected! Streaming screen...");
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "Disconnected from receiver WebSocket");
        stopCasting();
    }

    @Override
    public void onError(Exception ex) {
        Log.e(TAG, "LibreCastClient error", ex);
        notifyStatus(isRunning, "WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onDestroy() {
        stopCasting();
        super.onDestroy();
    }
}
