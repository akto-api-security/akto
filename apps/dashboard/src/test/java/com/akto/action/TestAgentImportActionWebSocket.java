package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestingRunPlaygroundDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.testing.TestingRun;
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
        assertEquals("ERROR", action.importFromUrl());
        miniTesting.join();

        String error = action.getActionErrors().iterator().next();
        assertTrue(error, error.contains("HTTP 401"));
        // mini-testing was asked for the handshake only: the handshake URL marked WEBSOCKET, and no message
        assertEquals("https://agent.example.com/ws", seen.get().getOriginalHttpRequest().getUrl());
        assertEquals("WEBSOCKET", seen.get().getOriginalHttpRequest().getType());
        assertTrue(seen.get().getOriginalHttpRequest().getBody() == null || seen.get().getOriginalHttpRequest().getBody().isEmpty());
        assertEquals("mt-1", seen.get().getMiniTestingName());
        // nothing is created for a connection that was refused
        assertNull(ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, COLLECTION_ID)));
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
