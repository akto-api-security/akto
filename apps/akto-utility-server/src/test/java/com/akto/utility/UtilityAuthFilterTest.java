package com.akto.utility;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The filter on a real JDK HttpServer, called over HTTP the way test-editor-services and data-ingestion call it. */
public class UtilityAuthFilterTest {

    private static final String TOKEN = "s3cr3t-token";

    private interface Call {
        void run(HttpServer server) throws IOException;
    }

    private static void withServer(String token, Call call) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/utility/sendLogs", exchange -> HttpUtil.sendJson(exchange, 200, HttpUtil.mapOf("ok", true)))
                .getFilters().add(new UtilityAuthFilter(token));
        server.start();
        try {
            call.run(server);
        } finally {
            server.stop(0);
        }
    }

    private static HttpURLConnection post(HttpServer server, String authorization) throws IOException {
        URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/utility/sendLogs");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        if (authorization != null) connection.setRequestProperty("Authorization", authorization);
        try (OutputStream out = connection.getOutputStream()) {
            out.write("{}".getBytes(StandardCharsets.UTF_8));
        }
        return connection;
    }

    @Test
    public void withoutAConfiguredTokenCallsGoThroughAsBefore() throws IOException {
        withServer(null, server -> {
            assertEquals(200, post(server, null).getResponseCode());
            assertEquals(200, post(server, "anything").getResponseCode());
        });
        assertFalse(new UtilityAuthFilter(null).isEnforced());
    }

    @Test
    public void withATokenOnlyTheExactTokenGetsIn() throws IOException {
        withServer(TOKEN, server -> {
            assertEquals(200, post(server, TOKEN).getResponseCode());

            assertEquals(401, post(server, null).getResponseCode());
            assertEquals(401, post(server, "wrong").getResponseCode());
            assertEquals(401, post(server, TOKEN + "x").getResponseCode());
            assertEquals(401, post(server, TOKEN.substring(1)).getResponseCode());
            assertEquals(401, post(server, "Bearer " + TOKEN).getResponseCode(), "the raw token, no scheme");
            assertEquals(401, post(server, "").getResponseCode());
        });
        assertTrue(new UtilityAuthFilter(TOKEN).isEnforced());
    }
}
