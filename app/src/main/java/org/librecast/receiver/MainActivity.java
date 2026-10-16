package org.librecast.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView textServerStatus;
    private TextView textConnectionStatus;

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

        textServerStatus = findViewById(R.id.text_server_status);
        textConnectionStatus = findViewById(R.id.text_connection_status);

        startReceiverService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ReceiverService.ACTION_STATUS_UPDATE);
        registerReceiver(statusReceiver, filter);

        // Request current status
        Intent queryIntent = new Intent(this, ReceiverService.class);
        queryIntent.setAction(ReceiverService.ACTION_QUERY_STATUS);
        startService(queryIntent);
    }

    @Override
    protected void onStop() {
        super.onStop();
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
        } else {
            textServerStatus.setText(getString(R.string.server_stopped));
        }

        if (isClientConnected) {
            textConnectionStatus.setText(getString(R.string.client_connected_format, clientIp != null ? clientIp : ""));
        } else {
            textConnectionStatus.setText(getString(R.string.waiting_for_connection));
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
}
