package com.akto.data_actor;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.dto.filter.MergedUrls;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import com.sun.org.apache.bcel.internal.classfile.Code;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.net.URI;
import java.util.*;

import static com.akto.util.HttpRequestResponseUtils.generateSTIsFromPayload;

public class DbActor extends DataActor {

    public AccountSettings fetchAccountSettings() {
        return DbLayer.fetchAccountSettings();
    }

    public long fetchEstimatedDocCount() {
        return DbLayer.fetchEstimatedDocCount();
    }

    public void updateCidrList(List<String> cidrList) {
        DbLayer.updateCidrList(cidrList);
    }

    public void updateApiCollectionNameForVxlan(int vxlanId, String name) {
        DbLayer.updateApiCollectionName(vxlanId, name);
    }

    public APIConfig fetchApiConfig(String configName) {
        return DbLayer.fetchApiconfig(configName);
    }

    public void bulkWriteSingleTypeInfo(List<Object> writesForApiInfo) {
        ArrayList<WriteModel<SingleTypeInfo>> writes = new ArrayList<>();
        for (Object obj: writesForApiInfo) {
            WriteModel<SingleTypeInfo> write = (WriteModel<SingleTypeInfo>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteSingleTypeInfo(writes);
    }

    public void bulkWriteSensitiveParamInfo(List<Object> writesForSensitiveParamInfo) {
        ArrayList<WriteModel<SensitiveParamInfo>> writes = new ArrayList<>();
        for (Object obj: writesForSensitiveParamInfo) {
            WriteModel<SensitiveParamInfo> write = (WriteModel<SensitiveParamInfo>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteSensitiveParamInfo(writes);
    }

    public void bulkWriteSampleData(List<Object> writesForSampleData) {
        ArrayList<WriteModel<SampleData>> writes = new ArrayList<>();
        for (Object obj: writesForSampleData) {
            WriteModel<SampleData> write = (WriteModel<SampleData>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteSampleData(writes);
    }

    public void bulkWriteSensitiveSampleData(List<Object> writesForSensitiveSampleData) {
        ArrayList<WriteModel<SensitiveSampleData>> writes = new ArrayList<>();
        for (Object obj: writesForSensitiveSampleData) {
            WriteModel<SensitiveSampleData> write = (WriteModel<SensitiveSampleData>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteSensitiveSampleData(writes);
    }

    public void bulkWriteTrafficInfo(List<Object> writesForTrafficInfo) {
        ArrayList<WriteModel<TrafficInfo>> writes = new ArrayList<>();
        for (Object obj: writesForTrafficInfo) {
            WriteModel<TrafficInfo> write = (WriteModel<TrafficInfo>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteTrafficInfo(writes);
    }

    public void bulkWriteTrafficMetrics(List<Object> writesForTrafficMetrics) {
        ArrayList<WriteModel<TrafficMetrics>> writes = new ArrayList<>();
        for (Object obj: writesForTrafficMetrics) {
            WriteModel<TrafficMetrics> write = (WriteModel<TrafficMetrics>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteTrafficMetrics(writes);
    }

    public List<SingleTypeInfo> fetchStiOfCollections(int batchCount, int lastStiFetchTs) {
        return DbLayer.fetchStiOfCollections();
    }

    public List<SingleTypeInfo> fetchAllStis(int batchCount, int lastStiFetchTs) {
        List<SingleTypeInfo> allParams = DbLayer.fetchStiBasedOnHostHeaders();
        allParams.addAll(DbLayer.fetchAllSingleTypeInfo());
        return allParams;
    }

    public List<SensitiveParamInfo> getUnsavedSensitiveParamInfos() {
        return DbLayer.getUnsavedSensitiveParamInfos();
    }

    public List<CustomDataType> fetchCustomDataTypes() {
        return DbLayer.fetchCustomDataTypes();
    }

    public List<AktoDataType> fetchAktoDataTypes() {
        return DbLayer.fetchAktoDataTypes();
    }

    public List<CustomAuthType> fetchCustomAuthTypes() {
        return DbLayer.fetchCustomAuthTypes();
    }

    public List<ApiInfo> fetchApiInfos() {
        return DbLayer.fetchApiInfos();
    }

    public List<ApiInfo> fetchNonTrafficApiInfos() {
        return DbLayer.fetchNonTrafficApiInfos();
    }

    public void bulkWriteApiInfo(List<ApiInfo> apiInfoList) {
        DbLayer.bulkWriteApiInfo(apiInfoList);
    }
    public List<RuntimeFilter> fetchRuntimeFilters() {
        return DbLayer.fetchRuntimeFilters();
    }

    public void updateRuntimeVersion(String fieldName, String version) {
        DbLayer.updateRuntimeVersion(fieldName, version);
    }

    public Account fetchActiveAccount() {
        return DbLayer.fetchActiveAccount();
    }

    public void updateKafkaIp(String currentInstanceIp) {
        DbLayer.updateKafkaIp(currentInstanceIp);
    }

    public List<ApiInfo.ApiInfoKey> fetchEndpointsInCollection() {
        return DbLayer.fetchEndpointsInCollection();
    }

    public List<ApiCollection> fetchApiCollections() {
        return DbLayer.fetchApiCollections();
    }

    public void createCollectionSimple(int vxlanId) {
        DbLayer.createCollectionSimple(vxlanId);
    }

    public void createCollectionForHost(String host, int colId) {
        DbLayer.createCollectionForHost(host, colId);
    }

    public AccountSettings fetchAccountSettingsForAccount(int accountId) {
        return DbLayer.fetchAccountSettings(accountId);
    }

    public void insertRuntimeLog(Log log) {
        DbLayer.insertRuntimeLog(log);
    }

    public void insertAnalyserLog(Log log) {
        DbLayer.insertAnalyserLog(log);
    }

    public void modifyHybridSaasSetting(boolean isHybridSaas) {
        DbLayer.modifyHybridSaasSetting(isHybridSaas);
    }

    public Setup fetchSetup() {
        return DbLayer.fetchSetup();
    }

    public Organization fetchOrganization(int accountId) {
        return DbLayer.fetchOrganization(accountId);
    }

    public void bulkWriteSuspectSampleData(List<Object> writesForSuspectSampleData) {
        ArrayList<WriteModel<SuspectSampleData>> writes = new ArrayList<>();
        for (Object obj: writesForSuspectSampleData) {
            WriteModel<SuspectSampleData> write = (WriteModel<SuspectSampleData>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteSuspectSampleData(writes);
    }

    public List<YamlTemplate> fetchFilterYamlTemplates() {
        return DbLayer.fetchFilterYamlTemplates();
    }

    public void insertTestingLog(Log log) {
        DbLayer.insertTestingLog(log);
    }

    public void insertProtectionLog(Log log) {
        DbLayer.insertProtectionLog(log);
    }
    @Override
    public List<CodeAnalysisRepo> findReposToRun() {
        return CodeAnalysisRepoDao.instance.findAll(
                Filters.eq(CodeAnalysisRepo.CODE_ANALYSIS_RUN_STATE, CodeAnalysisRepo.CodeAnalysisRunState.SCHEDULED)
//                Filters.expr(
//                        Document.parse("{ $gt: [ \"$" + CodeAnalysisRepo.SCHEDULE_TIME + "\", \"$" + CodeAnalysisRepo.LAST_RUN + "\" ] }")
//                )
        );
    }

    public static final int MAX_BATCH_SIZE = 100;
    @Override
    public void syncExtractedAPIs( CodeAnalysisRepo codeAnalysisRepo, List<CodeAnalysisApi> codeAnalysisApisList, boolean isLastBatch) {

        if (codeAnalysisApisList == null) {
            return;
        }

        if (codeAnalysisRepo == null) {
            return;
        }
        String apiCollectionName = codeAnalysisRepo.getProjectName() + "/" + codeAnalysisRepo.getRepoName();
        if (!StringUtils.isEmpty(codeAnalysisRepo.getApiCollectionName())) {
            apiCollectionName = codeAnalysisRepo.getApiCollectionName();
        }

        // Ensure batch size is not exceeded
        if (codeAnalysisApisList.size() > MAX_BATCH_SIZE) {
            return;
        }

        // populate code analysis api map
        Map<String, CodeAnalysisApi> codeAnalysisApisMap = new HashMap<>();
        for (CodeAnalysisApi codeAnalysisApi: codeAnalysisApisList) {
            codeAnalysisApisMap.put(codeAnalysisApi.generateCodeAnalysisApisMapKey(), codeAnalysisApi);
        }

        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(apiCollectionName);
        if (apiCollection == null) {
            apiCollection = new ApiCollection(Context.now(), apiCollectionName, Context.now(), new HashSet<>(), null, 0, false, false);
            ApiCollectionsDao.instance.insertOne(apiCollection);
        }

        /*
         * In some cases it is not possible to determine the type of template url from source code
         * In such cases, we can use the information from traffic endpoints to match the traffic and source code endpoints
         *
         * Eg:
         * Source code endpoints:
         * GET /books/STRING -> GET /books/AKTO_TEMPLATE_STR -> GET /books/INTEGER
         * POST /city/STRING/district/STRING -> POST /city/AKTO_TEMPLATE_STR/district/AKTO_TEMPLATE_STR -> POST /city/STRING/district/INTEGER
         * Traffic endpoints:
         * GET /books/INTEGER -> GET /books/AKTO_TEMPLATE_STR
         * POST /city/STRING/district/INTEGER -> POST /city/AKTO_TEMPLATE_STR/district/AKTO_TEMPLATE_STR
         */

        List<BasicDBObject> trafficApis = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollection.getId(), 0, -1,  60 * 24 * 60 * 60);
        Map<String, String> trafficApiEndpointAktoTemplateStrToOriginalMap = new HashMap<>();
        List<String> trafficApiKeys = new ArrayList<>();
        for (BasicDBObject trafficApi: trafficApis) {
            BasicDBObject trafficApiApiInfoKey = (BasicDBObject) trafficApi.get("_id");
            String trafficApiMethod = trafficApiApiInfoKey.getString("method");
            String trafficApiUrl = trafficApiApiInfoKey.getString("url");
            String trafficApiEndpoint = "";

            // extract path name from url
            try {
                // Directly parse the trafficApiUrl as a URI
                URI uri = new URI(trafficApiUrl);
                trafficApiEndpoint = uri.getPath();

                // Decode any percent-encoded characters in the path
                trafficApiEndpoint = java.net.URLDecoder.decode(trafficApiEndpoint, "UTF-8");

            } catch (Exception e) {
                continue;
            }


            // Ensure endpoint doesn't end with a slash
            if (trafficApiEndpoint.length() > 1 && trafficApiEndpoint.endsWith("/")) {
                trafficApiEndpoint = trafficApiEndpoint.substring(0, trafficApiEndpoint.length() - 1);
            }

            String trafficApiKey = trafficApiMethod + " " + trafficApiEndpoint;
            trafficApiKeys.add(trafficApiKey);

            String trafficApiEndpointAktoTemplateStr = trafficApiEndpoint;

            for (SingleTypeInfo.SuperType type : SingleTypeInfo.SuperType.values()) {
                // Replace each occurrence of Akto template url format with"AKTO_TEMPLATE_STRING"
                trafficApiEndpointAktoTemplateStr = trafficApiEndpointAktoTemplateStr.replace(type.name(), "AKTO_TEMPLATE_STR");
            }

            trafficApiEndpointAktoTemplateStrToOriginalMap.put(trafficApiEndpointAktoTemplateStr, trafficApiEndpoint);
        }

        Map<String, CodeAnalysisApi> tempCodeAnalysisApisMap = new HashMap<>(codeAnalysisApisMap);
        for (Map.Entry<String, CodeAnalysisApi> codeAnalysisApiEntry: codeAnalysisApisMap.entrySet()) {
            String codeAnalysisApiKey = codeAnalysisApiEntry.getKey();
            CodeAnalysisApi codeAnalysisApi = codeAnalysisApiEntry.getValue();

            String codeAnalysisApiEndpointAktoTemplateStr = codeAnalysisApi.getEndpoint();

            for (SingleTypeInfo.SuperType type : SingleTypeInfo.SuperType.values()) {
                // Replace each occurrence of Akto template url format with "AKTO_TEMPLATE_STRING"
                codeAnalysisApiEndpointAktoTemplateStr = codeAnalysisApiEndpointAktoTemplateStr.replace(type.name(), "AKTO_TEMPLATE_STR");
            }

            if(codeAnalysisApiEndpointAktoTemplateStr.contains("AKTO_TEMPLATE_STR") && trafficApiEndpointAktoTemplateStrToOriginalMap.containsKey(codeAnalysisApiEndpointAktoTemplateStr)) {
                CodeAnalysisApi newCodeAnalysisApi = new CodeAnalysisApi(
                        codeAnalysisApi.getMethod(),
                        trafficApiEndpointAktoTemplateStrToOriginalMap.get(codeAnalysisApiEndpointAktoTemplateStr),
                        codeAnalysisApi.getLocation(), codeAnalysisApi.getRequestBody(), codeAnalysisApi.getResponseBody());

                tempCodeAnalysisApisMap.remove(codeAnalysisApiKey);
                tempCodeAnalysisApisMap.put(newCodeAnalysisApi.generateCodeAnalysisApisMapKey(), newCodeAnalysisApi);
            }
        }


        /*
         * Match endpoints between traffic and source code endpoints, when only method is different
         * Eg:
         * Source code endpoints:
         * POST /books
         * Traffic endpoints:
         * PUT /books
         * Add PUT /books to source code endpoints
         */
        for(String trafficApiKey: trafficApiKeys) {
            if (!codeAnalysisApisMap.containsKey(trafficApiKey)) {
                for(Map.Entry<String, CodeAnalysisApi> codeAnalysisApiEntry: tempCodeAnalysisApisMap.entrySet()) {
                    CodeAnalysisApi codeAnalysisApi = codeAnalysisApiEntry.getValue();
                    String codeAnalysisApiEndpoint = codeAnalysisApi.getEndpoint();

                    String trafficApiMethod = "", trafficApiEndpoint = "";
                    try {
                        String[] trafficApiKeyParts = trafficApiKey.split(" ");
                        trafficApiMethod = trafficApiKeyParts[0];
                        trafficApiEndpoint = trafficApiKeyParts[1];
                    } catch (Exception e) {
                        continue;
                    }

                    if (codeAnalysisApiEndpoint.equals(trafficApiEndpoint)) {
                        CodeAnalysisApi newCodeAnalysisApi = new CodeAnalysisApi(
                                trafficApiMethod,
                                trafficApiEndpoint,
                                codeAnalysisApi.getLocation(), codeAnalysisApi.getRequestBody(), codeAnalysisApi.getResponseBody());

                        tempCodeAnalysisApisMap.put(newCodeAnalysisApi.generateCodeAnalysisApisMapKey(), newCodeAnalysisApi);
                        break;
                    }
                }
            }
        }

        codeAnalysisApisMap = tempCodeAnalysisApisMap;

        ObjectId codeAnalysisCollectionId = null;
        try {
            // ObjectId for new code analysis collection
            codeAnalysisCollectionId = new ObjectId();

            String projectDir = codeAnalysisRepo.getProjectName() + "/" + codeAnalysisRepo.getRepoName();  //todo:

            CodeAnalysisCollection codeAnalysisCollection = CodeAnalysisCollectionDao.instance.updateOne(
                    Filters.eq("codeAnalysisCollectionName", apiCollectionName),
                    Updates.combine(
                            Updates.setOnInsert(CodeAnalysisCollection.ID, codeAnalysisCollectionId),
                            Updates.setOnInsert(CodeAnalysisCollection.NAME, apiCollectionName),
                            Updates.set(CodeAnalysisCollection.PROJECT_DIR, projectDir),
                            Updates.setOnInsert(CodeAnalysisCollection.API_COLLECTION_ID, apiCollection.getId())
                    )
            );

            // Set code analysis collection id if existing collection is updated
            if (codeAnalysisCollection != null) {
                codeAnalysisCollectionId = codeAnalysisCollection.getId();
            }
        } catch (Exception e) {
            return;
        }

        int now = Context.now();

        if (codeAnalysisCollectionId != null) {
            List<WriteModel<CodeAnalysisApiInfo>> bulkUpdates = new ArrayList<>();
            List<WriteModel<SingleTypeInfo>> bulkUpdatesSTI = new ArrayList<>();

            for(Map.Entry<String, CodeAnalysisApi> codeAnalysisApiEntry: codeAnalysisApisMap.entrySet()) {
                CodeAnalysisApi codeAnalysisApi = codeAnalysisApiEntry.getValue();
                CodeAnalysisApiInfo.CodeAnalysisApiInfoKey codeAnalysisApiInfoKey = new CodeAnalysisApiInfo.CodeAnalysisApiInfoKey(codeAnalysisCollectionId, codeAnalysisApi.getMethod(), codeAnalysisApi.getEndpoint());

                bulkUpdates.add(
                        new UpdateOneModel<>(
                                Filters.eq(CodeAnalysisApiInfo.ID, codeAnalysisApiInfoKey),
                                Updates.combine(
                                        Updates.setOnInsert(CodeAnalysisApiInfo.ID, codeAnalysisApiInfoKey),
                                        Updates.set(CodeAnalysisApiInfo.LOCATION, codeAnalysisApi.getLocation()),
                                        Updates.setOnInsert(CodeAnalysisApiInfo.DISCOVERED_TS, now),
                                        Updates.set(CodeAnalysisApiInfo.LAST_SEEN_TS, now)
                                ),
                                new UpdateOptions().upsert(true)
                        )
                );

                String requestBody = codeAnalysisApi.getRequestBody();
                String responseBody = codeAnalysisApi.getResponseBody();

                List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
                singleTypeInfos.addAll(generateSTIsFromPayload(apiCollection.getId(), codeAnalysisApi.getEndpoint(), codeAnalysisApi.getMethod(), requestBody, -1));
                singleTypeInfos.addAll(generateSTIsFromPayload(apiCollection.getId(), codeAnalysisApi.getEndpoint(), codeAnalysisApi.getMethod(), responseBody, 200));

                Bson update = Updates.combine(Updates.max(SingleTypeInfo.LAST_SEEN, now), Updates.setOnInsert("timestamp", now));

                for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
                    bulkUpdatesSTI.add(
                            new UpdateOneModel<>(
                                    SingleTypeInfoDao.createFilters(singleTypeInfo),
                                    update,
                                    new UpdateOptions().upsert(true)
                            )
                    );
                }

            }

            if (!bulkUpdatesSTI.isEmpty()) {
                CodeAnalysisSingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesSTI);
            }

            if (bulkUpdates.size() > 0) {
                try {
                    CodeAnalysisApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);
                } catch (Exception e) {
                    return;
                }
            }
        }

        if (isLastBatch) {//Remove scheduled state from codeAnalysisRepo
            Bson sourceCodeFilter;
            if (codeAnalysisRepo.getSourceCodeType() == CodeAnalysisRepo.SourceCodeType.BITBUCKET) {
                sourceCodeFilter = Filters.or(
                        Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, codeAnalysisRepo.getSourceCodeType()),
                        Filters.exists(CodeAnalysisRepo.SOURCE_CODE_TYPE, false)

                );
            } else {
                sourceCodeFilter = Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, codeAnalysisRepo.getSourceCodeType());
            }

            Bson filters = Filters.and(
                    Filters.eq(CodeAnalysisRepo.REPO_NAME, codeAnalysisRepo.getRepoName()),
                    Filters.eq(CodeAnalysisRepo.PROJECT_NAME, codeAnalysisRepo.getProjectName()),
                    sourceCodeFilter
            );

            Bson updates = Updates.combine(
                    Updates.set(CodeAnalysisRepo.LAST_RUN, Context.now()),
                    Updates.set(CodeAnalysisRepo.CODE_ANALYSIS_RUN_STATE, CodeAnalysisRepo.CodeAnalysisRunState.COMPLETED));
            CodeAnalysisRepoDao.instance.updateOneNoUpsert(filters, updates);
        }
    }

    @Override
    public void updateRepoLastRun(CodeAnalysisRepo codeAnalysisRepo) {
        Bson sourceCodeFilter;
        if (codeAnalysisRepo == null) {
            return;
        }

        if (codeAnalysisRepo.getSourceCodeType() == CodeAnalysisRepo.SourceCodeType.BITBUCKET) {
            sourceCodeFilter = Filters.or(
                    Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, codeAnalysisRepo.getSourceCodeType()),
                    Filters.exists(CodeAnalysisRepo.SOURCE_CODE_TYPE, false)

            );
        } else {
            sourceCodeFilter = Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, codeAnalysisRepo.getSourceCodeType());
        }

        Bson filters = Filters.and(
                Filters.eq(CodeAnalysisRepo.REPO_NAME, codeAnalysisRepo.getRepoName()),
                Filters.eq(CodeAnalysisRepo.PROJECT_NAME, codeAnalysisRepo.getProjectName()),
                sourceCodeFilter
        );

        Bson updates = Updates.combine(
                Updates.set(CodeAnalysisRepo.LAST_RUN, Context.now()),
                Updates.set(CodeAnalysisRepo.CODE_ANALYSIS_RUN_STATE, CodeAnalysisRepo.CodeAnalysisRunState.COMPLETED));
        CodeAnalysisRepoDao.instance.updateOneNoUpsert(filters, updates);
    }

    public Set<MergedUrls> fetchMergedUrls() {
        return DbLayer.fetchMergedUrls();
    }
}
