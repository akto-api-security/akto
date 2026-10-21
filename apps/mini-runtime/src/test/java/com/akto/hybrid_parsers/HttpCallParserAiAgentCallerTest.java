package com.akto.hybrid_parsers;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SetupDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.Setup;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers ai-agent-caller attribution: when a service is seen calling a host Akto has already
 * classified as an AI asset, the CALLER's collection gets tagged.
 *
 * The behaviour these pin down was previously broken in a specific way. The gate re-derived
 * "is the callee an AI asset" from the current request's URL, so attribution depended on which
 * endpoint the caller happened to hit rather than on what the destination actually is. On a real
 * deployment two services calling the SAME gen-ai gateway were treated differently - one on
 * /v1/chat/completions was tagged, one on /chat/completions was not - and RAG and MCP callers
 * could never be tagged at all, because neither family is reachable through the LLM rules.
 *
 * Self-contained JUnit 5 embedded-Mongo bootstrap, mirroring HttpCallParserOutboundTagsTest.
 */
class HttpCallParserAiAgentCallerTest {

    /** Must be one of HttpCallParser.AI_AGENT_CALLER_TAGGING_ACCOUNTS or every case no-ops. */
    private static final int ACCOUNT_ID = 1736798101;
    /** The key the mirroring daemonset writes the caller's service name under. */
    private static final String SERVICE_TAG_KEY = HttpCallParser.SERVICE_TAG_KEY;
    private static final String CALLER_SERVICE = "translation-service";
    private static final String GATEWAY = "llm-gateway.internal.svc:8080";

    private static final String INBOUND = "1";
    private static final String OUTBOUND = "2";

    private static MongodExecutable mongodExe;
    private static MongodProcess mongod;
    private static int originalActualAccountId;

    @BeforeAll
    static void startMongoAndInit() throws Exception {
        MongodStarter starter = MongodStarter.getDefaultInstance();
        ImmutableMongodConfig mongodConfig = ImmutableMongodConfig.builder()
                .version(Version.Main.PRODUCTION)
                .net(new Net("localhost", 27023, false))
                .build();
        mongodExe = starter.prepare(mongodConfig);
        mongod = mongodExe.start();
        DaoInit.init(new ConnectionString("mongodb://localhost:27023"));
        // Context.actualAccountId is a write-once static shared by every test class in this JVM, so
        // whichever class runs first would otherwise decide the account for all of them. Remember
        // what it was and put it back in @AfterAll, so this class neither depends on nor disturbs
        // the ordering.
        originalActualAccountId = Context.getActualAccountId();
        Context.accountId.set(ACCOUNT_ID);
        forceActualAccountId(ACCOUNT_ID);
    }

    @AfterAll
    static void stopMongo() throws Exception {
        forceActualAccountId(originalActualAccountId);
        if (mongod != null) {
            mongod.stop();
            mongodExe.stop();
        }
    }

    @BeforeEach
    void setup() throws Exception {
        Context.accountId.set(ACCOUNT_ID);
        forceActualAccountId(ACCOUNT_ID);
        OrganizationsDao.instance.getMCollection().drop();
        SetupDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        SetupDao.instance.insertOne(new Setup("ON_PREM"));
    }

    // ---------- fixtures ----------

    /** The destination, carrying whatever agentic tag Akto already decided it has. */
    private int seedCallee(String hostName, String agenticTagKey) {
        int id = hostName.hashCode();
        ApiCollection collection = new ApiCollection(id, hostName, Context.now(),
                new HashSet<>(), hostName, 0, false, true);
        List<CollectionTags> tags = new ArrayList<>();
        if (agenticTagKey != null) {
            tags.add(new CollectionTags(Context.now(), agenticTagKey, "detected",
                    CollectionTags.TagSource.KUBERNETES));
        }
        collection.setTagsList(tags);
        ApiCollectionsDao.instance.insertOne(collection);
        return id;
    }

    /** The caller's own collection, as inbound traffic to it would have created. */
    private int seedCaller(String serviceTag) {
        int id = ApiCollection.generateServiceTagCollectionId(serviceTag);
        ApiCollection collection = new ApiCollection(id, serviceTag, Context.now(),
                new HashSet<>(), null, 0, false, true);
        collection.setServiceTag(serviceTag);
        collection.setTagsList(new ArrayList<>());
        collection.setHostNames(new ArrayList<>());
        ApiCollectionsDao.instance.insertOne(collection);
        return id;
    }

    private HttpCallParser newParser() {
        return new HttpCallParser("test-user", 10, 10, 120, false);
    }

    private HttpResponseParams request(String host, String path, String direction, String callerService) {
        Map<String, List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("host", Collections.singletonList(host));
        reqHeaders.put("content-type", Collections.singletonList("application/json"));
        HttpRequestParams requestParams = new HttpRequestParams(
                "POST", path, "HTTP/1.1", reqHeaders, "{\"messages\":[]}", 0);
        String tagsJson = callerService == null
                ? ""
                : "{\"" + SERVICE_TAG_KEY + "\":\"" + callerService + "\"}";
        return new HttpResponseParams(
                "HTTP/1.1", 200, "OK", new HashMap<>(), "{\"id\":1}",
                requestParams, 0, String.valueOf(ACCOUNT_ID), false, HttpResponseParams.Source.MIRRORING,
                "", "10.0.1.15", "10.0.2.20", direction, tagsJson, new ArrayList<>());
    }

    private CollectionTags callerTag(int collectionId) {
        ApiCollection collection = ApiCollectionsDao.instance.findOne(Filters.eq("_id", collectionId));
        if (collection == null || collection.getTagsList() == null) {
            return null;
        }
        return collection.getTagsList().stream()
                .filter(t -> Constants.AKTO_AI_AGENT_CALLER_TAG.equals(t.getKeyName()))
                .findFirst().orElse(null);
    }

    // ---------- the path-independence this change is for ----------

    @Test
    void genAiCallee_taggedCallerEvenWithoutV1Prefix() {
        seedCallee(GATEWAY, Constants.AKTO_GEN_AI_TAG);
        int callerId = seedCaller(CALLER_SERVICE);

        // /chat/completions, NOT /v1/chat/completions - the literal the old gate required.
        newParser().createApiCollectionId(request(GATEWAY, "/chat/completions", OUTBOUND, CALLER_SERVICE));

        CollectionTags tag = callerTag(callerId);
        assertNotNull(tag, "caller of a known gen-ai host must be tagged regardless of the path it used");
        assertEquals(GATEWAY, tag.getValue(), "the tag should record which AI host was called");
    }

    @Test
    void genAiCallee_taggedOnAnArbitraryPath() {
        seedCallee(GATEWAY, Constants.AKTO_GEN_AI_TAG);
        int callerId = seedCaller(CALLER_SERVICE);

        // Nothing LLM-shaped about this URL at all; the destination is what makes it an AI call.
        newParser().createApiCollectionId(request(GATEWAY, "/api/v1/embeddings", OUTBOUND, CALLER_SERVICE));

        assertNotNull(callerTag(callerId), "attribution must follow the destination, not the URL shape");
    }

    // ---------- families that were previously unreachable ----------

    @Test
    void ragCallee_tagsCaller() {
        String ragHost = "vector-store.internal.svc";
        seedCallee(ragHost, Constants.AKTO_RAG_DATABASE_TAG);
        int callerId = seedCaller("search-service");

        newParser().createApiCollectionId(
                request(ragHost, "/v1/documents/search", OUTBOUND, "search-service"));

        assertNotNull(callerTag(callerId),
                "RAG is not reachable through the LLM rules, so this only works off the stored tag");
    }

    @Test
    void mcpCallee_tagsCaller() {
        String mcpHost = "tools-mcp.internal.svc";
        seedCallee(mcpHost, Constants.AKTO_MCP_SERVER_TAG);
        int callerId = seedCaller("routing-service");

        // A redacted JSON-RPC body can never match MCP_METHOD_SET, so the body is deliberately inert.
        newParser().createApiCollectionId(request(mcpHost, "/mcp", OUTBOUND, "routing-service"));

        assertNotNull(callerTag(callerId), "a known mcp-server destination should attribute its caller");
    }

    /**
     * hostName is not unique: a service-tag collection also records the host it was seen on, so
     * several documents can share one hostName and hostNameToIdMap (one id per host) keeps only the
     * last. On real data three collections shared "vector-store.internal.svc" and the map resolved to a
     * service-tag one carrying no agentic tag, hiding the rag-database tag on the host collection
     * and silently dropping every RAG caller. The host collection is keyed by hostName.hashCode().
     */
    @Test
    void agenticTagIsFoundWhenOtherCollectionsShareTheHostname() {
        String sharedHost = "vector-store.internal.svc";
        seedCallee(sharedHost, Constants.AKTO_RAG_DATABASE_TAG);   // id = sharedHost.hashCode()

        // Two service-tag collections that also carry the same hostName, inserted after the host
        // collection so they win hostNameToIdMap.
        for (String svc : new String[] { "search-service", "catalog-service" }) {
            int id = ApiCollection.generateServiceTagCollectionId(svc);
            ApiCollection c = new ApiCollection(id, svc, Context.now(), new HashSet<>(), sharedHost, 0, false, true);
            c.setServiceTag(svc);
            c.setTagsList(new ArrayList<>());
            c.setHostNames(new ArrayList<>());
            ApiCollectionsDao.instance.insertOne(c);
        }

        newParser().createApiCollectionId(
                request(sharedHost, "/v1/documents/search", OUTBOUND, "catalog-service"));

        assertNotNull(callerTag(ApiCollection.generateServiceTagCollectionId("catalog-service")),
                "the agentic tag lives on the host collection; a colliding hostName must not hide it");
    }

    // ---------- guards that must still hold ----------

    @Test
    void ordinaryCallee_doesNotTagCaller() {
        String plainHost = "orders-api.internal.svc";
        seedCallee(plainHost, null);
        int callerId = seedCaller(CALLER_SERVICE);

        newParser().createApiCollectionId(
                request(plainHost, "/v1/orders/search", OUTBOUND, CALLER_SERVICE));

        assertNull(callerTag(callerId), "a destination with no agentic tag must not produce a caller tag");
    }

    @Test
    void inboundTrafficToAgenticHost_doesNotTagCaller() {
        seedCallee(GATEWAY, Constants.AKTO_GEN_AI_TAG);
        int callerId = seedCaller(CALLER_SERVICE);

        // Inbound labels describe the receiver, so there is no caller to attribute.
        newParser().createApiCollectionId(request(GATEWAY, "/chat/completions", INBOUND, CALLER_SERVICE));

        assertNull(callerTag(callerId), "inbound labels name the callee, not a caller");
    }

    @Test
    void outboundWithoutServiceLabel_doesNotTagAnything() {
        seedCallee(GATEWAY, Constants.AKTO_GEN_AI_TAG);

        newParser().createApiCollectionId(request(GATEWAY, "/chat/completions", OUTBOUND, null));

        long tagged = ApiCollectionsDao.instance
                .findAll(Filters.eq("tagsList.keyName", Constants.AKTO_AI_AGENT_CALLER_TAG)).size();
        assertEquals(0, tagged, "with no pod label there is no caller identity to attribute");
    }

    // ---------- callers that have no collection of their own ----------

    @Test
    void callerWithNoCollection_getsStubCarryingTheTag() {
        seedCallee(GATEWAY, Constants.AKTO_GEN_AI_TAG);
        String outboundOnlyService = "batch-worker";
        int callerId = ApiCollection.generateServiceTagCollectionId(outboundOnlyService);
        assertNull(ApiCollectionsDao.instance.findOne(Filters.eq("_id", callerId)),
                "precondition: this service has never received traffic, so it has no collection");

        newParser().createApiCollectionId(
                request(GATEWAY, "/chat/completions", OUTBOUND, outboundOnlyService));

        ApiCollection created = ApiCollectionsDao.instance.findOne(Filters.eq("_id", callerId));
        assertNotNull(created, "a caller seen using AI should be recorded even with no inbound traffic");
        assertEquals(outboundOnlyService, created.getServiceTag());
        assertNotNull(callerTag(callerId), "the stub exists precisely to carry the ai-agent-caller tag");
    }

    @Test
    void stubIsNotCreatedForOrdinaryDestinations() {
        String plainHost = "orders-api.internal.svc";
        seedCallee(plainHost, null);
        String outboundOnlyService = "some-batch-job";
        int callerId = ApiCollection.generateServiceTagCollectionId(outboundOnlyService);

        newParser().createApiCollectionId(request(plainHost, "/v1/orders", OUTBOUND, outboundOnlyService));

        assertNull(ApiCollectionsDao.instance.findOne(Filters.eq("_id", callerId)),
                "stub creation is gated on the callee being an AI asset, so ordinary peers create nothing");
    }

    // ---------- existing tags must survive ----------

    @Test
    void existingCallerTagsArePreserved() {
        seedCallee(GATEWAY, Constants.AKTO_GEN_AI_TAG);
        int callerId = ApiCollection.generateServiceTagCollectionId(CALLER_SERVICE);
        ApiCollection caller = new ApiCollection(callerId, CALLER_SERVICE, Context.now(),
                new HashSet<>(), null, 0, false, true);
        caller.setServiceTag(CALLER_SERVICE);
        caller.setTagsList(new ArrayList<>(Arrays.asList(
                new CollectionTags(Context.now(), "envType", "QA", CollectionTags.TagSource.USER),
                new CollectionTags(Context.now(), "team", "payments", CollectionTags.TagSource.KUBERNETES))));
        caller.setHostNames(new ArrayList<>());
        ApiCollectionsDao.instance.insertOne(caller);

        newParser().createApiCollectionId(request(GATEWAY, "/chat/completions", OUTBOUND, CALLER_SERVICE));

        ApiCollection updated = ApiCollectionsDao.instance.findOne(Filters.eq("_id", callerId));
        List<String> keys = new ArrayList<>();
        updated.getTagsList().forEach(t -> keys.add(t.getKeyName()));
        assertTrue(keys.contains("envType"), "the upsert replaces the whole tags array, so pre-existing tags must be merged");
        assertTrue(keys.contains("team"), "the upsert replaces the whole tags array, so pre-existing tags must be merged");
        assertTrue(keys.contains(Constants.AKTO_AI_AGENT_CALLER_TAG));
    }

    @Test
    void callerIsNotTaggedTwice() {
        seedCallee(GATEWAY, Constants.AKTO_GEN_AI_TAG);
        int callerId = seedCaller(CALLER_SERVICE);

        HttpCallParser parser = newParser();
        parser.createApiCollectionId(request(GATEWAY, "/chat/completions", OUTBOUND, CALLER_SERVICE));
        parser.createApiCollectionId(request(GATEWAY, "/v1/chat/completions", OUTBOUND, CALLER_SERVICE));

        ApiCollection updated = ApiCollectionsDao.instance.findOne(Filters.eq("_id", callerId));
        long count = updated.getTagsList().stream()
                .filter(t -> Constants.AKTO_AI_AGENT_CALLER_TAG.equals(t.getKeyName())).count();
        assertEquals(1, count, "repeat observations must not accumulate duplicate tags");
    }

    /**
     * Context.setActualAccountId is write-once - it logs and returns on every call after the first -
     * so the account cannot be set through the public API once anything in this JVM has claimed it.
     */
    private static void forceActualAccountId(int accountId) throws Exception {
        java.lang.reflect.Field value = Context.class.getDeclaredField("actualAccountId");
        value.setAccessible(true);
        value.setInt(null, accountId);
        java.lang.reflect.Field initialised = Context.class.getDeclaredField("actualAccountIdInitialized");
        initialised.setAccessible(true);
        initialised.setBoolean(null, true);
    }

    @Test
    void unenabledAccountIsUntouched() throws Exception {
        int otherAccount = 999999;
        try {
            Context.accountId.set(otherAccount);
            forceActualAccountId(otherAccount);
            SetupDao.instance.insertOne(new Setup("ON_PREM"));
            seedCallee(GATEWAY, Constants.AKTO_GEN_AI_TAG);
            int callerId = seedCaller(CALLER_SERVICE);

            newParser().createApiCollectionId(request(GATEWAY, "/chat/completions", OUTBOUND, CALLER_SERVICE));

            assertNull(callerTag(callerId), "the feature is account-gated and must stay inert elsewhere");
            assertFalse(ApiCollectionsDao.instance
                    .findAll(Filters.eq("tagsList.keyName", Constants.AKTO_AI_AGENT_CALLER_TAG)).size() > 0);
        } finally {
            ApiCollectionsDao.instance.getMCollection().drop();
            SetupDao.instance.getMCollection().drop();
            Context.accountId.set(ACCOUNT_ID);
            forceActualAccountId(ACCOUNT_ID);
        }
    }
}
