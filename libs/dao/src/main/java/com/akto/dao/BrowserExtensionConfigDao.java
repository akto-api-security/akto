package com.akto.dao;

import com.akto.dto.BrowserExtensionConfig;
import com.akto.dto.BrowserExtensionConfigCommon;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * Read backing the v2 API. Every host in the shared catalogue is ON by default for every account;
     * the account collection only stores what a user changed - an opt-out ({@code active:false}) or a
     * custom host. So the extension sees the catalogue overlaid with this account's account-level
     * choices: catalogue hosts minus opt-outs, plus the account's own custom hosts.
     *
     * Configs are read raw and mapped via {@link BrowserExtensionConfigCommon#fromDocument} (their
     * `path`/`modelPath`/`responsePath` are polymorphic and the pojo codec can't decode them), which
     * also keeps the account's createdBy/updatedBy audit/PII out of this public response.
     */
    public List<BrowserExtensionConfigCommon> findActiveConfigsV2() {
        // account rows keyed by host — opt-outs (active:false) + custom hosts
        MongoCollection<Document> accountColl = getMCollection(getDBName(), getCollName(), Document.class);
        Map<String, Document> accountByHost = new HashMap<>();
        try (MongoCursor<Document> cursor = accountColl.find().cursor()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                String host = doc.getString(BrowserExtensionConfig.HOST);
                if (host != null && !host.trim().isEmpty()) {
                    accountByHost.put(host.trim().toLowerCase(), doc);
                }
            }
        }

        List<BrowserExtensionConfigCommon> configs = new ArrayList<>();
        Set<String> commonHosts = new HashSet<>();

        // 1. catalogue hosts are on by default; drop the ones this account opted out
        for (BrowserExtensionConfigCommon cc : BrowserExtensionConfigCommonDao.instance.findActiveConfigs()) {
            if (cc.getHost() == null) {
                continue;
            }
            String key = cc.getHost().trim().toLowerCase();
            // record the host even when opted out, so it is never re-added as a "custom" host below,
            // and never listed twice if the catalogue itself repeats a host
            if (!commonHosts.add(key)) {
                continue;
            }
            Document override = accountByHost.get(key);
            if (override != null && Boolean.FALSE.equals(override.get(BrowserExtensionConfig.ACTIVE))) {
                continue;
            }
            configs.add(cc);
        }

        // 2. account-only custom hosts (not in the catalogue), unless the account disabled them
        for (Map.Entry<String, Document> entry : accountByHost.entrySet()) {
            if (commonHosts.contains(entry.getKey())) {
                continue;
            }
            Document doc = entry.getValue();
            if (Boolean.FALSE.equals(doc.get(BrowserExtensionConfig.ACTIVE))) {
                continue;
            }
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
