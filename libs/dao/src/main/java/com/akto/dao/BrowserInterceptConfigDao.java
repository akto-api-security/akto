package com.akto.dao;

import com.akto.dto.BrowserInterceptConfigs;
import com.mongodb.client.model.Filters;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a tenant's v2 per-site interception catalogue from its OWN account DB
 * (AccountsContextDao -> DB named by the account id), so every tenant owns and fetches only its own
 * configs - no shared catalogue, no cross-account merge.
 *
 * Typed on {@link Document} on purpose - a config is schema-loose (typed control fields plus
 * arbitrary engine data stored and returned as-is), so documents are passed through untouched
 * rather than mapped onto a pojo.
 */
public class BrowserInterceptConfigDao extends AccountsContextDao<Document> {

    public static final String COLLECTION_NAME = "browser_intercept_configs";
    public static final BrowserInterceptConfigDao instance = new BrowserInterceptConfigDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<Document> getClassT() {
        return Document.class;
    }

    /**
     * `active` is optional, so only configs explicitly marked inactive are dropped.
     */
    public List<Document> findActive() {
        List<Document> docs = instance.findAll(Filters.ne(BrowserInterceptConfigs.ACTIVE, false));
        return docs != null ? docs : new ArrayList<>();
    }
}
