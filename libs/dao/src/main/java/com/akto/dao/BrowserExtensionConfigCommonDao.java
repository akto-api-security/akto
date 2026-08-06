package com.akto.dao;

import com.akto.dto.BrowserExtensionConfigCommon;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    // Sorted by _id ascending: top brands were seeded with the oldest _ids, so they always lead.
    public List<BrowserExtensionConfigCommon> findAllConfigs() {
        return toConfigs(instance.findAll(new BasicDBObject(), 0, 0, new BasicDBObject("_id", 1)));
    }

    /**
     * `active` is optional, so only configs explicitly marked inactive are dropped.
     */
    public List<BrowserExtensionConfigCommon> findActiveConfigs() {
        return toConfigs(instance.findAll(Filters.ne(BrowserExtensionConfigCommon.ACTIVE, false)));
    }

    /**
     * Raw catalogue document for a host (case-insensitive), or null. Returned as a schema-loose
     * {@link Document} so the caller can copy every extraction field the extension needs - including
     * ones not modelled on the DTO (identity, triggerFrame, modelHeader, …) - into an account row.
     */
    public Document findRawByHost(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.trim();
        Document doc = instance.getMCollection().find(Filters.eq(BrowserExtensionConfigCommon.HOST, normalized)).first();
        if (doc == null) {
            doc = instance.getMCollection()
                    .find(Filters.regex(BrowserExtensionConfigCommon.HOST, "^" + Pattern.quote(normalized) + "$", "i"))
                    .first();
        }
        return doc;
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
        return dedupeByHost(configs);
    }

    /**
     * The common collection has no unique index on host, so the same host can be listed multiple times.
     * Collapse duplicates by normalized host, preferring an active entry over an inactive one and the
     * richer document (more paths/operations) on ties, so the dashboard never shows duplicate rows.
     */
    private static List<BrowserExtensionConfigCommon> dedupeByHost(List<BrowserExtensionConfigCommon> configs) {
        Map<String, BrowserExtensionConfigCommon> byHost = new LinkedHashMap<>();
        for (BrowserExtensionConfigCommon config : configs) {
            String key = config.getHost().trim().toLowerCase();
            BrowserExtensionConfigCommon existing = byHost.get(key);
            if (existing == null || isRicher(config, existing)) {
                byHost.put(key, config);
            }
        }
        return new ArrayList<>(byHost.values());
    }

    private static boolean isRicher(BrowserExtensionConfigCommon candidate, BrowserExtensionConfigCommon existing) {
        // an active entry always wins over an inactive one for the same host
        if (candidate.isActive() != existing.isActive()) {
            return candidate.isActive();
        }
        return detailScore(candidate) > detailScore(existing);
    }

    private static int detailScore(BrowserExtensionConfigCommon config) {
        int score = 0;
        if (config.getPaths() != null) {
            score += config.getPaths().size();
        }
        if (config.getOperations() != null) {
            score += config.getOperations().size();
        }
        return score;
    }
}
