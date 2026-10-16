package com.m5dev.librecast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QRCodeUtilsTest {

    @Test
    public void testQRCodeBitMatrixEncoding() throws Exception {
        String testUrl = "ws://192.168.1.100:8080";
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix bitMatrix = writer.encode(testUrl, BarcodeFormat.QR_CODE, 250, 250, hints);
        assertNotNull("BitMatrix should not be null", bitMatrix);
        assertTrue("Width should be positive", bitMatrix.getWidth() > 0);
        assertTrue("Height should be positive", bitMatrix.getHeight() > 0);
    }

    @Test
    public void testQRCodeUtilsNullOrEmptyHandling() {
        assertNull("Null text should return null", QRCodeUtils.generateQRCode(null, 250, 250));
        assertNull("Empty text should return null", QRCodeUtils.generateQRCode("", 250, 250));
        assertNull("Whitespace text should return null", QRCodeUtils.generateQRCode("   ", 250, 250));
        assertNull("Invalid width should return null", QRCodeUtils.generateQRCode("ws://192.168.1.1:8080", 0, 250));
        assertNull("Invalid height should return null", QRCodeUtils.generateQRCode("ws://192.168.1.1:8080", 250, -1));
    }
}
