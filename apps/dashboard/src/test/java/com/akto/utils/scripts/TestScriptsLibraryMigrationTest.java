package com.akto.utils.scripts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.config.TestCollectionPropertiesDao;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dto.testing.config.TestScript;
import com.akto.testing.scripts.LegacyTestScriptAdapter;
import com.mongodb.client.MongoCollection;

public class TestScriptsLibraryMigrationTest extends MongoBasedTest {

    private MongoCollection<Document> legacy() {
        return TestCollectionPropertiesDao.instance.getMCollection().withDocumentClass(Document.class);
    }

    private void insertLegacy(String id, String type, String js, String author) {
        legacy().insertOne(new Document("_id", id).append("type", type).append("javascript", js)
            .append("author", author).append("lastCreatedAt", 10).append("lastUpdatedAt", 20));
    }

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        TestScriptsDao.instance.getMCollection().drop();
        TestCollectionPropertiesDao.instance.getMCollection().drop();
        TestScriptsDao.instance.createIndicesIfAbsent();
        // a real collection-properties doc sharing the legacy collection
        legacy().insertOne(new Document("_id", "prop").append("name", "AUTH_TOKEN").append("apiCollectionId", 0));
    }

    @Test
    public void adapterPresentsLegacyScriptAsDefault() {
        insertLegacy("legacy-pre", "PRE_REQUEST", "pre();", "a@x.com");
        TestScript script = new LegacyTestScriptAdapter().findDefault(TestScript.Type.PRE_REQUEST);
        assertEquals("legacy-pre", script.getId());
        assertEquals(TestScript.DEFAULT_PRE_NAME, script.getName());
        assertEquals("a@x.com", script.getCreatedBy());
        assertEquals("a@x.com", script.getLastUpdatedBy());
        assertNull(new LegacyTestScriptAdapter().findDefault(TestScript.Type.POST_REQUEST));
        assertNull(new LegacyTestScriptAdapter().findById("legacy-pre"));
    }

    @Test
    public void adapterIgnoresLegacyDocWithoutBody() {
        legacy().insertOne(new Document("_id", "broken").append("type", "POST_REQUEST"));
        assertNull(new LegacyTestScriptAdapter().findDefault(TestScript.Type.POST_REQUEST));
    }

    @Test
    public void copiesBothTypesKeepingIdsAndLeavesLegacyUntouched() {
        insertLegacy("legacy-pre", "PRE_REQUEST", "pre();", "a@x.com");
        insertLegacy("legacy-post", "POST_REQUEST", "post();", "b@x.com");

        assertEquals(2, BackwardCompatibilityUtils.migrateTestScriptsToLibrary());

        TestScript pre = TestScriptsDao.instance.findByName(TestScript.DEFAULT_PRE_NAME);
        assertEquals("legacy-pre", pre.getId());
        assertEquals("pre();", pre.getJavascript());
        assertEquals(TestScript.Type.PRE_REQUEST, pre.getType());
        assertEquals(10, pre.getLastCreatedAt());
        assertEquals(20, pre.getLastUpdatedAt());
        TestScript post = TestScriptsDao.instance.findByName(TestScript.DEFAULT_POST_NAME);
        assertEquals("legacy-post", post.getId());
        assertEquals("b@x.com", post.getCreatedBy());

        assertEquals(3, legacy().countDocuments());
    }

    @Test
    public void rerunIsIdempotent() {
        insertLegacy("legacy-pre", "PRE_REQUEST", "pre();", "a@x.com");
        assertEquals(1, BackwardCompatibilityUtils.migrateTestScriptsToLibrary());
        assertEquals(0, BackwardCompatibilityUtils.migrateTestScriptsToLibrary());
        assertEquals(1, TestScriptsDao.instance.count(new Document()));
    }

    @Test
    public void existingDefaultIsNeverOverwritten() {
        insertLegacy("legacy-pre", "PRE_REQUEST", "old();", "a@x.com");
        TestScriptsDao.instance.insertOne(new TestScript("user-made", TestScript.DEFAULT_PRE_NAME, "new();",
            TestScript.Type.PRE_REQUEST, "u@x.com", "u@x.com", 1, 1));

        assertEquals(0, BackwardCompatibilityUtils.migrateTestScriptsToLibrary());
        assertEquals("new();", TestScriptsDao.instance.findByName(TestScript.DEFAULT_PRE_NAME).getJavascript());
    }

    @Test
    public void nothingToCopyWhenAccountNeverHadScripts() {
        assertEquals(0, BackwardCompatibilityUtils.migrateTestScriptsToLibrary());
        assertEquals(0, TestScriptsDao.instance.count(new Document()));
    }
}
