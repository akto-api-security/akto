package com.akto.dao.redteam_learnings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

/**
 * Shared by the red-team learning DAOs the database-abstractor writes through. The dashboard reads the same
 * collections with its own DAOs and creates the same indexes from DaoInit and Liquibase 034; the names here
 * must stay the same as there.
 */
public abstract class AgentRedteamDao<T extends AgentRedteamRecord> extends AccountsContextDao<T> {

    private final Set<Integer> indicesReadyForAccount = ConcurrentHashMap.newKeySet();

    public abstract void createIndicesIfAbsent();

    /** The indexes createIndicesIfAbsent makes, by name. */
    protected abstract List<String> indexNames();

    public static Bson identityFilter(int apiCollectionId, String url, String method) {
        return Filters.and(
                Filters.eq(AgentRedteamRecord.API_COLLECTION_ID, apiCollectionId),
                Filters.eq(AgentRedteamRecord.URL, url),
                Filters.eq(AgentRedteamRecord.METHOD, method));
    }

    public static Bson runFilter(String testingRunResultSummaryId, int apiCollectionId, String url, String method) {
        return Filters.and(Filters.eq(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId),
                identityFilter(apiCollectionId, url, method));
    }

    /**
     * The database-abstractor never runs DaoInit.createIndices, so the first write per account in this process makes
     * the collection and its indexes. createIndexIfAbsent reports neither success nor failure, so the indexes are
     * listed afterwards; if one is still missing, the next write tries again.
     */
    public void ensureIndicesOnce() {
        Integer accountId = Context.accountId.get();
        if (accountId == null || !indicesReadyForAccount.add(accountId)) return;
        createIndicesIfAbsent();
        if (!missingIndexNames().isEmpty()) {
            indicesReadyForAccount.remove(accountId);
        }
    }

    public List<String> missingIndexNames() {
        Set<String> present = new HashSet<>();
        try {
            for (Document index : getMCollection().listIndexes()) {
                present.add(index.getString("name"));
            }
        } catch (Exception e) {
            // The collection does not exist yet: every index is missing.
        }
        List<String> missing = new ArrayList<>();
        for (String name : indexNames()) {
            if (!present.contains(name)) missing.add(name);
        }
        return missing;
    }

    /** First, because listing the indexes of a collection that does not exist yet fails and the index would be skipped. */
    protected void ensureCollection() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
    }

    protected void createIndex(String name, Bson keys) {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), keys, new IndexOptions().name(name));
    }

    /** The stored Date decides when a document expires. */
    protected void createTtlIndex(String name, String dateField) {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), Indexes.ascending(dateField),
                new IndexOptions().name(name).expireAfter(0L, TimeUnit.SECONDS));
    }
}
