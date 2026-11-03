package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

public class UnauthenticatedEndpoint extends TestingEndpoints {

    private static int limit = 1000;
    // each write is one updateMany per collection with an $or of this many (url, method) pairs, so keep it small
    private static final int WRITE_BATCH_SIZE = 100;
    // pause after each write batch, and a cap per run, so a large change set is spread over time instead of spiking the primary
    private static final long WRITE_PAUSE_MS = 200;
    private static final int MAX_CHANGES_PER_RUN = 20_000;
    private static final int SYNC_BUFFER_SECONDS = 10 * 60;
    private static final int FIRST_SYNC_LOOKBACK_SECONDS = 5 * Constants.ONE_DAY_TIMESTAMP;
    private static final long SLOW_RUN_WARN_MS = 5 * 60 * 1000L;
    private static final Logger logger = LoggerFactory.getLogger(UnauthenticatedEndpoint.class);

    @BsonIgnore
    private List<ApiInfoKey> apiInfos;

    public UnauthenticatedEndpoint() {
        super(Type.UNAUTHENTICATED, Operator.OR);
    }

    public List<ApiInfoKey> getApiInfos() {
        return apiInfos;
    }

    public void setApiInfos(List<ApiInfoKey> apiInfos) {
        this.apiInfos = apiInfos;
    }

    @Override
    public List<ApiInfoKey> returnApis() {
       return this.apiInfos;
    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        ApiInfo apiInfo = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(key));
        if (apiInfo == null) {
            return false;
        }
        apiInfo.calculateActualAuth();
        if (apiInfo.getActualAuthType() == null || apiInfo.getActualAuthType().isEmpty()) {
            return false;
        }
        Set<AuthType> authTypes = new HashSet<>(apiInfo.getActualAuthType());
        if (authTypes == null || authTypes.isEmpty()) {
            return false;
        }
        if (authTypes.contains(AuthType.UNAUTHENTICATED)) {
            return true;
        }
        return false;
    }

    private static Bson createApiFilters(CollectionType type, ApiInfoKey api) {

        String prefix = getFilterPrefix(type);

        return Filters.and(
                Filters.eq(prefix + SingleTypeInfo._URL, api.getUrl()),
                Filters.eq(prefix + SingleTypeInfo._METHOD, api.getMethod().toString()),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, api.getApiCollectionId()));

    }

    public final static int UNAUTHENTICATED_GROUP_ID = 111_111_120;

    private static final Set<AuthType> UNAUTHENTICATED_AUTH_SET = Collections.singleton(AuthType.UNAUTHENTICATED);

    /**
     * Brings the group in line with api_info (the source of truth for auth types), writing only the apis whose group
     * tag disagrees with their auth types. Auth types only change in runtime upserts, which bump lastSeen, so only apis
     * seen since the last sync are looked at. The last sync time is kept in account settings and is saved only after
     * every write has succeeded; without it, the last 5 days are checked.
     */
    public static void updateCollections(){
        int accountId = Context.accountId.get();
        long startMs = System.currentTimeMillis();
        int syncStartedAt = Context.now();

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        LastCronRunInfo lastRunInfo = accountSettings == null ? null : accountSettings.getLastUpdatedCronInfo();
        int lastSync = lastRunInfo == null ? 0 : lastRunInfo.getLastUnauthenticatedGroupSync();
        boolean hasMarker = lastSync != 0;
        int since = hasMarker ? Math.max(lastSync - SYNC_BUFFER_SECONDS, 0) : syncStartedAt - FIRST_SYNC_LOOKBACK_SECONDS;
        if (!hasMarker) {
            logger.warn(String.format("AccountId: %d unauthenticated group has no last sync time, checking the last %d days (since=%d)",
                    accountId, FIRST_SYNC_LOOKBACK_SECONDS / Constants.ONE_DAY_TIMESTAMP, since));
        }

        SyncResult result = new SyncResult();
        try {
            incrementalUpdate(fetchAllowedCollectionIds(), since, result);
        } catch (Exception e) {
            logger.error(String.format("AccountId: %d unauthenticated group sync failed after collections=%d, scanned=%d, added=%d, removed=%d, took=%d ms, since=%d; last sync time not saved, next run starts from the same point",
                    accountId, result.collections, result.scanned, result.added, result.removed, System.currentTimeMillis() - startMs, since), e);
            return;
        }

        long tookMs = System.currentTimeMillis() - startMs;
        if (result.changes() >= MAX_CHANGES_PER_RUN) {
            // marker left as is: what is already fixed is skipped next time, so the next run carries on from here
            logger.warn(String.format("AccountId: %d unauthenticated group sync hit the cap of %d changes: collections=%d, scanned=%d, added=%d, removed=%d, took=%d ms, since=%d; last sync time not saved, the rest continues next run",
                    accountId, MAX_CHANGES_PER_RUN, result.collections, result.scanned, result.added, result.removed, tookMs, since));
            return;
        }

        AccountSettingsDao.instance.getMCollection().updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.LAST_UNAUTHENTICATED_GROUP_SYNC, syncStartedAt),
            new UpdateOptions().upsert(true));

        String message = String.format("AccountId: %d unauthenticated group synced: since=%d (%s), collections=%d, scanned=%d, added=%d, removed=%d, took=%d ms",
                accountId, since, hasMarker ? "last sync " + (syncStartedAt - lastSync) / 60 + " min ago" : "no last sync, fallback", 
                result.collections, result.scanned, result.added, result.removed, tookMs);
        if (tookMs > SLOW_RUN_WARN_MS) {
            logger.warn(message);
        } else {
            logger.info(message);
        }
    }

    private static Set<Integer> fetchAllowedCollectionIds() {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
            Filters.and(
                UsersCollectionsList.getApiContextFilter(),
                Filters.ne(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.toString())
            ), Projections.include("_id")
        );
        Set<Integer> ids = new HashSet<>();
        for (ApiCollection apiCollection : apiCollections) {
            ids.add(apiCollection.getId());
        }
        return ids;
    }

    private static class SyncResult {
        int collections;
        int scanned;
        int added;
        int removed;

        int changes() {
            return added + removed;
        }
    }

    private static void incrementalUpdate(Set<Integer> allowedCollectionIds, int since, SyncResult result) {
        List<Integer> collectionIds = new ArrayList<>(allowedCollectionIds);
        result.collections = collectionIds.size();
        for (int i = 0; i < collectionIds.size(); i += limit) {
            List<Integer> chunk = collectionIds.subList(i, Math.min(i + limit, collectionIds.size()));
            Bson filter = Filters.and(
                Filters.in(ApiInfo.ID_API_COLLECTION_ID, chunk),
                Filters.gte(ApiInfo.LAST_SEEN, since)
            );

            List<ApiInfoKey> toAdd = new ArrayList<>();
            List<ApiInfoKey> toRemove = new ArrayList<>();
            try (MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().find(filter)
                    .projection(Projections.include("_id", ApiInfo.ALL_AUTH_TYPES_FOUND, ApiInfo.COLLECTION_IDS))
                    .batchSize(limit).cursor()) {
                while (cursor.hasNext()) {
                    ApiInfo apiInfo = cursor.next();
                    result.scanned++;
                    Set<Set<AuthType>> authTypesFound = apiInfo.getAllAuthTypesFound();
                    boolean isUnauthenticated = authTypesFound != null && authTypesFound.contains(UNAUTHENTICATED_AUTH_SET);
                    List<Integer> apiGroupIds = apiInfo.getCollectionIds();
                    boolean inGroup = apiGroupIds != null && apiGroupIds.contains(UNAUTHENTICATED_GROUP_ID);
                    // write only when the group tag disagrees with the auth types
                    if (isUnauthenticated == inGroup) {
                        continue;
                    }
                    (isUnauthenticated ? toAdd : toRemove).add(apiInfo.getId());

                    if (toAdd.size() >= WRITE_BATCH_SIZE) {
                        addToGroup(toAdd);
                        result.added += toAdd.size();
                        toAdd.clear();
                    }
                    if (toRemove.size() >= WRITE_BATCH_SIZE) {
                        removeFromGroup(toRemove);
                        result.removed += toRemove.size();
                        toRemove.clear();
                    }
                    if (result.changes() >= MAX_CHANGES_PER_RUN) {
                        break;
                    }
                }
            }
            addToGroup(toAdd);
            result.added += toAdd.size();
            removeFromGroup(toRemove);
            result.removed += toRemove.size();
            if (result.changes() >= MAX_CHANGES_PER_RUN) {
                break;
            }
        }
    }

    private static void addToGroup(List<ApiInfoKey> keys) {
        if (keys.isEmpty()) {
            return;
        }
        UnauthenticatedEndpoint endpoint = new UnauthenticatedEndpoint();
        endpoint.setApiInfos(new ArrayList<>(keys));
        // synchronous: errors must reach updateCollections() so the last sync time is not saved for a failed run
        ApiCollectionUsers.addToCollectionsForCollectionId(Collections.singletonList(endpoint), UNAUTHENTICATED_GROUP_ID, true);
        pauseBetweenWrites();
    }

    private static void pauseBetweenWrites() {
        try {
            Thread.sleep(WRITE_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void removeFromGroup(List<ApiInfoKey> keys) {
        if (keys.isEmpty()) {
            return;
        }
        UnauthenticatedEndpoint endpoint = new UnauthenticatedEndpoint();
        endpoint.setApiInfos(new ArrayList<>(keys));
        ApiCollectionUsers.removeApisFromCollectionId(Collections.singletonList(endpoint), UNAUTHENTICATED_GROUP_ID, true);
        pauseBetweenWrites();
    }


    @Override
    public Bson createFilters(CollectionType type) {
        Set<ApiInfoKey> apiSet = new HashSet<>(returnApis());
        List<Bson> apiFilters = new ArrayList<>();
        if (apiSet != null && !apiSet.isEmpty()) {
            for (ApiInfoKey api : apiSet) {
                apiFilters.add(createApiFilters(type, api));
            }
            return Filters.or(apiFilters);
        }

        return MCollection.noMatchFilter;
    } 
}
