package com.akto.dao;

import com.akto.dto.BrowserExtensionConfig;
import com.akto.dto.BrowserExtensionConfigCommon;
import com.mongodb.BasicDBObject;
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
     * Read backing the v2 API: the active common catalogue overlaid with this account's own choices
     * (opt-outs + custom hosts). The overlay rules live in {@link BrowserExtensionConfigCommon#merge};
     * this method just supplies the two inputs.
     */
    public List<BrowserExtensionConfigCommon> findActiveConfigsV2() {
        List<BrowserExtensionConfigCommon> common = BrowserExtensionConfigCommonDao.instance.findActiveConfigs();
        return BrowserExtensionConfigCommon.merge(common, findAllAccountConfigs());
    }

    /**
     * Read backing the v3 API: the same merged catalogue as v2, narrowed to configs with updatedAt
     * strictly after {@code updatedAfter} (0 = all). The common catalogue is filtered in Mongo (indexed
     * on updatedAt); the account's own configs are few, so they are read whole for the merge. See
     * {@link BrowserExtensionConfigCommon#updatedAfter}.
     */
    public List<BrowserExtensionConfigCommon> findConfigsV3(long updatedAfter) {
        if (updatedAfter <= 0) {
            return BrowserExtensionConfigCommon.updatedAfter(findActiveConfigsV2(), 0);
        }
        List<BrowserExtensionConfigCommon> account = findAllAccountConfigs();
        List<String> accountHosts = new ArrayList<>();
        for (BrowserExtensionConfigCommon a : account) {
            accountHosts.add(a.getHost());
        }
        List<BrowserExtensionConfigCommon> common =
                BrowserExtensionConfigCommonDao.instance.findActiveConfigsUpdatedAfter(updatedAfter, accountHosts);
        return BrowserExtensionConfigCommon.updatedAfter(BrowserExtensionConfigCommon.merge(common, account), updatedAfter);
    }

    /**
     * All account-level configs - active AND inactive - because an opt-out is an inactive doc whose
     * host {@link BrowserExtensionConfigCommon#merge} must remove from the catalogue. Read raw and
     * mapped via {@link BrowserExtensionConfigCommon#fromDocument} (polymorphic path fields the pojo
     * codec can't decode; also keeps the account's createdBy/updatedBy audit/PII out of the response).
     */
    private List<BrowserExtensionConfigCommon> findAllAccountConfigs() {
        List<Document> docs = getMCollection(getDBName(), getCollName(), Document.class)
                .find().into(new ArrayList<>());
        List<BrowserExtensionConfigCommon> configs = new ArrayList<>();
        for (Document doc : docs) {
            BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);
            if (config != null) {
                configs.add(config);
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
