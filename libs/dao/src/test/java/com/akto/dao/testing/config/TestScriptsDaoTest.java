package com.akto.dao.testing.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;

import com.akto.dao.context.Context;
import com.akto.dto.testing.config.TestScript;
import com.akto.utils.MongoBasedTest;
import com.mongodb.MongoWriteException;

public class TestScriptsDaoTest extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        TestScriptsDao.instance.getMCollection().drop();
        TestCollectionPropertiesDao.instance.getMCollection().drop();
        TestScriptsDao.instance.createIndicesIfAbsent();
    }

    private static TestScript script(String id, String name, TestScript.Type type, int updatedAt) {
        return new TestScript(id, name, "var x = 1;", type, "creator@x.com", "editor@x.com", 100, updatedAt);
    }

    @Test
    public void roundTripsLibraryFields() {
        TestScriptsDao.instance.insertOne(script("s1", "sign-hmac", TestScript.Type.PRE_REQUEST, 200));

        TestScript read = TestScriptsDao.instance.findById("s1");
        assertEquals("sign-hmac", read.getName());
        assertEquals("creator@x.com", read.getCreatedBy());
        assertEquals("editor@x.com", read.getLastUpdatedBy());
        assertEquals(TestScript.Type.PRE_REQUEST, read.getType());
        assertEquals(100, read.getLastCreatedAt());
        assertEquals(200, read.getLastUpdatedAt());
        assertEquals("s1", TestScriptsDao.instance.findByName("sign-hmac").getId());
    }

    @Test
    public void nullLookupsReturnNullWithoutQuerying() {
        assertNull(TestScriptsDao.instance.findById(null));
        assertNull(TestScriptsDao.instance.findByName(null));
    }

    @Test
    public void duplicateNameIsRejectedByIndex() {
        TestScriptsDao.instance.insertOne(script("s1", "same", TestScript.Type.PRE_REQUEST, 1));
        assertThrows(MongoWriteException.class,
            () -> TestScriptsDao.instance.insertOne(script("s2", "same", TestScript.Type.POST_REQUEST, 2)));
        assertEquals(1, TestScriptsDao.instance.count(new Document()));
    }

    @Test
    public void summariesExcludeJavascriptNewestFirst() {
        TestScriptsDao.instance.insertOne(script("old", "a", TestScript.Type.PRE_REQUEST, 10));
        TestScriptsDao.instance.insertOne(script("new", "b", TestScript.Type.POST_REQUEST, 20));

        List<TestScript> summaries = TestScriptsDao.instance.findAllSummaries();
        assertEquals(2, summaries.size());
        assertEquals("new", summaries.get(0).getId());
        assertEquals("old", summaries.get(1).getId());
        for (TestScript s : summaries) {
            assertNull(s.getJavascript());
            assertTrue(s.getName() != null && s.getType() != null);
        }
    }

    @Test
    public void legacyDaoReadsOnlyScriptDocsOfThatType() {
        // a TestCollectionProperty-shaped doc sharing the legacy collection must never be read as a script
        TestCollectionPropertiesDao.instance.getMCollection().withDocumentClass(Document.class)
            .insertOne(new Document("_id", "prop").append("name", "AUTH_TOKEN").append("apiCollectionId", 1));
        TestCollectionPropertiesDao.instance.getMCollection().withDocumentClass(Document.class)
            .insertOne(new Document("_id", "legacy-pre").append("type", "PRE_REQUEST")
                .append("javascript", "pre();").append("author", "a@x.com")
                .append("lastCreatedAt", 5).append("lastUpdatedAt", 6));

        TestScript pre = TestCollectionPropertiesDao.instance.findLegacyTestScript(TestScript.Type.PRE_REQUEST);
        assertEquals("legacy-pre", pre.getId());
        assertEquals("pre();", pre.getJavascript());
        assertEquals("a@x.com", pre.getAuthor());
        assertNull(pre.getName());
        assertNull(TestCollectionPropertiesDao.instance.findLegacyTestScript(TestScript.Type.POST_REQUEST));
        assertThrows(IllegalArgumentException.class, () -> TestCollectionPropertiesDao.instance.findLegacyTestScript(null));
    }
}
