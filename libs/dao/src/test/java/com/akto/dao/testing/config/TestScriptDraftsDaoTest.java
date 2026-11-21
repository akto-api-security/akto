package com.akto.dao.testing.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;

import com.akto.dao.context.Context;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.config.TestScriptDraft;
import com.akto.utils.MongoBasedTest;

public class TestScriptDraftsDaoTest extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        TestScriptDraftsDao.instance.getMCollection().drop();
        TestScriptsDao.instance.getMCollection().drop();
        TestScriptDraftsDao.instance.createIndicesIfAbsent();
    }

    private static TestScriptDraft draft(String validationId) {
        return new TestScriptDraft(TestScriptDraft.draftId(validationId, TestScript.Type.POST_REQUEST), "body = 1;",
            TestScript.Type.POST_REQUEST, "a@x.com", 10, new Date(System.currentTimeMillis() + 60_000));
    }

    @Test
    public void roundTripsInheritedAndOwnFields() {
        TestScriptDraftsDao.instance.insertIfAbsent(draft("v1"));
        TestScriptDraft read = TestScriptDraftsDao.instance.findById("ai-draft-v1-post");
        assertEquals("body = 1;", read.getJavascript());
        assertEquals(TestScript.Type.POST_REQUEST, read.getType());
        assertEquals("a@x.com", read.getCreatedBy());
        assertTrue(read.getExpireAt() != null);
    }

    @Test
    public void retriedInsertKeepsOneDocument() {
        TestScriptDraftsDao.instance.insertIfAbsent(draft("v1"));
        TestScriptDraftsDao.instance.insertIfAbsent(draft("v1"));
        assertEquals(1, TestScriptDraftsDao.instance.count(new Document()));
    }

    @Test
    public void draftsNeverTouchTheLibrary() {
        TestScriptDraftsDao.instance.insertIfAbsent(draft("v1"));
        assertEquals(0, TestScriptsDao.instance.count(new Document()));
        assertNull(TestScriptsDao.instance.findById("ai-draft-v1-post"));
    }

    @Test
    public void everyDocumentIsUnderTheTtlIndex() {
        boolean found = false;
        for (Document idx : TestScriptDraftsDao.instance.getMCollection().listIndexes()) {
            if (((Document) idx.get("key")).containsKey(TestScriptDraft.EXPIRE_AT)) {
                found = ((Number) idx.get("expireAfterSeconds")).longValue() == 0L;
            }
        }
        assertTrue(found);
    }

    @Test
    public void draftIds() {
        assertEquals("ai-draft-v1-pre", TestScriptDraft.draftId("v1", TestScript.Type.PRE_REQUEST));
        assertTrue(TestScriptDraft.isDraftId("ai-draft-v1-pre"));
        assertFalse(TestScriptDraft.isDraftId("sign"));
        assertNull(TestScriptDraftsDao.instance.findById("not-a-draft"));
        assertThrows(IllegalArgumentException.class, () -> TestScriptDraft.draftId("v1", TestScript.Type.PRE_TEST));
    }
}
