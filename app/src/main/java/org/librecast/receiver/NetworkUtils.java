package org.librecast.receiver;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;

public class NetworkUtils {

    /**
     * Helper utility to get the device's local Wi-Fi or local network IPv4 address.
     * Falls back to NetworkInterface enumeration if WifiManager returns 0.0.0.0.
     */
    public static String getLocalIpAddress(Context context) {
        if (context != null) {
            try {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null && wifiManager.isWifiEnabled()) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo != null) {
                        int ipAddress = wifiInfo.getIpAddress();
                        if (ipAddress != 0) {
                            return String.format(Locale.US, "%d.%d.%d.%d",
                                    (ipAddress & 0xff),
                                    (ipAddress >> 8 & 0xff),
                                    (ipAddress >> 16 & 0xff),
                                    (ipAddress >> 24 & 0xff));
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return getNetworkInterfaceIpAddress();
    }

    /**
     * Resolves local IPv4 address by checking network interfaces (Ethernet / Wi-Fi fallback).
     */
    public static String getNetworkInterfaceIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface netInterface = interfaces.nextElement();
                    if (netInterface.isLoopback() || !netInterface.isUp()) {
                        continue;
                    }
                    Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "0.0.0.0";
    }
}
