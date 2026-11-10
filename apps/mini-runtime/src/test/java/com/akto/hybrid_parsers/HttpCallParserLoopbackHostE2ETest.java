package com.akto.hybrid_parsers;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SetupDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.Setup;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * End-to-end coverage for loopback-host capture, driven through the real ingestion entry point.
 *
 * <p>The runtime consumes traffic as JSON strings off Kafka (see Main: {@code
 * HttpCallParser.parseKafkaMessage(record.value())} then {@code parser.syncFunction(...)}, which
 * calls {@code createApiCollectionId}). This test feeds that exact wire format through
 * {@link HttpCallParser#parseKafkaMessage(String)} and then {@link HttpCallParser#createApiCollectionId}
 * against an embedded Mongo, so the whole message-parse → hostname-derivation → collection-creation
 * path runs for real. A literal Kafka broker would add no logic to this path - {@code
 * parseKafkaMessage} is the only thing the consumer does with a record's value - so this reproduces
 * the broker's contribution without the flakiness of a container.</p>
 *
 * <p>Before the loopback fix, a {@code Host: 127.0.0.1:2424} request produced no host-named
 * collection ({@code isValidHostname} rejects IP hosts), so it fell into the vxlan bucket. After the
 * fix, {@code getHostnameForCollection} rewrites it to {@code localhost:2424} and it gets its own
 * host collection.</p>
 */
class HttpCallParserLoopbackHostE2ETest {

    // Must be in HttpCallParser.LOOPBACK_HOST_NORMALIZATION_ACCOUNTS or normalization is a no-op.
    private static final int ACCOUNT_ID = 1760499072;
    // An account NOT in that allow-list: loopback hosts must be left untouched for it.
    private static final int OTHER_ACCOUNT_ID = 1_000_777;
    private static final int VXLAN_ID = 4242;
    private static final String INBOUND = "1";

    private static MongodExecutable mongodExe;
    private static MongodProcess mongod;
    private static int originalActualAccountId;

    @BeforeAll
    static void startMongoAndInit() throws Exception {
        MongodStarter starter = MongodStarter.getDefaultInstance();
        // Distinct port from the other embedded-Mongo tests in this module so they can coexist.
        ImmutableMongodConfig mongodConfig = ImmutableMongodConfig.builder()
                .version(Version.Main.PRODUCTION)
                .net(new Net("localhost", 27024, false))
                .build();
        mongodExe = starter.prepare(mongodConfig);
        mongod = mongodExe.start();
        DaoInit.init(new ConnectionString("mongodb://localhost:27024"));
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
    void setup() {
        Context.accountId.set(ACCOUNT_ID);
        OrganizationsDao.instance.getMCollection().drop();
        SetupDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        SetupDao.instance.insertOne(new Setup("ON_PREM"));
    }

    /**
     * A record exactly as the mirroring collector writes it onto Kafka. {@code requestHeaders} /
     * {@code responseHeaders} are themselves JSON strings, which is how the wire format nests them.
     */
    private String kafkaMessage(String hostHeader) {
        return "{"
                + "\"method\":\"GET\","
                + "\"path\":\"/api/v1/ping\","
                + "\"type\":\"HTTP/1.1\","
                + "\"requestHeaders\":\"{\\\"host\\\":\\\"" + hostHeader + "\\\",\\\"content-type\\\":\\\"application/json\\\"}\","
                + "\"responseHeaders\":\"{\\\"content-type\\\":\\\"application/json\\\"}\","
                + "\"requestPayload\":\"{}\","
                + "\"responsePayload\":\"{\\\"ok\\\":true}\","
                + "\"ip\":\"10.0.0.5\","
                + "\"destIp\":\"127.0.0.1\","
                + "\"direction\":\"" + INBOUND + "\","
                + "\"time\":\"" + Context.now() + "\","
                + "\"statusCode\":\"200\","
                + "\"status\":\"OK\","
                + "\"akto_account_id\":\"" + ACCOUNT_ID + "\","
                + "\"akto_vxlan_id\":\"" + VXLAN_ID + "\","
                + "\"is_pending\":\"false\","
                // MIRRORING is required by useHostCondition for the host-collection path to be taken.
                + "\"source\":\"" + HttpResponseParams.Source.MIRRORING.name() + "\""
                + "}";
    }

    private HttpCallParser newParser() {
        return new HttpCallParser("test-user", 10, 10, 120, false);
    }

    private ApiCollection collectionByHost(String hostName) {
        return ApiCollectionsDao.instance.findOne(Filters.eq("_id", hostName.hashCode()));
    }

    @Test
    @DisplayName("Kafka message with Host 127.0.0.1:2424 lands in a 'localhost:2424' host collection")
    void loopbackHostWithPort_createsHostCollection() throws Exception {
        HttpResponseParams params = HttpCallParser.parseKafkaMessage(kafkaMessage("127.0.0.1:2424"));
        assertNotNull(params, "the Kafka record should parse into response params");

        int collectionId = newParser().createApiCollectionId(params);

        // The collection id is keyed off the NORMALIZED host, not the raw loopback IP.
        assertEquals("localhost:2424".hashCode(), collectionId,
                "loopback host must be bucketed under the normalized 'localhost:2424' collection");

        ApiCollection created = collectionByHost("localhost:2424");
        assertNotNull(created, "a host-named collection must have been created for localhost:2424");
        assertEquals("localhost:2424", created.getHostName(),
                "the collection should carry the normalized hostname");

        // And it must NOT have fallen into the vxlan (non-hostname) bucket.
        assertNull(ApiCollectionsDao.instance.findOne(Filters.eq("_id", VXLAN_ID)),
                "loopback traffic should no longer fall back to the vxlan collection");
    }

    @Test
    @DisplayName("Bare Host 127.0.0.1 (no port) lands in a 'localhost' host collection")
    void loopbackHostNoPort_createsHostCollection() throws Exception {
        HttpResponseParams params = HttpCallParser.parseKafkaMessage(kafkaMessage("127.0.0.1"));
        assertNotNull(params);

        int collectionId = newParser().createApiCollectionId(params);

        assertEquals("localhost".hashCode(), collectionId,
                "bare loopback host must be bucketed under the 'localhost' collection");
        assertNotNull(collectionByHost("localhost"), "a 'localhost' host collection must exist");
    }

    @Test
    @DisplayName("A normal public host is unaffected and keeps its own collection")
    void ordinaryHost_isUnchanged() throws Exception {
        HttpResponseParams params = HttpCallParser.parseKafkaMessage(kafkaMessage("api.example.com"));
        assertNotNull(params);

        int collectionId = newParser().createApiCollectionId(params);

        assertEquals("api.example.com".hashCode(), collectionId,
                "a non-loopback host must be untouched by loopback normalization");
        assertNotNull(collectionByHost("api.example.com"));
    }

    @Test
    @DisplayName("For a non-allow-listed account, loopback is NOT normalized (falls to vxlan bucket)")
    void loopbackHost_notNormalizedForOtherAccount() throws Exception {
        // Switch the actual account to one NOT in LOOPBACK_HOST_NORMALIZATION_ACCOUNTS.
        forceActualAccountId(OTHER_ACCOUNT_ID);
        Context.accountId.set(OTHER_ACCOUNT_ID);
        try {
            HttpResponseParams params = HttpCallParser.parseKafkaMessage(kafkaMessage("127.0.0.1:2424"));
            assertNotNull(params);

            int collectionId = newParser().createApiCollectionId(params);

            // The gate is off for this account: 127.0.0.1:2424 is NOT a valid hostname, so it does
            // not take the host path and must land in the vxlan (non-hostname) bucket instead.
            assertEquals(VXLAN_ID, collectionId,
                    "loopback traffic for a non-allow-listed account must fall into the vxlan bucket");
            assertNull(collectionByHost("localhost:2424"),
                    "no 'localhost:2424' collection may be created for a non-allow-listed account");
            assertNull(collectionByHost("127.0.0.1:2424"),
                    "the raw loopback IP must not form a host collection either");
        } finally {
            // Restore so the class's other tests (which assume ACCOUNT_ID) are unaffected.
            forceActualAccountId(ACCOUNT_ID);
            Context.accountId.set(ACCOUNT_ID);
        }
    }

    private static void forceActualAccountId(int accountId) throws Exception {
        java.lang.reflect.Field value = Context.class.getDeclaredField("actualAccountId");
        value.setAccessible(true);
        value.setInt(null, accountId);
        java.lang.reflect.Field initialised = Context.class.getDeclaredField("actualAccountIdInitialized");
        initialised.setAccessible(true);
        initialised.setBoolean(null, true);
    }
}
