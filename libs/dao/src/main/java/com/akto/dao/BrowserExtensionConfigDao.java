package com.akto.dao;

import com.akto.dto.BrowserExtensionConfig;
import com.akto.dto.BrowserExtensionConfigCommon;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class BrowserExtensionConfigDao extends AccountsContextDao<BrowserExtensionConfig> {

    public static final String COLLECTION_NAME = "browser_extension_configs";
    public static final int FULL_SYNC_PAGE_SIZE = 50;         // default when the client sends no limit
    public static final int MAX_FULL_SYNC_PAGE_SIZE = 500;    // cap on a client-sent limit
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

    /** A v3 response: the configs, and the _id to send back for the next page (null = no more). */
    @Getter
    @AllArgsConstructor
    public static class ConfigsV3 {
        private final List<BrowserExtensionConfigCommon> configs;
        private final String nextCursor;
    }

    /**
     * Read backing the v3 API. The account's own configs are few, so they are read whole; the common
     * catalogue is read in Mongo.
     *  - delta ({@code updatedAfter > 0}): the same merged catalogue as v2, narrowed to configs with
     *    updatedAt strictly after it (indexed on updatedAt), in one response.
     *  - full sync: the catalogue in pages by _id, {@code limit} per page (default
     *    {@link #FULL_SYNC_PAGE_SIZE}, at most {@link #MAX_FULL_SYNC_PAGE_SIZE}). The client sends the
     *    {@code nextCursor} of the previous page as {@code cursor} until {@code nextCursor} comes back null. Opt-outs are
     *    applied to every page, the account's custom hosts ride on the last page.
     */
    public ConfigsV3 findConfigsV3(long updatedAfter, String cursor, int limit) {
        List<BrowserExtensionConfigCommon> account = findAllAccountConfigs();
        List<String> accountHosts = new ArrayList<>();
        for (BrowserExtensionConfigCommon a : account) {
            accountHosts.add(a.getHost());
        }

        if (updatedAfter > 0) {
            List<BrowserExtensionConfigCommon> common =
                    BrowserExtensionConfigCommonDao.instance.findActiveConfigsUpdatedAfter(updatedAfter, accountHosts);
            return new ConfigsV3(BrowserExtensionConfigCommon.updatedAfter(
                    BrowserExtensionConfigCommon.merge(common, account), updatedAfter), null);
        }

        int pageSize = limit <= 0 ? FULL_SYNC_PAGE_SIZE : Math.min(limit, MAX_FULL_SYNC_PAGE_SIZE);
        ObjectId afterId = cursor == null || cursor.isEmpty() ? null : new ObjectId(cursor);   // the cursor is the last _id
        List<BrowserExtensionConfigCommon> fetched =
                BrowserExtensionConfigCommonDao.instance.findActiveConfigsAfterId(afterId, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        List<BrowserExtensionConfigCommon> page = hasMore ? fetched.subList(0, pageSize) : fetched;

        List<BrowserExtensionConfigCommon> configs = BrowserExtensionConfigCommon.withoutOptOuts(page, account);
        if (hasMore) {
            return new ConfigsV3(configs, page.get(page.size() - 1).get_id());
        }
        configs.addAll(BrowserExtensionConfigCommon.customHosts(
                BrowserExtensionConfigCommonDao.instance.findActiveConfigsByHosts(accountHosts), account));
        return new ConfigsV3(configs, null);
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
