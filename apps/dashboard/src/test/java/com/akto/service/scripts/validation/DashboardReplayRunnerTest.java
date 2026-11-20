package com.akto.service.scripts.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.mockito.MockedStatic;

import com.akto.MongoBasedTest;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.testing.config.TestScriptDraftsDao;
import com.akto.dto.testing.config.TestScriptDraft;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.test_editor.execution.Executor;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import com.sun.net.httpserver.HttpServer;

/** Proves the replay template: the engine accepts an empty modification list and sends the captured request as is. */
public class DashboardReplayRunnerTest extends MongoBasedTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedMethod = new AtomicReference<>();

    @Before
    public void startServer() throws Exception {
        Context.accountId.set(ACCOUNT_ID);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ask/queries", exchange -> {
            hits.incrementAndGet();
            receivedMethod.set(exchange.getRequestMethod());
            receivedBody.set(read(exchange.getRequestBody()));
            byte[] body = "{\"job_id\":\"j1\",\"status\":\"queued\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(202, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        TestRolesDao.instance.getMCollection().drop();
        AuthMechanism attacker = new AuthMechanism(java.util.Collections.<AuthParam>singletonList(
            new HardcodedAuthParam(AuthParam.Location.HEADER, "authorization", "attacker", true)), null, "HARDCODED", null);
        java.util.List<AuthWithCond> conds = new java.util.ArrayList<>();
        conds.add(new AuthWithCond(attacker, new java.util.HashMap<>(), null));
        TestRolesDao.instance.insertOne(new TestRoles(new org.bson.types.ObjectId(), "ATTACKER_TOKEN_ALL", null, conds, "a", 0, 0, null, "a"));
        Executor.clearRoleCache();
    }

    @After
    public void stopServer() {
        server.stop(0);
    }

    private static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private String sample(String url) {
        BasicDBObject s = new BasicDBObject();
        s.put("path", url);
        s.put("method", "POST");
        s.put("type", "HTTP/1.1");
        s.put("requestHeaders", "{\"Content-Type\":\"application/json\"}");
        s.put("requestPayload", "{\"question\":\"hi\"}");
        s.put("responseHeaders", "{\"Content-Type\":\"application/json\"}");
        s.put("responsePayload", "{\"job_id\":\"old\"}");
        s.put("statusCode", "202");
        s.put("status", "Accepted");
        s.put("time", "1700000000");
        s.put("akto_account_id", String.valueOf(ACCOUNT_ID));
        s.put("ip", "null");
        s.put("source", "HAR");
        return s.toJson();
    }

    @Test
    public void templateParses() throws Exception {
        TestConfig config = ScriptValidationReplayTemplate.parse();
        assertNotNull(config);
        assertEquals(ScriptValidationReplayTemplate.ID, config.getId());
    }

    @Test
    public void replaysTheCapturedRequestUnchanged() throws Exception {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/ask/queries";
        ApiInfo.ApiInfoKey key = new ApiInfo.ApiInfoKey(9, url, URLMethods.Method.POST);
        TestingRunConfig config = new TestingRunConfig();
        config.setPreRequestScriptId(TestScript.NO_SCRIPT);
        config.setPostRequestScriptId(TestScript.NO_SCRIPT);

        ReplayOutcome outcome = new DashboardReplayRunner().replay(key, sample(url), config);

        assertTrue("engine errors: " + outcome.getWarnings(), outcome.getWarnings().isEmpty());
        assertEquals(1, hits.get());
        assertEquals("POST", receivedMethod.get());
        assertEquals("{\"question\":\"hi\"}", receivedBody.get());
        assertEquals(Integer.valueOf(202), outcome.getStatusCode());
        assertTrue(outcome.getReplayMessage(), outcome.getReplayMessage().contains("j1"));
    }

    /**
     * End to end through the test engine: a sandboxed draft post-request script runs on the thread the
     * collector is active on (the feature-gate stub is thread-local, so it would not apply otherwise),
     * its print output and rewrite are captured, and the final body is what the script set.
     */
    @Test
    public void draftScriptsRunThroughTheEngineWithDiagnostics() throws Exception {
        TestScriptDraftsDao.instance.getMCollection().drop();
        java.util.Date expires = new java.util.Date(System.currentTimeMillis() + 60_000);
        String draftId = TestScriptDraft.draftId("v-e2e", TestScript.Type.POST_REQUEST);
        TestScriptDraftsDao.instance.insertIfAbsent(new TestScriptDraft(draftId,
            "print('job', JSON.parse(String(body)).job_id); body = 'final answer';",
            TestScript.Type.POST_REQUEST, "a@x.com", 0, expires));
        String preDraft = TestScriptDraft.draftId("v-e2e", TestScript.Type.PRE_REQUEST);
        TestScriptDraftsDao.instance.insertIfAbsent(new TestScriptDraft(preDraft,
            "java.lang.System.getenv('HOME');", TestScript.Type.PRE_REQUEST, "a@x.com", 0, expires));

        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/ask/queries";
        ApiInfo.ApiInfoKey key = new ApiInfo.ApiInfoKey(9, url, URLMethods.Method.POST);
        TestingRunConfig config = new TestingRunConfig();
        config.setPreRequestScriptId(preDraft);
        config.setPostRequestScriptId(draftId);

        ReplayOutcome outcome;
        try (MockedStatic<UsageMetricUtils> usage = mockStatic(UsageMetricUtils.class)) {
            usage.when(() -> UsageMetricUtils.getFeatureAccessSaas(anyInt(), eq("TEST_PRE_SCRIPT"))).thenReturn(FeatureAccess.fullAccess);
            outcome = new DashboardReplayRunner().replay(key, sample(url), config);
        }

        // pre: the sandbox blocked System, the error is recorded, and the request still went out unchanged
        assertEquals(1, outcome.getDiagnostics().getPre().getRuns());
        assertNotNull(outcome.getDiagnostics().getPre().getError());
        assertNull(outcome.getDiagnostics().getPre().getAfter());
        assertEquals("{\"question\":\"hi\"}", receivedBody.get());
        // post: ran once, its print output was captured, and the body it set is the final response
        assertTrue("post runs: " + outcome.getDiagnostics().getPost().getRuns(), outcome.getDiagnostics().getPost().getRuns() >= 1);
        assertNull(outcome.getDiagnostics().getPost().getError());
        assertEquals("job j1\n", outcome.getDiagnostics().getPost().getLogs());
        assertTrue(outcome.getDiagnostics().getPost().getBefore().contains("j1"));
        assertTrue(outcome.getDiagnostics().getPost().getAfter().endsWith("final answer"));
        assertTrue(outcome.getReplayMessage(), outcome.getReplayMessage().contains("final answer"));
    }
}
