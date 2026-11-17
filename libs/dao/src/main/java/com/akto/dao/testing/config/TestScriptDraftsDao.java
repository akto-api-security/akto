package com.akto.dao.testing.config;

import java.util.concurrent.TimeUnit;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.testing.config.TestScriptDraft;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

/** Ask Akto validation drafts. Every document carries expireAt and is deleted by the TTL index. */
public class TestScriptDraftsDao extends AccountsContextDao<TestScriptDraft> {

    public static final TestScriptDraftsDao instance = new TestScriptDraftsDao();

    public static final String COLLECTION_NAME = "test_script_drafts";

    private TestScriptDraftsDao() {}

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), Indexes.ascending(TestScriptDraft.EXPIRE_AT),
            new IndexOptions().name("expireAt_ttl").expireAfter(0L, TimeUnit.SECONDS));
    }

    public TestScriptDraft findById(String id) {
        if (!TestScriptDraft.isDraftId(id)) {
            return null;
        }
        return findOne(Filters.eq(TestScriptDraft.ID, id));
    }

    /** Inserts the draft unless one with its id exists (a retried validation). */
    public void insertIfAbsent(TestScriptDraft draft) {
        try {
            insertOne(draft);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                throw e;
            }
        }
    }

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<TestScriptDraft> getClassT() {
        return TestScriptDraft.class;
    }
}
