package com.m5dev.librecast;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class SenderActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SCREEN_CAPTURE = 2001;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 2002;

    private EditText editTargetAddress;
    private Button btnScanQr;
    private Button btnToggleCast;
    private TextView textCastStatus;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ScreenCaptureService.ACTION_STATUS_CHANGED.equals(intent.getAction())) {
                boolean isCasting = intent.getBooleanExtra(ScreenCaptureService.EXTRA_IS_CASTING, false);
                String statusMsg = intent.getStringExtra(ScreenCaptureService.EXTRA_STATUS_MESSAGE);
                updateUIState(isCasting, statusMsg);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sender);

        editTargetAddress = findViewById(R.id.edit_target_address);
        btnScanQr = findViewById(R.id.btn_scan_qr);
        btnToggleCast = findViewById(R.id.btn_toggle_cast);
        textCastStatus = findViewById(R.id.text_cast_status);

        // Check compatibility for screen capture (< Android 5.0)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            showUnsupportedVersionDialog();
        }

        btnScanQr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkCameraPermissionAndScan();
            }
        });

        btnToggleCast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ScreenCaptureService.isCasting()) {
                    stopCastService();
                } else {
                    startCastService();
                }
            }
        });

        updateUIState(ScreenCaptureService.isCasting(), null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ScreenCaptureService.ACTION_STATUS_CHANGED);
        registerReceiver(statusReceiver, filter);
        updateUIState(ScreenCaptureService.isCasting(), null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(statusReceiver);
        } catch (Exception ignored) {
        }
    }

    private void showUnsupportedVersionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.unsupported_version_title)
                .setMessage(R.string.unsupported_version_message)
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void checkCameraPermissionAndScan() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Toast.makeText(this, "Camera not available on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
                return;
            }
        }
        startQRScanner();
    }

    private void startQRScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan TV QR Code");
        integrator.setOrientationLocked(false);
        integrator.setBeepEnabled(true);
        integrator.initiateScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startQRScanner();
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCastService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            showUnsupportedVersionDialog();
            return;
        }

        String targetAddress = editTargetAddress.getText().toString().trim();
        if (targetAddress.isEmpty() || (!targetAddress.startsWith("ws://") && !targetAddress.startsWith("wss://"))) {
            Toast.makeText(this, R.string.invalid_address, Toast.LENGTH_LONG).show();
            return;
        }

        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
        }
    }

    private void stopCastService() {
        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        serviceIntent.setAction(ScreenCaptureService.ACTION_STOP);
        startService(serviceIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                String targetAddress = editTargetAddress.getText().toString().trim();
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.setAction(ScreenCaptureService.ACTION_START);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_TARGET_URI, targetAddress);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
            }
        } else {
            IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (result != null) {
                if (result.getContents() != null) {
                    editTargetAddress.setText(result.getContents());
                }
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    private void updateUIState(boolean isCasting, String statusMsg) {
        if (isCasting) {
            btnToggleCast.setText(R.string.btn_stop_cast);
            btnToggleCast.setTextColor(getResources().getColor(R.color.accent_red));
            editTargetAddress.setEnabled(false);
            btnScanQr.setEnabled(false);
            textCastStatus.setText(statusMsg != null ? statusMsg : getString(R.string.casting_active));
        } else {
            btnToggleCast.setText(R.string.btn_start_cast);
            btnToggleCast.setTextColor(getResources().getColor(R.color.accent_green));
            editTargetAddress.setEnabled(true);
            btnScanQr.setEnabled(true);
            textCastStatus.setText(statusMsg != null ? statusMsg : getString(R.string.casting_stopped));
        }
    }
}
