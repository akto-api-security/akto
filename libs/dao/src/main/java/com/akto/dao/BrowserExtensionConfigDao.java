package com.akto.dao;

import com.akto.dto.BrowserExtensionConfig;
import com.akto.dto.BrowserExtensionConfigCommon;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class BrowserExtensionConfigDao extends AccountsContextDao<BrowserExtensionConfig> {

    public static final String COLLECTION_NAME = "browser_extension_configs";
    public static final BrowserExtensionConfigDao instance = new BrowserExtensionConfigDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<BrowserExtensionConfig> getClassT() {
        return BrowserExtensionConfig.class;
    }

    public List<BrowserExtensionConfig> findActiveConfigs() {
        return instance.findAll(Filters.eq(BrowserExtensionConfig.ACTIVE, true));
    }

    /**
     * Read backing the v2 API. The account collection now carries the same rich, per-site
     * extraction config the browser extension supports (see monitoring-configs.js), whose
     * `path`/`modelPath`/`responsePath` fields are polymorphic (a single json path or a list).
     * The pojo codec can't decode those, so - exactly like {@link BrowserExtensionConfigCommon} -
     * documents are read raw and mapped via {@link BrowserExtensionConfigCommon#fromDocument},
     * which yields a typed object exposing only the known config fields (no raw db-key dump,
     * no createdBy/updatedBy PII leak). New extraction fields are surfaced by adding them to
     * that mapper in one place.
     *
     * `active` is optional here; only configs explicitly marked inactive are dropped.
     */
    public List<BrowserExtensionConfigCommon> findActiveConfigsV2() {
        MongoCollection<Document> coll = getMCollection(getDBName(), getCollName(), Document.class);
        List<BrowserExtensionConfigCommon> configs = new ArrayList<>();
        try (MongoCursor<Document> cursor =
                     coll.find(Filters.ne(BrowserExtensionConfigCommon.ACTIVE, false)).cursor()) {
            while (cursor.hasNext()) {
                BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(cursor.next());
                // host is mandatory, so a document without it is not usable
                if (config != null) {
                    configs.add(config);
                }
            }
        }
        return configs;
    }

    public List<BrowserExtensionConfig> findAllSortedByCreatedTimestamp(int skip, int limit) {
        BasicDBObject sort = new BasicDBObject();
        sort.put(BrowserExtensionConfig.CREATED_TIMESTAMP, -1);
        return instance.findAll(new BasicDBObject(), skip, limit, sort);
    }
}
