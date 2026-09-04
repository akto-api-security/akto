package com.akto.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.akto.util.Constants;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class HTTPClientHandlerTest {

    @BeforeAll
    public static void init() {
        HTTPClientHandler.initHttpClientHandler(false);
    }

    @Test
    public void defaultClientKeepsSixtySecondTimeout() {
        OkHttpClient client = HTTPClientHandler.instance.getHTTPClient(true, false, null);
        assertEquals(60_000, client.callTimeoutMillis());
        assertEquals(60_000, client.readTimeoutMillis());
    }

    @Test
    public void highTimeoutClientUsesConfiguredTimeoutAndIsPreBuilt() {
        OkHttpClient client = HTTPClientHandler.instance.getHTTPClient(true, true, null, true);
        int expectedMs = Constants.TESTING_TARGET_READ_TIMEOUT_SECONDS * 1000;
        assertEquals(expectedMs, client.callTimeoutMillis());
        assertEquals(expectedMs, client.readTimeoutMillis());
        // built once and reused, like the default clients
        assertSame(client, HTTPClientHandler.instance.getHTTPClient(true, true, null, true));
    }

    @Test
    public void highTimeoutFalseFallsBackToSharedDefaultClient() {
        OkHttpClient defaultClient = HTTPClientHandler.instance.getHTTPClient(true, false, null);
        assertSame(defaultClient, HTTPClientHandler.instance.getHTTPClient(true, false, null, false));
    }
}
