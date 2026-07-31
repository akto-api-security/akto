package com.akto.dao;

import com.akto.dto.BrowserExtensionConfigCommon;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import org.bson.Document;

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
