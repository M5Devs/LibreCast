package org.librecast.receiver;

import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkUtilsTest {

    @Test
    public void testGetNetworkInterfaceIpAddress() {
        String ip = NetworkUtils.getNetworkInterfaceIpAddress();
        assertNotNull("IP address should not be null", ip);
        assertFalse("IP address should not be empty", ip.trim().isEmpty());
    }

    @Test
    public void testGetLocalIpAddressWithNullContext() {
        String ip = NetworkUtils.getLocalIpAddress(null);
        assertNotNull("IP address should fall back to non-null", ip);
    }
}
