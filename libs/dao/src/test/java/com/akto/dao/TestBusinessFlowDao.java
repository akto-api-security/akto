package com.akto.dao;

import com.akto.dto.BusinessFlow;
import com.akto.utils.MongoBasedTest;
import org.bson.Document;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestBusinessFlowDao extends MongoBasedTest {

    private static Document node(String id, String entityId, String operation, String action) {
        return new Document("id", id).append("entityId", entityId).append("operation", operation).append("action", action)
                .append("repeats", 3)
                .append("exampleApis", Collections.singletonList(new Document("apiCollectionId", -2016970724)
                        .append("method", "POST").append("url", "/backend.php/community/deleteWaitlistConfig/")));
    }

    @Test
    public void findFlowsReturnsLifecyclesAndJourneysOnly() {
        BusinessFlowDao.instance.getMCollection().drop();
        // Same shapes the business-flow agent writes, including a loop edge without shares and the internal rules doc
        Document lifecycle = new Document("_id", "chargepoint:lifecycle:waitlist").append("scopeKey", "chargepoint")
                .append("type", "lifecycle").append("entityIds", Collections.singletonList("waitlist"))
                .append("nodes", Arrays.asList(node("waitlist.delete", "waitlist", "delete", null), node("waitlist.create", "waitlist", "create", null)))
                .append("edges", Arrays.asList(
                        new Document("from", "waitlist.delete").append("to", "waitlist.create").append("kind", "step").append("count", 370)
                                .append("leadsTo", 0.54).append("comesFrom", 0.61).append("dependency", 0.9),
                        new Document("from", "waitlist.action:leave").append("to", "waitlist.read:inlinestatus").append("kind", "loop").append("count", 6129)))
                .append("runId", "r1");
        Document journey = new Document("_id", "chargepoint:journey:x").append("scopeKey", "chargepoint").append("type", "journey")
                .append("entityIds", Arrays.asList("login_session", "oauth_authorization"))
                .append("nodes", Arrays.asList(node("login_session.create", "login_session", "create", null),
                        node("oauth_authorization.action:code", "oauth_authorization", "action", "code")))
                .append("edges", Collections.singletonList(new Document("from", "login_session.create").append("to", "oauth_authorization.action:code")
                        .append("kind", "step").append("count", 358).append("leadsTo", 0.03).append("comesFrom", 0.88).append("dependency", 0.95)))
                .append("precedence", 0.88).append("probability", 0.03).append("count", 358);
        Document rules = new Document("_id", "chargepoint:rules").append("scopeKey", "chargepoint").append("type", "rules")
                .append("rules", Collections.emptyList());
        MCollection.getMCollection(BusinessFlowDao.instance.getDBName(), BusinessFlowDao.instance.getCollName(), Document.class)
                .insertMany(Arrays.asList(lifecycle, journey, rules));

        List<BusinessFlow> flows = BusinessFlowDao.instance.findFlows("chargepoint");

        assertEquals(2, flows.size());
        BusinessFlow life = flows.stream().filter(f -> f.getType().equals("lifecycle")).findFirst().get();
        assertEquals(2, life.getEdges().size());
        assertNull(life.getEdges().get(1).getComesFrom());
        assertNull(life.getPrecedence());
        assertEquals("/backend.php/community/deleteWaitlistConfig/", life.getNodes().get(0).getExampleApis().get(0).getUrl());
        BusinessFlow path = flows.stream().filter(f -> f.getType().equals("journey")).findFirst().get();
        assertEquals("code", path.getNodes().get(1).getAction());
        assertEquals(0.88, path.getEdges().get(0).getComesFrom(), 0.0001);
        assertEquals(Integer.valueOf(358), path.getCount());
    }
}
