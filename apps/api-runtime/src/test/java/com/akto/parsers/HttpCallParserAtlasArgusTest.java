package com.akto.parsers;

import com.akto.DaoInit;
import com.akto.dao.SetupDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.Setup;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.CollectionTags.TagSource;
import com.akto.util.Constants;
import com.mongodb.ConnectionString;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.ImmutableMongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "hostname" agentic-routing change in HttpCallParser.createApiCollectionId:
 * hasAtlasOrArgusTag() and the case table it drives (fresh host / clean mixed collection /
 * already-tagged collection / ENDPOINT-sourced bypass). Mirrors
 * apps/mini-runtime's HttpCallParserAtlasArgusTest in akto_mini, adapted to this file's
 * simpler shape (mcp-server only, BasicDBObject-based tags, no rag/gen-ai).
 *
 * Self-contained JUnit 5 embedded-Mongo bootstrap (mirrors com.akto.MongoBasedTest, which is
 * JUnit 4 and can't be used here - the JUnit Vintage engine in this repo fails to parse this
 * project's junit:junit version string, so JUnit 4-style tests never actually run).
 */
class HttpCallParserAtlasArgusTest {

    private static final int ACCOUNT_ID = 1000000;
    private static MongodExecutable mongodExe;
    private static MongodProcess mongod;

    @BeforeAll
    static void startMongoAndInit() throws Exception {
        MongodStarter starter = MongodStarter.getDefaultInstance();
        ImmutableMongodConfig mongodConfig = ImmutableMongodConfig.builder()
                .version(Version.Main.PRODUCTION)
                .net(new Net("localhost", 27021, false))
                .build();
        mongodExe = starter.prepare(mongodConfig);
        mongod = mongodExe.start();
        DaoInit.init(new ConnectionString("mongodb://localhost:27021"));
        Context.accountId.set(ACCOUNT_ID);
    }

    @AfterAll
    static void stopMongo() {
        if (mongod != null) {
            mongod.stop();
            mongodExe.stop();
        }
    }

    @BeforeEach
    void setup() {
        Context.accountId.set(ACCOUNT_ID);
        OrganizationsDao.instance.getMCollection().drop();
        SetupDao.instance.getMCollection().drop();

        // ON_PREM makes DashboardMode.isMetered() true so the org's featureWiseAllowed is actually consulted.
        SetupDao.instance.insertOne(new Setup("ON_PREM"));
    }

    private void grantSecurityTypeAgentic() {
        Organization org = new Organization("test-org", "Test Org", "a@b.com",
                new HashSet<>(Collections.singletonList(ACCOUNT_ID)), true);
        HashMap<String, FeatureAccess> features = new HashMap<>();
        features.put("SECURITY_TYPE_AGENTIC", new FeatureAccess(true));
        org.setFeatureWiseAllowed(features);
        OrganizationsDao.instance.insertOne(org);
    }

    private HttpCallParser newParser() throws Exception {
        return new HttpCallParser("test-user", 10, 10, 120, false);
    }

    // ---------- hasAtlasOrArgusTag ----------

    private ApiCollection collectionWithTags(CollectionTags... tags) {
        ApiCollection collection = new ApiCollection();
        if (tags != null && tags.length > 0) {
            List<CollectionTags> tagsList = new ArrayList<>();
            Collections.addAll(tagsList, tags);
            collection.setTagsList(tagsList);
        }
        return collection;
    }

    private boolean invokeHasAtlasOrArgusTag(HttpCallParser parser, ApiCollection collection) throws Exception {
        Method m = HttpCallParser.class.getDeclaredMethod("hasAtlasOrArgusTag", ApiCollection.class);
        m.setAccessible(true);
        return (boolean) m.invoke(parser, collection);
    }

    @Test
    public void hasAtlasOrArgusTag_noTags_false() throws Exception {
        HttpCallParser parser = newParser();
        assertFalse(invokeHasAtlasOrArgusTag(parser, collectionWithTags()));
        assertFalse(invokeHasAtlasOrArgusTag(parser, new ApiCollection()));
    }

    @Test
    public void hasAtlasOrArgusTag_mcpServer_true() throws Exception {
        HttpCallParser parser = newParser();
        CollectionTags mcp = new CollectionTags(0, Constants.AKTO_MCP_SERVER_TAG, "MCP Server", TagSource.KUBERNETES);
        assertTrue(invokeHasAtlasOrArgusTag(parser, collectionWithTags(mcp)));
    }

    @Test
    public void hasAtlasOrArgusTag_endpointSource_true() throws Exception {
        HttpCallParser parser = newParser();
        CollectionTags source = new CollectionTags(0, Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE, TagSource.KUBERNETES);
        assertTrue(invokeHasAtlasOrArgusTag(parser, collectionWithTags(source)));
    }

    @Test
    public void hasAtlasOrArgusTag_unrelatedTag_false() throws Exception {
        HttpCallParser parser = newParser();
        CollectionTags unrelated = new CollectionTags(0, "team", "payments", TagSource.KUBERNETES);
        assertFalse(invokeHasAtlasOrArgusTag(parser, collectionWithTags(unrelated)));
    }

    @Test
    public void hasAtlasOrArgusTag_sourceTagButNotEndpoint_false() throws Exception {
        HttpCallParser parser = newParser();
        CollectionTags source = new CollectionTags(0, Constants.AKTO_ENDPOINT_SOURCE_TAG, "N8N", TagSource.KUBERNETES);
        assertFalse(invokeHasAtlasOrArgusTag(parser, collectionWithTags(source)));
    }

    // ---------- createApiCollectionId routing (case table) ----------

    private HttpResponseParams mcpRequest(String host, String path, String tagsJson) {
        Map<String, List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("host", Collections.singletonList(host));
        reqHeaders.put("content-type", Collections.singletonList("application/json"));
        HttpRequestParams requestParams = new HttpRequestParams(
                "POST", path, "HTTP/1.1", reqHeaders,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}", 0
        );
        HttpResponseParams responseParams = new HttpResponseParams(
                "HTTP/1.1", 200, "OK", new HashMap<>(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}",
                requestParams, 0, String.valueOf(ACCOUNT_ID), false, HttpResponseParams.Source.MIRRORING,
                "", "10.0.1.15", "", "1"
        );
        responseParams.setTags(tagsJson == null ? "" : tagsJson);
        return responseParams;
    }

    private HttpResponseParams normalRequest(String host, String path) {
        Map<String, List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("host", Collections.singletonList(host));
        reqHeaders.put("content-type", Collections.singletonList("application/json"));
        HttpRequestParams requestParams = new HttpRequestParams("GET", path, "HTTP/1.1", reqHeaders, "", 0);
        HttpResponseParams responseParams = new HttpResponseParams(
                "HTTP/1.1", 200, "OK", new HashMap<>(), "{\"id\":1}",
                requestParams, 0, String.valueOf(ACCOUNT_ID), false, HttpResponseParams.Source.MIRRORING,
                "", "10.0.1.15", "", "1"
        );
        responseParams.setTags("");
        return responseParams;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> getHostNameToIdMap(HttpCallParser parser) throws Exception {
        Field f = HttpCallParser.class.getDeclaredField("hostNameToIdMap");
        f.setAccessible(true);
        return (Map<String, Integer>) f.get(parser);
    }

    @Test
    public void case1_freshHost_agenticMatch_noFork() throws Exception {
        grantSecurityTypeAgentic();
        HttpCallParser parser = newParser();

        String host = "case1-fresh.akto.internal";
        int id = parser.createApiCollectionId(mcpRequest(host, "/mcp", null));

        Map<String, Integer> map = getHostNameToIdMap(parser);
        assertTrue(map.containsKey(host));
        assertEquals((Integer) id, map.get(host));
        assertFalse(map.containsKey(host + "-agentic"), "no agentic sibling should exist for a fresh host");
    }

    @Test
    public void case2_plainCollection_normalTraffic_staysPut() throws Exception {
        HttpCallParser parser = newParser();
        String host = "case2-normal.akto.internal";

        int id1 = parser.createApiCollectionId(normalRequest(host, "/api/users/1"));
        int id2 = parser.createApiCollectionId(normalRequest(host, "/api/orders/1"));

        assertEquals(id1, id2);
        assertFalse(getHostNameToIdMap(parser).containsKey(host + "-agentic"));
    }

    @Test
    public void case3_plainCollection_endpointSource_staysPut() throws Exception {
        HttpCallParser parser = newParser();
        String host = "case3-endpoint.akto.internal";

        int normalId = parser.createApiCollectionId(normalRequest(host, "/api/users/1"));
        int mcpId = parser.createApiCollectionId(mcpRequest(host, "/mcp", "{\"source\":\"ENDPOINT\"}"));

        assertEquals(normalId, mcpId, "ENDPOINT-sourced match must not fork");
        assertFalse(getHostNameToIdMap(parser).containsKey(host + "-agentic"));
    }

    // case4 (fresh mixed collection actually forks on an agentic match) lives on the
    // nayan/fix-apicollectionmap-cache-sync branch - it depends on a fix not present here yet
    // (apiCollectionMap isn't updated immediately when createApiCollectionId creates a new
    // collection, only hostNameToIdMap is, so a same-session follow-up request can't see it).

    @Test
    public void case5_alreadyTaggedCollection_neverReForks() throws Exception {
        HttpCallParser parser = newParser();
        String host = "case5-already-tagged.akto.internal";

        // Setup: ENDPOINT-sourced match tags the plain collection directly (no fork).
        int firstId = parser.createApiCollectionId(mcpRequest(host, "/mcp", "{\"source\":\"ENDPOINT\"}"));

        // Now grant the feature and send an ordinary-source agentic match to the same host.
        grantSecurityTypeAgentic();
        int secondId = parser.createApiCollectionId(mcpRequest(host, "/mcp-again", null));

        assertEquals(firstId, secondId, "already Atlas/Argus-tagged collection must not fork again");
        assertFalse(getHostNameToIdMap(parser).containsKey(host + "-agentic"));
    }
}
