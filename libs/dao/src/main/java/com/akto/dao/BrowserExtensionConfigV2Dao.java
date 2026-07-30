package com.akto.dao;

import com.akto.dto.BrowserExtensionConfigV2;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the shared browser extension config collection from the common DB.
 * Typed on {@link Document} on purpose - the collection is schema-loose and only the
 * `host` key is guaranteed, so documents are mapped via
 * {@link BrowserExtensionConfigV2#fromDocument(Document)} instead of the pojo codec.
 */
public class BrowserExtensionConfigV2Dao extends CommonContextDao<Document> {

    public static final String COLLECTION_NAME = "browser_extension_configs";
    public static final BrowserExtensionConfigV2Dao instance = new BrowserExtensionConfigV2Dao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<Document> getClassT() {
        return Document.class;
    }

    public List<BrowserExtensionConfigV2> findAllConfigs() {
        return toConfigs(instance.findAll(new BasicDBObject()));
    }

    /**
     * `active` is optional, so only configs explicitly marked inactive are dropped.
     */
    public List<BrowserExtensionConfigV2> findActiveConfigs() {
        return toConfigs(instance.findAll(Filters.ne(BrowserExtensionConfigV2.ACTIVE, false)));
    }

    private List<BrowserExtensionConfigV2> toConfigs(List<Document> docs) {
        List<BrowserExtensionConfigV2> configs = new ArrayList<>();
        if (docs == null) {
            return configs;
        }
        for (Document doc : docs) {
            BrowserExtensionConfigV2 config = BrowserExtensionConfigV2.fromDocument(doc);
            // host is mandatory, so a document without it is not usable
            if (config != null) {
                configs.add(config);
            }
        }
        return configs;
    }
}
