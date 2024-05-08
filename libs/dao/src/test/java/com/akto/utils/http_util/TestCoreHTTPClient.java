package com.akto.utils.http_util;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.akto.util.Util;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.akto.util.http_util.CoreHTTPClient;

import okhttp3.OkHttpClient;

public class TestCoreHTTPClient {

    @Test
    public void testIpContains() {
        assertEquals(true, CoreHTTPClient.ipContains("10.10.20.0/30", "10.10.20.3"));
        assertEquals(false, CoreHTTPClient.ipContains("10.10.20.0/30", "10.10.20.5"));
        assertEquals(true, CoreHTTPClient.ipContains("1::/64", "1::1"));
        assertEquals(false, CoreHTTPClient.ipContains("1::/64", "2::1"));
        assertEquals(true, CoreHTTPClient.ipContains("1::3-4:5-6", "1::4:5"));
        assertEquals(true, CoreHTTPClient.ipContains("1-2::/64", "2::"));
        assertEquals(false, CoreHTTPClient.ipContains("bla", "foo"));
    }

    @Test
    public void testMatchHost() {
        List<String> hosts = Arrays.asList(".example1.com", "10.10.20.0/30", "127.0.0.1", "example2.com");
        assertEquals(true, CoreHTTPClient.matchHost(hosts, "10.10.20.3"));
        assertEquals(false, CoreHTTPClient.matchHost(hosts, "example1.com"));
        assertEquals(true, CoreHTTPClient.matchHost(hosts, "sub1.example1.com"));
        assertEquals(true, CoreHTTPClient.matchHost(hosts, "sub2.example1.com"));
        assertEquals(true, CoreHTTPClient.matchHost(hosts, "example2.com"));
        assertEquals(false, CoreHTTPClient.matchHost(hosts, "sub1.example2.com"));
        assertEquals(true, CoreHTTPClient.matchHost(hosts, "127.0.0.1"));
    }

    @Test
    public void testClientsIndependence() {

        /*
         * This tests that the clients created using the original clients have different
         * properties.
         */
        OkHttpClient client1 = CoreHTTPClient.client
                .newBuilder()
                .writeTimeout(5, TimeUnit.SECONDS).build();

        // default value for all timeouts is 10 seconds.
        assertEquals(5 * 1000, client1.writeTimeoutMillis());
        assertEquals(10 * 1000, client1.readTimeoutMillis());

        OkHttpClient client2 = CoreHTTPClient.client
                .newBuilder()
                .readTimeout(20, TimeUnit.SECONDS).build();

        assertEquals(5 * 1000, client1.writeTimeoutMillis());
        assertEquals(10 * 1000, client1.readTimeoutMillis());
        assertEquals(20 * 1000, client2.readTimeoutMillis());
        assertEquals(10 * 1000, client2.writeTimeoutMillis());

    }

    @Test
    public void testClientProxyConfig() {

        String host = "127.0.0.1";
        String uriStr1 = "http://example1.com";
        String host2 = "example2.com";
        String uriStr2 = "http://" + host2;
        InetSocketAddress p = new InetSocketAddress(host, 80);
        try {
            URI uri1 = new URI(uriStr1);
            URI uri2 = new URI(uriStr2);
            try (MockedStatic<Util> utilities = Mockito.mockStatic(Util.class)) {
                utilities.when(() -> Util.getEnvironmentVariable("PROXY_URI"))
                        .thenReturn(host);
                utilities.when(() -> Util.getEnvironmentVariable("NO_PROXY"))
                        .thenReturn(host2);
                assertEquals(host, Util.getEnvironmentVariable("PROXY_URI"));
                List<Proxy> proxies = CoreHTTPClient.client.proxySelector().select(uri1);
                Proxy proxy = proxies.get(0);
                assertEquals(p, proxy.address());
                proxies = CoreHTTPClient.client.proxySelector().select(uri2);
                proxy = proxies.get(0);
                assertNull(proxy.address());
            }
        } catch (Exception e) {
        }

    }

}