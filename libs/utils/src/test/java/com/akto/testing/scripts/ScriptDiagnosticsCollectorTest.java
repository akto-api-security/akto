package com.akto.testing.scripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.config.ScriptPhaseDiagnostics;
import com.akto.dto.testing.config.TestScript;

public class ScriptDiagnosticsCollectorTest {

    @AfterEach
    public void tearDown() {
        ScriptDiagnosticsCollector.end();
    }

    /** Runs js the way ApiExecutorUtil does when a collector is active. */
    private static void run(ScriptDiagnosticsCollector collector, TestScript.Type type, String js) {
        ScriptEngine engine = ScriptEngines.create(true);
        ScriptContext context = engine.getContext();
        collector.beforeRun(type, context, "before");
        try {
            engine.eval(js);
            collector.afterRun(type, "after");
        } catch (Exception e) {
            collector.failRun(type, e);
        }
    }

    @Test
    public void inactiveUnlessBegun() throws Exception {
        assertNull(ScriptDiagnosticsCollector.current());
        ScriptDiagnosticsCollector c = ScriptDiagnosticsCollector.begin();
        assertSame(c, ScriptDiagnosticsCollector.current());

        AtomicReference<Object> other = new AtomicReference<>("unset");
        Thread t = new Thread(() -> other.set(ScriptDiagnosticsCollector.current()));
        t.start();
        t.join();
        assertNull(other.get());

        ScriptDiagnosticsCollector.end();
        assertNull(ScriptDiagnosticsCollector.current());
    }

    @Test
    public void capturesPrintOutputAndSnapshots() {
        ScriptDiagnosticsCollector c = ScriptDiagnosticsCollector.begin();
        run(c, TestScript.Type.POST_REQUEST, "print('polling', 3);");

        ScriptPhaseDiagnostics post = c.getPost();
        assertEquals(1, post.getRuns());
        assertEquals("polling 3\n", post.getLogs());
        assertNull(post.getError());
        assertEquals("before", post.getBefore());
        assertEquals("after", post.getAfter());
        assertEquals(0, c.getPre().getRuns());
    }

    @Test
    public void recordsTheErrorTheExecutorSwallows() {
        ScriptDiagnosticsCollector c = ScriptDiagnosticsCollector.begin();
        run(c, TestScript.Type.PRE_REQUEST, "print('start'); undefinedFunction();");

        ScriptPhaseDiagnostics pre = c.getPre();
        assertEquals(1, pre.getRuns());
        assertTrue(pre.getError().contains("undefinedFunction"), pre.getError());
        assertEquals("start\n", pre.getLogs());
        assertNull(pre.getAfter());
    }

    @Test
    public void blockedClassShowsUpAsAScriptError() {
        ScriptDiagnosticsCollector c = ScriptDiagnosticsCollector.begin();
        run(c, TestScript.Type.PRE_REQUEST, "java.lang.System.getenv('HOME')");
        assertTrue(c.getPre().getError() != null);
    }

    @Test
    public void logsAreCappedAndFlagged() {
        ScriptDiagnosticsCollector c = ScriptDiagnosticsCollector.begin();
        run(c, TestScript.Type.POST_REQUEST,
            "var s = ''; for (var i = 0; i < 2000; i++) { s += 'xxxxxxxxxx'; } print(s);");
        assertEquals(ScriptDiagnosticsCollector.MAX_LOG_CHARS, c.getPost().getLogs().length());
        assertTrue(c.getPost().isLogsTruncated());
    }

    @Test
    public void secondRunCountsAndKeepsLogsTogether() {
        ScriptDiagnosticsCollector c = ScriptDiagnosticsCollector.begin();
        run(c, TestScript.Type.POST_REQUEST, "print('one');");
        run(c, TestScript.Type.POST_REQUEST, "print('two');");
        assertEquals(2, c.getPost().getRuns());
        assertEquals("one\ntwo\n", c.getPost().getLogs());
        assertFalse(c.getPost().isLogsTruncated());
    }

    @Test
    public void snapshotsShowTheRequestAndResponse() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", new ArrayList<>(Collections.singletonList("application/json")));
        OriginalHttpRequest req = new OriginalHttpRequest("https://x.test/chat", "a=1", "POST", "{\"q\":1}", headers, "HTTP/1.1");

        String text = ScriptDiagnosticsCollector.describeRequest(req);
        assertTrue(text.startsWith("POST https://x.test/chat?a=1\n"), text);
        assertTrue(text.contains("content-type: application/json"));
        assertTrue(text.endsWith("\n{\"q\":1}"));

        OriginalHttpResponse resp = new OriginalHttpResponse("{\"ok\":1}", new HashMap<>(), 202);
        assertEquals("HTTP 202\n\n{\"ok\":1}", ScriptDiagnosticsCollector.describeResponse(resp));
        assertNull(ScriptDiagnosticsCollector.describeRequest(null));
    }
}
