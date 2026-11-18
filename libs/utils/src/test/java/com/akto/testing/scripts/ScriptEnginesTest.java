package com.akto.testing.scripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

public class ScriptEnginesTest {

    private static Object evalSandboxed(String js) throws ScriptException {
        return ScriptEngines.create(true).eval(js);
    }

    @Test
    public void plainEngineKeepsFullJavaAccess() throws ScriptException {
        Object now = ScriptEngines.create(false).eval("java.lang.System.currentTimeMillis()");
        assertTrue(((Number) now).longValue() > 0);
    }

    @Test
    public void sandboxAllowsWhatPollingScriptsUse() throws ScriptException {
        Object out = evalSandboxed(
            "var UUID = Java.type('java.util.UUID');"
            + "var sb = new java.lang.StringBuilder(); sb.append('a').append('b');"
            + "java.lang.Thread.sleep(1);"
            + "var m = new java.util.HashMap(); m.put('k', 'v');"
            + "var started = Date.now();"
            + "var parsed = JSON.parse('{\"data\":{\"id\":\"x\"}}');"
            + "String(UUID.randomUUID()).length + ':' + sb.toString() + ':' + (m instanceof java.util.Map) + ':' + parsed.data.id + ':' + (started > 0)");
        assertEquals("36:ab:true:x:true", out);
    }

    @Test
    public void sandboxBlocksEnvironmentProcessAndFiles() {
        assertThrows(Exception.class, () -> evalSandboxed("java.lang.System.getenv('PATH')"));
        assertThrows(Exception.class, () -> evalSandboxed("Java.type('java.lang.System').getenv()"));
        assertThrows(Exception.class, () -> evalSandboxed("Java.type('java.lang.Runtime').getRuntime()"));
        assertThrows(Exception.class, () -> evalSandboxed("new (Java.type('java.io.File'))('/etc/passwd').exists()"));
        assertThrows(Exception.class, () -> evalSandboxed("Java.type('java.nio.file.Files')"));
    }

    @Test
    public void sandboxBlocksReflectionEscapes() {
        assertThrows(Exception.class, () -> evalSandboxed(
            "java.lang.Thread.currentThread().getContextClassLoader().loadClass('java.lang.Runtime')"
            + ".getMethod('getRuntime').invoke(null)"));
        assertThrows(Exception.class, () -> evalSandboxed(
            "new java.lang.StringBuilder().getClass().forName('java.lang.Runtime')"));
        assertThrows(Exception.class, () -> evalSandboxed("Java.type('java.lang.Class').forName('java.lang.Runtime')"));
        assertThrows(Exception.class, () -> evalSandboxed("Java.type('java.lang.reflect.Method')"));
    }

    /** The GET-poll pattern of the example post script, against a local server. */
    @Test
    public void sandboxedScriptCanPollOverHttp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jobs/1", exchange -> {
            byte[] body = "{\"status\":\"succeeded\",\"response\":{\"text\":\"done\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/jobs/1";
            ScriptEngine engine = ScriptEngines.create(true);
            engine.put("pollUrl", url);
            Object out = engine.eval(
                "var conn = new java.net.URL(pollUrl).openConnection();"
                + "conn.setRequestMethod('GET'); conn.setConnectTimeout(2000); conn.setReadTimeout(2000);"
                + "var code = conn.getResponseCode();"
                + "var reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), 'UTF-8'));"
                + "var sb = new java.lang.StringBuilder(); var line;"
                + "while ((line = reader.readLine()) != null) { sb.append(line); }"
                + "reader.close();"
                + "code + ':' + JSON.parse(String(sb.toString())).response.text");
            assertEquals("200:done", out);
        } finally {
            server.stop(0);
        }
    }

    /** The SSE unwrapping of the example post script is plain JS and must run unchanged in the sandbox. */
    @Test
    public void sandboxRunsPureJsParsing() throws ScriptException {
        ScriptEngine engine = ScriptEngines.create(true);
        engine.put("body", "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"ok\":true}}\n\n");
        Object out = engine.eval(
            "var lines = String(body).split(/\\r\\n|\\r|\\n/); var data = [];"
            + "for (var i = 0; i < lines.length; i++) { if (lines[i].indexOf('data:') === 0) { data.push(lines[i].substring(5).trim()); } }"
            + "JSON.parse(data[0]).result.ok");
        assertEquals(Boolean.TRUE, out);
    }
}
