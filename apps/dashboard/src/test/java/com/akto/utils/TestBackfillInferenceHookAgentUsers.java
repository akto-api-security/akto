package com.akto.utils;

import com.akto.MongoBasedTest;
import com.akto.dao.AgentUsersDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.AgenticUsers;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.utils.scripts.BackwardCompatibilityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestBackfillInferenceHookAgentUsers extends MongoBasedTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        ApiCollectionsDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();
        AgentUsersDao.instance.getMCollection().drop();
    }

    @Test
    public void upsertsOneIdentityPerHookUserEmail() throws Exception {
        insertCollection(1, "inference-hook-jane-doe.ai-agent.claude-cli", "inference-hooks");
        insertSample(1, "/claude/guardrail", "jane.doe@corp.com");
        insertSample(1, "/claude/guardrail/tool-call/bash", "jane.doe@corp.com");
        insertCollection(2, "inference-hook-john-roe.claude-app.gmail", "inference-hooks");
        insertSample(2, "/mcp", "john.roe@corp.com");

        assertEquals(2, BackwardCompatibilityUtils.backfillInferenceHookAgentUsers());

        AgenticUsers jane = AgentUsersDao.instance.findOne(Filters.eq(AgenticUsers.USER_ID, "jane.doe@corp.com"));
        assertEquals("jane.doe", jane.getUserName());
        assertEquals("jane.doe@corp.com", jane.getUserEmail());
        assertEquals(2, AgentUsersDao.instance.count(Filters.empty()));
    }

    @Test
    public void ignoresNonHookCollectionsAndSamplesWithoutEmail() throws Exception {
        insertCollection(1, "laptop-1.ai-agent.claude-desktop", "ai-agent");
        insertSample(1, "/v1/messages", "someone@corp.com");
        insertCollection(2, "inference-hook-unknown.ai-agent.config-test", "inference-hooks");
        insertSample(2, "/claude/guardrail", null);

        assertEquals(0, BackwardCompatibilityUtils.backfillInferenceHookAgentUsers());
        assertEquals(0, AgentUsersDao.instance.count(Filters.empty()));
    }

    @Test
    public void rerunDoesNotDuplicateExistingRow() throws Exception {
        insertCollection(1, "inference-hook-jane-doe.ai-agent.claude-cli", "inference-hooks");
        insertSample(1, "/claude/guardrail", "jane.doe@corp.com");
        AgenticUsers existing = new AgenticUsers();
        existing.setUserId("jane.doe@corp.com");
        existing.setUserEmail("jane.doe@corp.com");
        existing.setUserName("jane.doe");
        AgentUsersDao.instance.insertOne(existing);

        BackwardCompatibilityUtils.backfillInferenceHookAgentUsers();
        BackwardCompatibilityUtils.backfillInferenceHookAgentUsers();

        assertEquals(1, AgentUsersDao.instance.count(Filters.eq(AgenticUsers.USER_ID, "jane.doe@corp.com")));
        assertNull(AgentUsersDao.instance.findOne(Filters.eq(AgenticUsers.USER_NAME, "inference-hook-jane-doe")));
    }

    private void insertCollection(int id, String host, String tagKey) {
        ApiCollection collection = new ApiCollection();
        collection.setId(id);
        collection.setHostName(host);
        collection.setTagsList(Collections.singletonList(new CollectionTags(Context.now(), tagKey, "claude", CollectionTags.TagSource.KUBERNETES)));
        ApiCollectionsDao.instance.insertOne(collection);
    }

    private void insertSample(int collectionId, String path, String email) throws Exception {
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("host", "inference-hook.example");
        requestHeaders.put("content-type", "application/json");
        if (email != null) requestHeaders.put("x-akto-installer-user_email", email);

        Map<String, Object> sample = new HashMap<>();
        sample.put("path", path);
        sample.put("method", "POST");
        sample.put("type", "HTTP/1.1");
        sample.put("requestHeaders", MAPPER.writeValueAsString(requestHeaders));
        sample.put("responseHeaders", "{\"content-type\":\"application/json\"}");
        sample.put("requestPayload", "{\"body\":\"hello\"}");
        sample.put("responsePayload", "{}");
        sample.put("ip", "0.0.0.0");
        sample.put("destIp", "");
        sample.put("time", String.valueOf(Context.now()));
        sample.put("statusCode", "200");
        sample.put("status", "OK");
        sample.put("akto_account_id", String.valueOf(ACCOUNT_ID));
        sample.put("akto_vxlan_id", String.valueOf(collectionId));
        sample.put("is_pending", "false");
        sample.put("source", "MIRRORING");
        sample.put("direction", "");

        Key key = new Key(collectionId, path, URLMethods.Method.POST, 200, 0, 0);
        List<String> samples = Collections.singletonList(MAPPER.writeValueAsString(sample));
        SampleDataDao.instance.insertOne(new SampleData(key, samples));
    }
}
