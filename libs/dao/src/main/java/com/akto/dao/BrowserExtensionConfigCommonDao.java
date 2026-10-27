package com.akto.dao;

import com.akto.dto.BrowserExtensionConfigCommon;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the shared browser extension config collection from the common DB.
 * Typed on {@link Document} on purpose - the collection is schema-loose and only the
 * `host` key is guaranteed, so documents are mapped via
 * {@link BrowserExtensionConfigCommon#fromDocument(Document)} instead of the pojo codec.
 */
public class BrowserExtensionConfigCommonDao extends CommonContextDao<Document> {

    public static final String COLLECTION_NAME = "browser_extension_configs";
    public static final BrowserExtensionConfigCommonDao instance = new BrowserExtensionConfigCommonDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<Document> getClassT() {
        return Document.class;
    }

    public List<BrowserExtensionConfigCommon> findAllConfigs() {
        return toConfigs(instance.findAll(new BasicDBObject()));
    }

    /**
     * `active` is optional, so only configs explicitly marked inactive are dropped.
     */
    public List<BrowserExtensionConfigCommon> findActiveConfigs() {
        return toConfigs(instance.findAll(Filters.ne(BrowserExtensionConfigCommon.ACTIVE, false)));
    }

    /**
     * Active configs changed after {@code updatedAfter} (epoch seconds, served by the updatedAt index),
     * sorted by _id; {@code updatedAfter <= 0} means all of them. Also returns the active configs for
     * {@code alwaysIncludeHosts} (any age), so the caller's merge still lets the catalogue entry win
     * over a same-host account row.
     */
    public List<BrowserExtensionConfigCommon> findActiveConfigsUpdatedAfter(long updatedAfter, List<String> alwaysIncludeHosts) {
        Bson filter = Filters.ne(BrowserExtensionConfigCommon.ACTIVE, false);
        if (updatedAfter > 0) {
            Bson changed = Filters.gt(BrowserExtensionConfigCommon.UPDATED_AT, updatedAfter);
            filter = Filters.and(filter, alwaysIncludeHosts.isEmpty()
                    ? changed
                    : Filters.or(changed, Filters.in(BrowserExtensionConfigCommon.HOST, alwaysIncludeHosts)));
        }
        return toConfigs(instance.findAll(filter, 0, 1_000_000, Sorts.ascending("_id")));
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{BrowserExtensionConfigCommon.UPDATED_AT}, true);
    }

    private List<BrowserExtensionConfigCommon> toConfigs(List<Document> docs) {
        List<BrowserExtensionConfigCommon> configs = new ArrayList<>();
        if (docs == null) {
            return configs;
        }
        for (Document doc : docs) {
            BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);
            // host is mandatory, so a document without it is not usable
            if (config != null) {
                configs.add(config);
            }
        }
        return configs;
    }
}
