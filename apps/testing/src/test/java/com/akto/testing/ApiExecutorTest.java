package com.akto.testing;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class ApiExecutorTest {

    @Test
    public void testMakeUrlAbsolute() throws Exception {
        String originalUrl = "/dashboard";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("akto.io", "something"));
        headers.put("x-forwarded-proto", Collections.singletonList("https"));
        String url = ApiExecutor.makeUrlAbsolute(originalUrl, headers);
        assertEquals(url, "https://akto.io/dashboard");

        originalUrl = "dashboard";
        headers = new HashMap<>();
        headers.put("host", Arrays.asList("akto.io", "something"));
        headers.put("x-forwarded-proto", Collections.singletonList("http"));
        url = ApiExecutor.makeUrlAbsolute(originalUrl, headers);
        assertEquals(url, "http://akto.io/dashboard");

        originalUrl = "/dashboard";
        headers = new HashMap<>();
        headers.put("host", Arrays.asList("https://www.akto.io/", "something"));
        url = ApiExecutor.makeUrlAbsolute(originalUrl, headers);
        assertEquals(url, "https://www.akto.io/dashboard");

        originalUrl = "/dashboard";
        headers = new HashMap<>();
        headers.put("host", Collections.singletonList("akto.io/"));
        url = ApiExecutor.makeUrlAbsolute(originalUrl, headers);
        assertEquals(url, "https://akto.io/dashboard");

        originalUrl = "/dashboard";
        headers = new HashMap<>();
        headers.put("host", Collections.singletonList("127.0.0.1"));
        url = ApiExecutor.makeUrlAbsolute(originalUrl, headers);
        assertEquals(url, "http://127.0.0.1/dashboard");
    }
}
