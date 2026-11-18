package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestingRunPlaygroundDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dao.upload.FileUploadsDao;
import com.akto.dto.upload.FileUpload;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.util.enums.LoginFlowEnums;
import org.bson.types.ObjectId;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods.Method;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TestAgentImportActionWebSocket extends MongoBasedTest {

    private static final int COLLECTION_ID = "chat-agent".hashCode();

    @Before
    public void clean() {
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        TestingRunPlaygroundDao.instance.getMCollection().drop();
        TestRolesDao.instance.getMCollection().drop();
        FileUploadsDao.instance.getMCollection().drop();
    }

    // the import runs in the background: its status, as the UI polls it, once it has finished
    private static AgentImportAction awaitImport(AgentImportAction started) throws Exception {
        assertNotNull(started.getUploadId());
        AgentImportAction status = new AgentImportAction();
        status.setUploadId(started.getUploadId());
        for (int i = 0; i < 100; i++) {
            assertEquals("SUCCESS", status.fetchAgentImportStatus());
            if (status.getUploadStatus() != FileUpload.UploadStatus.IN_PROGRESS) {
                return status;
            }
            Thread.sleep(100);
        }
        fail("import did not finish");
        return status;
    }

    // plays mini-testing: picks up the scheduled request and answers it with the given response
    private static Thread fakeMiniTesting(OriginalHttpResponse response, AtomicReference<TestingRunPlayground> seen) {
        int accountId = Context.accountId.get();
        Thread t = new Thread(() -> {
            Context.accountId.set(accountId);
            for (int i = 0; i < 50; i++) {
                TestingRunPlayground scheduled = TestingRunPlaygroundDao.instance.findOne(
                        Filters.eq(TestingRunPlayground.STATE, TestingRun.State.SCHEDULED));
                if (scheduled != null) {
                    seen.set(scheduled);
                    TestingRunPlaygroundDao.instance.updateOne(Filters.eq("_id", scheduled.getId()), Updates.combine(
                            Updates.set(TestingRunPlayground.STATE, TestingRun.State.COMPLETED),
                            Updates.set("originalHttpResponse", response)));
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        t.start();
        return t;
    }

    private static AgentImportAction connectionImport(String miniTestingName) {
        AgentImportAction action = new AgentImportAction();
        action.setUrl("wss://agent.example.com/ws");
        action.setCollectionName("chat-agent");
        action.setMiniTestingName(miniTestingName);
        return action;
    }

    @Test
    public void testWebSocketImportNeedsMiniTesting() {
        AgentImportAction action = connectionImport(null);
        assertEquals("ERROR", action.importFromUrl());
        assertTrue(action.getActionErrors().iterator().next().contains("mini-testing"));
    }

    @Test
    public void testRefusedHandshakeIsNotImported() throws Exception {
        AtomicReference<TestingRunPlayground> seen = new AtomicReference<>();
        Thread miniTesting = fakeMiniTesting(new OriginalHttpResponse(
                "{\"error\":\"Connection closed (HTTP 401)\"}", new HashMap<>(), 401), seen);

        AgentImportAction action = connectionImport("mt-1");
        assertEquals("SUCCESS", action.importFromUrl());
        miniTesting.join();
        AgentImportAction status = awaitImport(action);

        assertEquals(FileUpload.UploadStatus.FAILED, status.getUploadStatus());
        assertTrue(status.getErrorMessage(), status.getErrorMessage().startsWith("WebSocket handshake failed with HTTP 401"));
        // mini-testing was asked for the handshake only: the handshake URL marked WEBSOCKET, and no message
        assertEquals("https://agent.example.com/ws", seen.get().getOriginalHttpRequest().getUrl());
        assertEquals("WEBSOCKET", seen.get().getOriginalHttpRequest().getType());
        assertTrue(seen.get().getOriginalHttpRequest().getBody() == null || seen.get().getOriginalHttpRequest().getBody().isEmpty());
        assertEquals("mt-1", seen.get().getMiniTestingName());
        // nothing is created for a connection that was refused
        assertNull(ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, COLLECTION_ID)));
    }

    private static TestRoles insertCookieRole() {
        AuthMechanism auth = new AuthMechanism(
                Collections.singletonList(new HardcodedAuthParam(AuthParam.Location.HEADER, "Cookie", "session=secret", true)),
                null, LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString(), null);
        TestRoles role = new TestRoles(new ObjectId(), "pdh-user", null,
                Collections.singletonList(new AuthWithCond(auth, new HashMap<>(), null)), "test", 0, 0, null, "test");
        TestRolesDao.instance.insertOne(role);
        return role;
    }

    @Test
    public void testMiniTestingAppliesTheTestRole() throws Exception {
        TestRoles role = insertCookieRole();
        AtomicReference<TestingRunPlayground> seen = new AtomicReference<>();
        Thread miniTesting = fakeMiniTesting(new OriginalHttpResponse("", new HashMap<>(), 401), seen);

        AgentImportAction action = connectionImport("mt-1");
        action.setTestRoleId(role.getHexId());
        assertEquals("SUCCESS", action.importFromUrl());
        miniTesting.join();
        awaitImport(action);

        Map<String, List<String>> sent = seen.get().getOriginalHttpRequest().getHeaders();
        // mini-testing gets the role to apply, and the role's auth header without its value: not resolved here
        assertEquals(Collections.singletonList(role.getHexId()), sent.get(Constants.AKTO_TEST_ROLE_ID_HEADER));
        assertEquals(Collections.singletonList(""), sent.get("cookie"));
    }

    @Test
    public void testMiniTestingRoleErrorIsShown() throws Exception {
        TestRoles role = insertCookieRole();
        AtomicReference<TestingRunPlayground> seen = new AtomicReference<>();
        Thread miniTesting = fakeMiniTesting(new OriginalHttpResponse(
                "{\"error\":\"Could not apply test role pdh-user: login failed\"}", new HashMap<>(), 0), seen);

        AgentImportAction action = connectionImport("mt-1");
        action.setTestRoleId(role.getHexId());
        assertEquals("SUCCESS", action.importFromUrl());
        miniTesting.join();
        AgentImportAction status = awaitImport(action);

        assertEquals(FileUpload.UploadStatus.FAILED, status.getUploadStatus());
        assertEquals("Mini-testing mt-1: Could not apply test role pdh-user: login failed", status.getErrorMessage());
    }

    @Test
    public void testFailedEventIsNotSaved() throws Exception {
        AtomicReference<TestingRunPlayground> seen = new AtomicReference<>();
        Thread miniTesting = fakeMiniTesting(new OriginalHttpResponse("{\"error\":\"timeout\"}", new HashMap<>(), 504), seen);

        AgentImportAction action = connectionImport("mt-1");
        action.setRequestBody("{\"event\":\"user_query\",\"data\":{\"user_query\":\"hi\"}}");
        assertEquals("SUCCESS", action.importFromUrl());
        miniTesting.join();
        AgentImportAction status = awaitImport(action);

        // the event went to mini-testing over the connection URL, and its timeout fails the import
        assertEquals("{\"event\":\"user_query\",\"data\":{\"user_query\":\"hi\"}}", seen.get().getOriginalHttpRequest().getBody());
        assertEquals(FileUpload.UploadStatus.FAILED, status.getUploadStatus());
        assertEquals("WebSocket event failed with HTTP 504: timeout", status.getErrorMessage());
        assertNull(ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, COLLECTION_ID)));
    }

    @Test
    public void testAuthAppliedByMiniTestingGoesInTheSample() {
        Map<String, List<String>> responseHeaders = new HashMap<>();
        responseHeaders.put("upgrade", Collections.singletonList("websocket"));
        responseHeaders.put(Constants.AKTO_APPLIED_AUTH_HEADER, Collections.singletonList("{\"cookie\": \"session=secret\"}"));
        OriginalHttpResponse response = new OriginalHttpResponse("", Collections.unmodifiableMap(responseHeaders), 101);
        Map<String, List<String>> requestHeaders = new HashMap<>();
        requestHeaders.put("cookie", Collections.singletonList(""));

        AgentImportAction.takeAppliedAuth(response, requestHeaders);
        // the request (so the saved sample) gets the value; the internal header is not kept on the response
        assertEquals(Collections.singletonList("session=secret"), requestHeaders.get("cookie"));
        assertFalse(response.getHeaders().containsKey(Constants.AKTO_APPLIED_AUTH_HEADER));
        assertEquals(Collections.singletonList("websocket"), response.getHeaders().get("upgrade"));

        // an older mini-testing sends nothing back: the request keeps its placeholder
        Map<String, List<String>> placeholder = new HashMap<>();
        placeholder.put("cookie", Collections.singletonList(""));
        AgentImportAction.takeAppliedAuth(new OriginalHttpResponse("", new HashMap<>(), 101), placeholder);
        assertEquals(Collections.singletonList(""), placeholder.get("cookie"));
        AgentImportAction.takeAppliedAuth(null, placeholder);
    }

    @Test
    public void testUnknownTestRoleIsNotSent() throws Exception {
        AgentImportAction action = connectionImport("mt-1");
        action.setTestRoleId(new ObjectId().toHexString());
        assertEquals("SUCCESS", action.importFromUrl());
        AgentImportAction status = awaitImport(action);

        assertEquals(FileUpload.UploadStatus.FAILED, status.getUploadStatus());
        assertTrue(status.getErrorMessage(), status.getErrorMessage().startsWith("Test role not found"));
        assertNull(TestingRunPlaygroundDao.instance.findOne(Filters.empty()));
    }

    private static void insertConnection(String url, String requestHeaders) {
        ApiInfo apiInfo = new ApiInfo(COLLECTION_ID, url, Method.GET);
        apiInfo.setIsConnectionString(true);
        ApiInfoDao.instance.insertOne(apiInfo);

        String sample = "{\"method\":\"GET\",\"path\":\"" + url + "\",\"type\":\"WEBSOCKET\",\"statusCode\":\"101\"," +
                "\"requestPayload\":\"{}\",\"responsePayload\":\"{}\",\"requestHeaders\":\"" + requestHeaders.replace("\"", "\\\"") + "\"}";
        SampleDataDao.instance.insertOne(new SampleData(new Key(COLLECTION_ID, url, Method.GET, -1, 0, 0), Collections.singletonList(sample)));
    }

    @Test
    public void testNoConnection() throws Exception {
        // an endpoint that isn't the connection string doesn't count
        ApiInfoDao.instance.insertOne(new ApiInfo(COLLECTION_ID, "/join_room", Method.WS));
        assertNull(new AgentImportAction().loadConnectionUrl(COLLECTION_ID));
    }

    @Test
    public void testConnectionUrl() throws Exception {
        insertConnection("https://agent.example.com/ws", "{\"origin\":\"https://agent.example.com\"}");
        assertEquals("wss://agent.example.com/ws", new AgentImportAction().loadConnectionUrl(COLLECTION_ID));
    }

    @Test
    public void testMessageType() {
        assertEquals("user_query", AgentImportAction.messageType("{\"event\":\"user_query\",\"data\":{}}"));
        assertEquals("join_room", AgentImportAction.messageType("{\"type\":\"join_room\",\"event\":\"x\"}"));
        assertEquals("data_frame", AgentImportAction.messageType("hello"));
    }
}
