package com.akto.hybrid_parsers;

import com.akto.DaoInit;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.McpAuditInfo;
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

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers browser LLM audit rows written on discovery: browserLlmSite (same grouping as the
 * Agentic assets LLMs tab) and insertBrowserLlmAuditInfo (writes the site's mcp_audit_info row).
 * Same JUnit 5 embedded-Mongo bootstrap as HttpCallParserAtlasArgusTest, on its own port.
 */
class LlmAuditDataTest {

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
        Context.setActualAccountId(ACCOUNT_ID);
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
        Context.setActualAccountId(ACCOUNT_ID);
        McpAuditInfoDao.instance.getMCollection().drop();
    }

    private static String site(String hostName) throws Exception {
        Method m = HttpCallParser.class.getDeclaredMethod("browserLlmSite", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, hostName);
    }

    private static void insert(HttpCallParser parser, String hostName) throws Exception {
        Method m = HttpCallParser.class.getDeclaredMethod("insertBrowserLlmAuditInfo", String.class);
        m.setAccessible(true);
        m.invoke(parser, hostName);
    }

    private static McpAuditInfo row(String site) {
        return McpAuditInfoDao.instance.findOne(Filters.and(
                Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_BROWSER_LLM_TAG),
                Filters.eq(McpAuditInfo.RESOURCE_NAME, site)));
    }

    private static long rowCount() {
        return McpAuditInfoDao.instance.count(Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_BROWSER_LLM_TAG));
    }

    @Test
    void browserLlmSite_dropsUserAndBrowser() throws Exception {
        assertEquals("chatgpt.com", site("janedoe.chrome.chatgpt.com"));
        assertEquals("chat.chatgpt.com", site("janedoe.chrome.chat.chatgpt.com"));
        assertEquals("gemini.google.com", site("JaneDoe-u1.Edge.Gemini.Google.com"));
    }

    @Test
    void browserLlmSite_keepsHostWithoutUserPrefix() throws Exception {
        assertEquals("chatgpt.com", site("chatgpt.com"));
        assertEquals("gemini.google.com", site("gemini.google.com"));
    }

    @Test
    void browserLlmSite_skipsBlankAndNotAttached() throws Exception {
        assertNull(site(null));
        assertNull(site("  "));
        assertNull(site("janedoe.chrome.not-attached"));
    }

    // One row per site comes from the database-abstractor upsert (same path as MCP audit rows); this checks what mini-runtime sends.
    @Test
    void insert_writesSiteRowForEachUser() throws Exception {
        HttpCallParser parser = new HttpCallParser("test-user", 10, 10, 120, false);
        insert(parser, "janedoe.chrome.chatgpt.com");
        insert(parser, "johnsmith.edge.chatgpt.com");

        // Every row written for either user is the site, not the per-user host.
        assertTrue(rowCount() > 0);
        assertEquals(rowCount(), McpAuditInfoDao.instance.count(Filters.eq(McpAuditInfo.RESOURCE_NAME, "chatgpt.com")));
        McpAuditInfo r = row("chatgpt.com");
        assertEquals(Constants.AKTO_BROWSER_LLM_TAG, r.getType());
    }

    @Test
    void insert_skipsBlankAndNotAttachedHosts() throws Exception {
        HttpCallParser parser = new HttpCallParser("test-user", 10, 10, 120, false);
        insert(parser, "");
        insert(parser, "janedoe.chrome.not-attached");

        assertEquals(0, rowCount());
    }
}
