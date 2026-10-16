package com.m5dev.librecast;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Map;

public class QRCodeUtils {

    private static final String TAG = "QRCodeUtils";

    /**
     * Generates an Android Bitmap containing a QR code for the specified text.
     *
     * @param text   The content string to encode in the QR code (e.g. "ws://192.168.1.100:8080")
     * @param width  Desired bitmap width in pixels
     * @param height Desired bitmap height in pixels
     * @return Bitmap containing the QR code, or null if generation fails or input is empty.
     */
    public static Bitmap generateQRCode(String text, int width, int height) {
        if (text == null || text.trim().isEmpty() || width <= 0 || height <= 0) {
            return null;
        }

        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            int matrixWidth = bitMatrix.getWidth();
            int matrixHeight = bitMatrix.getHeight();

            int[] pixels = new int[matrixWidth * matrixHeight];
            for (int y = 0; y < matrixHeight; y++) {
                int offset = y * matrixWidth;
                for (int x = 0; x < matrixWidth; x++) {
                    pixels[offset + x] = bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight);
            return bitmap;
        } catch (WriterException e) {
            Log.e(TAG, "Failed to encode QR code", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error generating QR code", e);
            return null;
        }
    }
}
