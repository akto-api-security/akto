package com.akto.data_actor;

import java.util.*;

import com.akto.bulk_update_util.ApiInfoBulkUpdate;
import com.akto.dao.*;
import com.akto.dao.filter.MergedUrlsDao;
import com.akto.dto.filter.MergedUrls;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.file.FilesDao;
import com.akto.dao.upload.FileUploadsDao;
import com.akto.dto.files.File;
import com.akto.dto.upload.SwaggerFileUpload;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dao.threat_detection.ApiHitCountInfoDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.APIConfig;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.AktoDataType;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.CustomDataType;
import com.akto.dto.Log;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.Setup;
import com.akto.dto.billing.Organization;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class DbLayer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DbLayer.class, LoggerMaker.LogDb.DASHBOARD);

    public DbLayer() {
    }

    public static List<CustomDataType> fetchCustomDataTypes() {
        return CustomDataTypeDao.instance.findAll(new BasicDBObject());
    }

    public static List<AktoDataType> fetchAktoDataTypes() {
        return AktoDataTypeDao.instance.findAll(new BasicDBObject());
    }

    public static List<CustomAuthType> fetchCustomAuthTypes() {
        return CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true);
    }

    public static void updateApiCollectionName(int vxlanId, String name) {
        ApiCollectionsDao.instance.getMCollection().updateMany(
                Filters.eq(ApiCollection.VXLAN_ID, vxlanId),
                Updates.set(ApiCollection.NAME, name)
        );
    }

    public static void updateCidrList(List<String> cidrList) {
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(), Updates.addEachToSet("privateCidrList", cidrList),
                new UpdateOptions().upsert(true)
        );
    }

    public static AccountSettings fetchAccountSettings() {
        return AccountSettingsDao.instance.findOne(new BasicDBObject());
    }

    public static AccountSettings fetchAccountSettings(int accountId) {
        Bson filters = Filters.eq("_id", accountId);
        return AccountSettingsDao.instance.findOne(filters);
    }

    public static List<ApiInfo> fetchApiInfos() {
        return ApiInfoDao.instance.findAll(new BasicDBObject());
    }

    public static List<ApiInfo> fetchApiRateLimits(ApiInfo.ApiInfoKey lastApiInfoKey) {
        // Filter for documents that have both rateLimits and rateLimitConfidence fields
        Bson existsFilter = Filters.and(
            Filters.ne("rateLimits", null),
            Filters.ne("rateLimitConfidence", null)
        );
        
        Bson filters = existsFilter;
        
        // Add pagination filter if lastApiInfoKey is provided
        if (lastApiInfoKey != null) {
            // Create proper compound key comparison for pagination
            // This handles the compound _id structure properly
            Bson paginationFilter = Filters.or(
                Filters.gt("_id.apiCollectionId", lastApiInfoKey.getApiCollectionId()),
                Filters.and(
                    Filters.eq("_id.apiCollectionId", lastApiInfoKey.getApiCollectionId()),
                    Filters.gt("_id.method", lastApiInfoKey.getMethod())
                ),
                Filters.and(
                    Filters.eq("_id.apiCollectionId", lastApiInfoKey.getApiCollectionId()),
                    Filters.eq("_id.method", lastApiInfoKey.getMethod()),
                    Filters.gt("_id.url", lastApiInfoKey.getUrl())
                )
            );
            filters = Filters.and(existsFilter, paginationFilter);
        }

        int limit = 1000;
        
        Bson projection = Projections.fields(
            Projections.include("_id", "rateLimits", "rateLimitConfidence")
        );
        
        // Ensure consistent ordering with compound _id sorting
        Bson sort = Sorts.orderBy(
            Sorts.ascending("_id.apiCollectionId"),
            Sorts.ascending("_id.method"), 
            Sorts.ascending("_id.url")
        );
        
        return ApiInfoDao.instance.findAll(filters, 0, limit, sort, projection);
    }

    public static List<ApiInfo> fetchNonTrafficApiInfos() {
        List<ApiCollection> nonTrafficApiCollections = ApiCollectionsDao.instance.fetchNonTrafficApiCollections();
        List<Integer> apiCollectionIds = new ArrayList<>();
        for (ApiCollection apiCollection: nonTrafficApiCollections) {
            apiCollectionIds.add(apiCollection.getId());
        }
        return ApiInfoDao.instance.findAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
    }

    public static void bulkWriteApiInfo(List<ApiInfo> apiInfoList) {
        List<WriteModel<ApiInfo>> writesForApiInfo = ApiInfoBulkUpdate.getUpdatesForApiInfo(apiInfoList);
        ApiInfoDao.instance.getMCollection().bulkWrite(writesForApiInfo);
    }
    public static void bulkWriteSingleTypeInfo(List<WriteModel<SingleTypeInfo>> writesForSingleTypeInfo) {
        BulkWriteResult res = SingleTypeInfoDao.instance.getMCollection().bulkWrite(writesForSingleTypeInfo);
    }

    public static void bulkWriteSampleData(List<WriteModel<SampleData>> writesForSampleData) {
        SampleDataDao.instance.getMCollection().bulkWrite(writesForSampleData);
    }

    public static void bulkWriteSensitiveSampleData(List<WriteModel<SensitiveSampleData>> writesForSensitiveSampleData) {
        SensitiveSampleDataDao.instance.getMCollection().bulkWrite(writesForSensitiveSampleData);
    }

    public static void bulkWriteTrafficInfo(List<WriteModel<TrafficInfo>> writesForTrafficInfo) {
        TrafficInfoDao.instance.getMCollection().bulkWrite(writesForTrafficInfo);
    }

    public static void bulkWriteTrafficMetrics(List<WriteModel<TrafficMetrics>> writesForTrafficMetrics) {
        TrafficMetricsDao.instance.getMCollection().bulkWrite(writesForTrafficMetrics);
    }

    public static void bulkWriteSensitiveParamInfo(List<WriteModel<SensitiveParamInfo>> writesForSensitiveParamInfo) {
        SensitiveParamInfoDao.instance.getMCollection().bulkWrite(writesForSensitiveParamInfo);
    }

    public static APIConfig fetchApiconfig(String configName) {
        return APIConfigsDao.instance.findOne(Filters.eq("name", configName));
    }

    public static List<SingleTypeInfo> fetchSti(Bson filterQ) {
        // add limit, offset
        return SingleTypeInfoDao.instance.findAll(filterQ, Projections.exclude(SingleTypeInfo._VALUES));
    }

//    public static List<SingleTypeInfo> fetchStiBasedOnId(ObjectId id) {
//        // add limit, offset
//        Bson filters = Filters.gt("_id", id);
//        Bson sort = Sorts.descending("_id") ;
//        return SingleTypeInfoDao.instance.findAll(filters, 0, 20000, sort, Projections.exclude(SingleTypeInfo._VALUES));
//    }

    public static List<SingleTypeInfo> fetchStiBasedOnHostHeaders() {
        Bson filterForHostHeader = SingleTypeInfoDao.filterForHostHeader(-1,false);
        Bson filterQ = Filters.and(filterForHostHeader, Filters.regex(SingleTypeInfo._URL, "STRING|INTEGER"));
        return SingleTypeInfoDao.instance.findAll(filterQ, Projections.exclude(SingleTypeInfo._VALUES));
    }

    public static List<Integer> fetchApiCollectionIds() {
        List<Integer> apiCollectionIds = new ArrayList<>();
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject(),
                Projections.include("_id"));
        for (ApiCollection apiCollection: apiCollections) {
            apiCollectionIds.add(apiCollection.getId());
        }
        return apiCollectionIds;
    }

    public static long fetchEstimatedDocCount() {
        return SingleTypeInfoDao.instance.getMCollection().estimatedDocumentCount();
    }

    public static List<RuntimeFilter> fetchRuntimeFilters() {
        return RuntimeFilterDao.instance.findAll(new BasicDBObject());
    }

    public static List<Integer> fetchNonTrafficApiCollectionsIds() {
        List<ApiCollection> nonTrafficApiCollections = ApiCollectionsDao.instance.fetchNonTrafficApiCollections();
        List<Integer> apiCollectionIds = new ArrayList<>();
        for (ApiCollection apiCollection: nonTrafficApiCollections) {
            apiCollectionIds.add(apiCollection.getId());
        }

        return apiCollectionIds;
    }

    public static List<SingleTypeInfo> fetchStiOfCollections() {
        List<Integer> apiCollectionIds = fetchNonTrafficApiCollectionsIds();
        Bson filters = Filters.in(SingleTypeInfo._API_COLLECTION_ID, apiCollectionIds);
        List<SingleTypeInfo> stis = new ArrayList<>();
        try {
            stis = SingleTypeInfoDao.instance.findAll(filters);
            for (SingleTypeInfo sti: stis) {
                try {
                    sti.setStrId(sti.getId().toHexString());
                } catch (Exception e) {
                    System.out.println("error" + e);
                }
            }
        } catch (Exception e) {
            System.out.println("error" + e);
        }
        return stis;
    }

    public static List<SensitiveParamInfo> getUnsavedSensitiveParamInfos() {
        return SensitiveParamInfoDao.instance.findAll(
                Filters.and(
                        Filters.or(
                                Filters.eq(SensitiveParamInfo.SAMPLE_DATA_SAVED,false),
                                Filters.not(Filters.exists(SensitiveParamInfo.SAMPLE_DATA_SAVED))
                        ),
                        Filters.eq(SensitiveParamInfo.SENSITIVE, true)
                )
        );
    }

    public static List<SingleTypeInfo> fetchSingleTypeInfo(int lastFetchTimestamp, String lastSeenObjectId, boolean resolveLoop) {
        if (resolveLoop) {
            Bson filters = Filters.eq("timestamp", lastFetchTimestamp);
            if (lastSeenObjectId != null) {
                filters = Filters.and(filters, Filters.gt("_id", new ObjectId(lastSeenObjectId)));
            }
            Bson sort = Sorts.ascending(Arrays.asList("_id"));
            return SingleTypeInfoDao.instance.findAll(filters, 0, 1000, sort, Projections.exclude(SingleTypeInfo._VALUES));
        } else {
            Bson filters = Filters.gte("timestamp", lastFetchTimestamp);
            Bson sort = Sorts.ascending("timestamp");
            return SingleTypeInfoDao.instance.findAll(filters, 0, 1000, sort, Projections.exclude(SingleTypeInfo._VALUES));
        }
    }

    public static List<SingleTypeInfo> fetchAllSingleTypeInfo() {
        return SingleTypeInfoDao.instance.findAll(new BasicDBObject(), Projections.exclude(SingleTypeInfo._VALUES));
    }

    public static void updateRuntimeVersion(String fieldName, String version) {
        AccountSettingsDao.instance.updateOne(
                        AccountSettingsDao.generateFilter(),
                        Updates.set(fieldName, version)
                );
    }

    public static Account fetchActiveAccount() {
        Bson activeFilter = Filters.or(
                Filters.exists(Account.INACTIVE_STR, false),
                Filters.eq(Account.INACTIVE_STR, false)
        );

        return AccountsDao.instance.findOne(activeFilter);
    }

    public static void updateKafkaIp(String currentInstanceIp) {
        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.CENTRAL_KAFKA_IP, currentInstanceIp+":9092")
        );
    }

    public static List<ApiInfo.ApiInfoKey> fetchEndpointsInCollection() {
        int apiCollectionId = -1;
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId =
                new BasicDBObject("apiCollectionId", "$apiCollectionId")
                        .append("url", "$url")
                        .append("method", "$method");

        if (apiCollectionId != -1) {
            pipeline.add(Aggregates.match(Filters.eq("apiCollectionId", apiCollectionId)));
        }

        Bson projections = Projections.fields(
                Projections.include("timestamp", "apiCollectionId", "url", "method")
        );

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        pipeline.add(Aggregates.project(projections));
        pipeline.add(Aggregates.group(groupedId));
        pipeline.add(Aggregates.sort(Sorts.descending("startTs")));

        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        List<ApiInfo.ApiInfoKey> endpoints = new ArrayList<>();
        while(endpointsCursor.hasNext()) {
            BasicDBObject v = endpointsCursor.next();
            try {
                BasicDBObject vv = (BasicDBObject) v.get("_id");
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(
                        (int) vv.get("apiCollectionId"),
                        (String) vv.get("url"),
                        URLMethods.Method.fromString((String) vv.get("method"))
                );
                endpoints.add(apiInfoKey);
            } catch (Exception e) {
                e.printStackTrace();

            }
        }

        return endpoints;
    }

    public static void createCollectionSimple(int vxlanId) {
        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);

        ApiCollectionsDao.instance.getMCollection().updateOne(
                Filters.eq("_id", vxlanId),
                Updates.combine(
                        Updates.set(ApiCollection.VXLAN_ID, vxlanId),
                        Updates.setOnInsert("startTs", Context.now()),
                        Updates.setOnInsert("urls", new HashSet<>())
                ),
                updateOptions
        );
    }

    public static void createCollectionForHost(String host, int id) {

        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);

        Bson updates = Updates.combine(
            Updates.setOnInsert("_id", id),
            Updates.setOnInsert("startTs", Context.now()),
            Updates.setOnInsert("urls", new HashSet<>())
        );

        ApiCollectionsDao.instance.getMCollection().findOneAndUpdate(Filters.eq(ApiCollection.HOST_NAME, host), updates, updateOptions);
    }

    public static void insertRuntimeLog(Log log) {
        RuntimeLogsDao.instance.insertOne(log);
    }

    public static void insertAnalyserLog(Log log) {
        AnalyserLogsDao.instance.insertOne(log);
    }

    public static void modifyHybridSaasSetting(boolean isHybridSaas) {
        Integer accountId = Context.accountId.get();
        AccountsDao.instance.updateOne(Filters.eq("_id", accountId), Updates.set(Account.HYBRID_SAAS_ACCOUNT, isHybridSaas));
    }

    public static Setup fetchSetup() {
        Setup setup = SetupDao.instance.findOne(new BasicDBObject());
        return setup;
    }

    public static List<ApiCollection> fetchApiCollections() {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject(),
                Projections.include("_id"));
        return apiCollections;
    }

    public static List<ApiCollection> fetchAllApiCollections() {
        return ApiCollectionsDao.instance.findAll(new BasicDBObject(), Projections.exclude("urls", "conditions"));
    }

    public static Organization fetchOrganization(int accountId) {
        return OrganizationsDao.instance.findOne(Filters.eq(Organization.ACCOUNTS, accountId));
    }

    public static void bulkWriteSuspectSampleData(List<WriteModel<SuspectSampleData>> writesForSingleTypeInfo) {
        SuspectSampleDataDao.instance.getMCollection().bulkWrite(writesForSingleTypeInfo);
    }

    public static List<YamlTemplate> fetchFilterYamlTemplates() {
        return FilterYamlTemplateDao.instance.findAll(Filters.empty());
    }

    public static void insertTestingLog(Log log) {
        LogsDao.instance.insertOne(log);
    }

    public static void insertProtectionLog(Log log) {
        ProtectionLogsDao.instance.insertOne(log);
    }

    public static Set<MergedUrls> fetchMergedUrls() {
        return MergedUrlsDao.instance.getMergedUrls();
    }

    public static void bulkinsertApiHitCount(List<ApiHitCountInfo> apiHitCountInfoList) throws Exception {
        try {
            List<WriteModel<ApiHitCountInfo>> updates = new ArrayList<>();
            for (ApiHitCountInfo apiHitCountInfo: apiHitCountInfoList) {
                // Create a filter to find existing documents with the same key fields
                Bson filter = Filters.and(
                    Filters.eq("apiCollectionId", apiHitCountInfo.getApiCollectionId()),
                    Filters.eq("url", apiHitCountInfo.getUrl()),
                    Filters.eq("method", apiHitCountInfo.getMethod()),
                    Filters.eq("ts", apiHitCountInfo.getTs())
                );

                // Use updateOne with upsert instead of insertOne to ensure uniqueness
                updates.add(new UpdateOneModel<>(
                    filter,
                    Updates.combine(
                        Updates.setOnInsert("apiCollectionId", apiHitCountInfo.getApiCollectionId()),
                        Updates.setOnInsert("url", apiHitCountInfo.getUrl()),
                        Updates.setOnInsert("method", apiHitCountInfo.getMethod()),
                        Updates.setOnInsert("ts", apiHitCountInfo.getTs()),
                        Updates.set("count", apiHitCountInfo.getCount())
                    ),
                    new UpdateOptions().upsert(true)
                ));
            }
            ApiHitCountInfoDao.instance.getMCollection().bulkWrite(updates);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in bulkinsertApiHitCount " + e.toString());
            throw e;
        }
    }

    public static String fetchOpenApiSchema(int apiCollectionId) {

        Bson sort = Sorts.descending("uploadTs");
        SwaggerFileUpload fileUpload = FileUploadsDao.instance.getSwaggerMCollection().find(Filters.eq("collectionId", apiCollectionId)).sort(sort).limit(1).projection(Projections.fields(Projections.include("swaggerFileId"))).first();
        if (fileUpload == null) {
            return null;
        }

        ObjectId objectId = new ObjectId(fileUpload.getSwaggerFileId());

        File file = FilesDao.instance.findOne(Filters.eq("_id", objectId));
        if (file == null) {
            return null;
        }

        return file.getCompressedContent();
    }

    public static void insertDataIngestionLog(Log log) {
        DataIngestionLogsDao.instance.insertOne(log);
    }
}
