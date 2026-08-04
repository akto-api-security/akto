package com.akto.dao;

import com.akto.dto.BrowserExtensionConfig;
import com.akto.dto.BrowserExtensionConfigCommon;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

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

    /**
     * The account collection now carries the same rich, per-site extraction config the extension
     * supports (copied from the common catalogue), whose {@code path}/{@code modelPath}/
     * {@code responsePath} fields are polymorphic - a single json path or a list. The pojo codec
     * can't decode those, so documents are read raw and mapped via
     * {@link BrowserExtensionConfigCommon#fromDocument}, exactly like the common catalogue read.
     */
    public List<BrowserExtensionConfigCommon> findAllSortedByCreatedTimestamp(int skip, int limit) {
        MongoCollection<Document> coll = getMCollection(getDBName(), getCollName(), Document.class);
        List<BrowserExtensionConfigCommon> configs = new ArrayList<>();
        try (MongoCursor<Document> cursor = coll.find()
                .sort(new BasicDBObject(BrowserExtensionConfig.CREATED_TIMESTAMP, -1))
                .skip(skip).limit(limit).cursor()) {
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
}
