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

    /** A v3 response: the configs, and the cursor to send back for the next page (null = no more). */
    @Getter
    @AllArgsConstructor
    public static class ConfigsV3 {
        private final List<BrowserExtensionConfigCommon> configs;
        private final String nextCursor;
    }

    public enum SyncMode {
        FULL,    // the whole catalogue, paged by cursor
        DELTA;   // everything newer than updatedAfter, in one response

        /** No mode = a client that predates it: updatedAfter &gt; 0 meant delta, otherwise full. */
        public static SyncMode resolve(String mode, long updatedAfter) {
            if (mode == null || mode.trim().isEmpty()) {
                return updatedAfter > 0 ? DELTA : FULL;
            }
            return valueOf(mode.trim().toUpperCase());
        }

        /**
         * @return why the request is invalid, or null. Checked only when the client names a mode, so
         *         a request without one stays as lenient as before.
         */
        public static String validate(String mode, long updatedAfter, String cursor, int limit) {
            if (mode == null || mode.trim().isEmpty()) {
                return null;
            }
            SyncMode resolved;
            try {
                resolved = resolve(mode, updatedAfter);
            } catch (IllegalArgumentException e) {
                return "mode must be FULL or DELTA";
            }
            boolean hasCursor = cursor != null && !cursor.isEmpty();
            if (resolved == DELTA) {
                if (updatedAfter <= 0) return "mode DELTA needs updatedAfter greater than 0";
                if (hasCursor || limit != 0) return "mode DELTA does not take cursor or limit";
            } else if (updatedAfter != 0) {
                return "mode FULL does not take updatedAfter";
            }
            return null;
        }
    }

    /**
     * v3 DELTA: the same merged catalogue as v2, narrowed to configs with updatedAt strictly after
     * {@code updatedAfter}, in one response. The common catalogue is filtered in Mongo (indexed on
     * updatedAt); the account's own configs are few, so they are read whole for the merge.
     */
    public ConfigsV3 findDelta(long updatedAfter) {
        List<BrowserExtensionConfigCommon> account = findAllAccountConfigs();
        List<BrowserExtensionConfigCommon> common = BrowserExtensionConfigCommonDao.instance
                .findActiveConfigsUpdatedAfter(updatedAfter, hostsOf(account));
        return new ConfigsV3(BrowserExtensionConfigCommon.updatedAfter(
                BrowserExtensionConfigCommon.merge(common, account), updatedAfter), null);
    }

    /**
     * v3 FULL: the catalogue in pages by _id, {@code limit} per page (default {@link #FULL_SYNC_PAGE_SIZE},
     * at most {@link #MAX_FULL_SYNC_PAGE_SIZE}). The client sends the {@code nextCursor} of the previous
     * page as {@code cursor} until it comes back null. Opt-outs are applied to every page, the account's
     * custom hosts ride on the last page.
     *
     * @throws IllegalArgumentException if the cursor is not one this API handed out
     */
    public ConfigsV3 findFullSyncPage(String cursor, int limit) {
        int pageSize = limit <= 0 ? FULL_SYNC_PAGE_SIZE : Math.min(limit, MAX_FULL_SYNC_PAGE_SIZE);
        ObjectId afterId = cursor == null || cursor.isEmpty() ? null : parseCursor(cursor);   // the cursor is the last _id
        List<BrowserExtensionConfigCommon> account = findAllAccountConfigs();
        List<BrowserExtensionConfigCommon> fetched =
                BrowserExtensionConfigCommonDao.instance.findActiveConfigsAfterId(afterId, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        List<BrowserExtensionConfigCommon> page = hasMore ? fetched.subList(0, pageSize) : fetched;

        List<BrowserExtensionConfigCommon> configs = BrowserExtensionConfigCommon.withoutOptOuts(page, account);
        if (hasMore) {
            return new ConfigsV3(configs, page.get(page.size() - 1).get_id());
        }
        configs.addAll(BrowserExtensionConfigCommon.customHosts(
                BrowserExtensionConfigCommonDao.instance.findActiveConfigsByHosts(hostsOf(account)), account));
        return new ConfigsV3(configs, null);
    }

    private static ObjectId parseCursor(String cursor) {
        if (!ObjectId.isValid(cursor)) {
            throw new IllegalArgumentException("invalid cursor");
        }
        return new ObjectId(cursor);
    }

    private static List<String> hostsOf(List<BrowserExtensionConfigCommon> configs) {
        List<String> hosts = new ArrayList<>();
        for (BrowserExtensionConfigCommon c : configs) {
            hosts.add(c.getHost());
        }
        return hosts;
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
