package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.dependency_flow.*;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.RelationshipSync;
import com.akto.test_editor.execution.Build;
import com.akto.utils.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DependencyAction extends UserAction {

    private int apiCollectionId;
    private String url;
    private URLMethods.Method method;

    private Collection<Node> result;

    private static final LoggerMaker loggerMaker = new LoggerMaker(DependencyAction.class,LogDb.DASHBOARD);
    private boolean dependencyGraphExists = false;
    public String checkIfDependencyGraphAvailable() {

        long start = System.currentTimeMillis();
        Node node = DependencyFlowNodesDao.instance.findOne(
                Filters.and(
                        Filters.eq("apiCollectionId", apiCollectionId + ""),
                        Filters.eq("url", url),
                        Filters.eq("method", method),
                        Filters.ne("maxDepth", 0)
                ), Projections.include("_id")
        );
        long end = System.currentTimeMillis();
        loggerMaker.debugAndAddToDb("checkIfDependencyGraphAvailable db call took: " + (end - start) + " ms");

        dependencyGraphExists = node != null;
        return SUCCESS.toUpperCase();
    }

    @Override
    public String execute()  {
        TreeHelper treeHelper = new TreeHelper();
        treeHelper.buildTree(apiCollectionId+"", url, method.name());
        this.result = treeHelper.result.values();
        return SUCCESS.toUpperCase();
    }

    private List<Integer> apiCollectionIds;
    private List<BasicDBObject> dependencyTableList;
    private List<ReplaceDetail> replaceDetails;
    private int total;
    private int skip;

    public String buildDependencyTable() {
        List<Node> nodes = DependencyFlowNodesDao.instance.findNodesForCollectionIds(apiCollectionIds,false, skip, 50);
        dependencyTableList = new ArrayList<>();

        List<ApiInfo.ApiInfoKey> apiInfoKeys = new ArrayList<>();
        for (Node node: nodes) {
            apiInfoKeys.add(new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), URLMethods.Method.fromString(node.getMethod())));
        }
        Map<ApiInfo.ApiInfoKey, List<String>> parametersMap = SingleTypeInfoDao.instance.fetchRequestParameters(apiInfoKeys);
        Map<ApiInfo.ApiInfoKey, List<String>> sourceCodeParametersMap = CodeAnalysisSingleTypeInfoDao.instance.fetchRequestParameters(apiInfoKeys);

        // Add parameters from source code, if any.
        if (sourceCodeParametersMap != null && !sourceCodeParametersMap.isEmpty()) {
            parametersMap.putAll(sourceCodeParametersMap);
        }

        replaceDetails = ReplaceDetailsDao.instance.findAll(Filters.in(ReplaceDetail._API_COLLECTION_ID, apiCollectionIds));

        for (Node node: nodes) {
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), URLMethods.Method.fromString(node.getMethod()));
            List<String> params = parametersMap.getOrDefault(apiInfoKey, new ArrayList<>());
            BasicDBObject res = new BasicDBObject("node", node);
            res.put("params", params);
            dependencyTableList.add(res);
        }

        this.total = DependencyFlowNodesDao.instance.findTotalNodesCount(apiCollectionIds, true);
        return SUCCESS.toUpperCase();
    }

    private int newCollectionId;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private boolean sourceCodeApis;

    public String invokeDependencyTable() {

        if(apiCollectionIds== null || apiCollectionIds.isEmpty()){
            addActionError("No API collections to invoke dependency graph");
            return ERROR.toUpperCase();
        }

        if (sourceCodeApis && apiCollectionIds.size() > 1) {
            addActionError("Please use a single API collection ID with source code APIs");
            return ERROR.toUpperCase();
        }

        if(!sourceCodeApis){
            ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
            apiCollectionsAction.setCollectionName("temp " + Context.now());
            apiCollectionsAction.createCollection();
            List<ApiCollection> apiCollections = apiCollectionsAction.getApiCollections();;

            if (apiCollections == null || apiCollections.size() == 0) {
                addActionError("Couldn't create collection");
                return ERROR.toUpperCase();
            }
            newCollectionId = apiCollections.get(0).getId();
        } else {
            // Insert in original collection for source code APIs.
            newCollectionId = apiCollectionIds.get(0);
        }

        int accountId = Context.accountId.get();

        executorService.schedule(new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                Build build = new Build();
                List<ModifyHostDetail> modifyHostDetails = ModifyHostDetailsDao.instance.findAll(Filters.empty());
                List<ReplaceDetail> replaceDetailsFromDb = ReplaceDetailsDao.instance.findAll(Filters.in(ReplaceDetail._API_COLLECTION_ID, apiCollectionIds));
                Map<Integer, ReplaceDetail> replaceDetailMap = new HashMap<>();
                for (ReplaceDetail replaceDetail: replaceDetailsFromDb) {
                    replaceDetailMap.put(replaceDetail.hashCode(), replaceDetail);
                }
                List<Build.RunResult> runResults = build.run(apiCollectionIds, modifyHostDetails, replaceDetailMap, sourceCodeApis);
                List<String> messages = new ArrayList<>();

                for (Build.RunResult runResult: runResults) {
                    String currentMessage = runResult.getCurrentMessage();
                    messages.add(currentMessage);
                }

                if(messages.isEmpty()){
                    loggerMaker.debugAndAddToDb("No messages found for invokeDependencyTable");
                    return;
                }

                try {
                    Utils.pushDataToKafka(newCollectionId, "", messages, new ArrayList<>(), true, true);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while sending data to kafka in invoke dependency graph function");
                }

            }
        }, 0, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    List<KVPair> kvPairs = new ArrayList<>();

    public String saveReplaceDetails() {
        if (url == null || url.isEmpty() || method == null ) {
            addActionError("Invalid url or method");
            return ERROR.toUpperCase();
        }

        ReplaceDetailsDao.instance.updateOne(
                Filters.and(
                        Filters.eq(ReplaceDetail._API_COLLECTION_ID, apiCollectionId),
                        Filters.eq(ReplaceDetail._URL, url),
                        Filters.eq(ReplaceDetail._METHOD, method.name())
                ),
                Updates.set(ReplaceDetail._KV_PAIRS, kvPairs)
        );

        return SUCCESS.toUpperCase();
    }

    private List<ModifyHostDetail> modifyHostDetails = new ArrayList<>();
    public String fetchGlobalVars() {
        List<ApiCollection> metaForIds = ApiCollectionsDao.instance.getMetaForIds(apiCollectionIds);
        List<Integer> nonTrafficCollectionIds = new ArrayList<>();

        Set<String> hosts = new HashSet<>();
        for (ApiCollection apiCollection: metaForIds) {
            String hostName = apiCollection.getHostName();
            if (hostName == null) {
                nonTrafficCollectionIds.add(apiCollection.getId());
            } else {
                hosts.add(hostName);
            }
        }

        if (!nonTrafficCollectionIds.isEmpty()) {
            Set<String> hostsFromNonTrafficCollections = SingleTypeInfoDao.instance.fetchHosts(nonTrafficCollectionIds);
            hosts.addAll(hostsFromNonTrafficCollections);
        }

        modifyHostDetails = ModifyHostDetailsDao.instance.findAll(Filters.empty());
        Set<String> modifiedHosts = new HashSet<>();
        for (ModifyHostDetail modifiedHostDetail: modifyHostDetails) {
            modifiedHosts.add(modifiedHostDetail.getCurrentHost());
        }

        for (String host: hosts) {
            if (!modifiedHosts.contains(host)) modifyHostDetails.add(new ModifyHostDetail(host, null));
        }

        return SUCCESS.toUpperCase();
    }

    public String saveGlobalVars() {
        List<WriteModel<ModifyHostDetail>> bulkUpdates = new ArrayList<>();
        for (ModifyHostDetail modifyHostDetail: modifyHostDetails) {
            Bson filter = Filters.eq(ModifyHostDetail.CURRENT_HOST, modifyHostDetail.getCurrentHost());
            String newHost = modifyHostDetail.getNewHost();

            if (newHost == null || newHost.trim().isEmpty()) {
                bulkUpdates.add(new DeleteOneModel<>(filter));
            } else {
                Bson update = Updates.set(ModifyHostDetail.NEW_HOST, newHost);
                bulkUpdates.add(new UpdateOneModel<ModifyHostDetail>(filter, update, new UpdateOptions().upsert(true)));
            }
        }

        if (!bulkUpdates.isEmpty()) ModifyHostDetailsDao.instance.bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));
        return SUCCESS.toUpperCase();
    }


    private Set<String> params;
    private final Map<String, Set<String>> paramToValuesMap = new HashMap<>();
    public String fetchValuesForParameters() {
        Bson filter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.url", url),
                Filters.eq("_id.method", method.name())
        );
        SampleData sampleData = SampleDataDao.instance.findOne(filter);

        if (sampleData == null) return SUCCESS.toUpperCase();

        List<String> samples = sampleData.getSamples();
        if (samples.isEmpty()) return SUCCESS.toUpperCase();

        String sample = samples.get(0);

        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
        originalHttpRequest.buildFromSampleMessage(sample);
        Map<String, Set<String>> valuesMap = RelationshipSync.extractAllValuesFromPayload(originalHttpRequest.getJsonRequestBody());

        for (String key: valuesMap.keySet()) {
            if (params.contains(key)) {
                paramToValuesMap.put(key, valuesMap.getOrDefault(key, new HashSet<>()));
            }
        }

        return SUCCESS.toUpperCase();
    }

    public Collection<Node> getResult() {
        return result;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setMethod(URLMethods.Method method) {
        this.method = method;
    }

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }

    public List<BasicDBObject> getDependencyTableList() {
        return dependencyTableList;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public int getTotal() {
        return total;
    }


    public void setKvPairs(List<KVPair> kvPairs) {
        this.kvPairs = kvPairs;
    }

    public List<ReplaceDetail> getReplaceDetails() {
        return replaceDetails;
    }

    public List<ModifyHostDetail> getModifyHostDetails() {
        return modifyHostDetails;
    }

    public void setModifyHostDetails(List<ModifyHostDetail> modifyHostDetails) {
        this.modifyHostDetails = modifyHostDetails;
    }


    public void setParams(Set<String> params) {
        this.params = params;
    }

    public Map<String, Set<String>> getParamToValuesMap() {
        return paramToValuesMap;
    }

    public int getNewCollectionId() {
        return newCollectionId;
    }

    public boolean isDependencyGraphExists() {
        return dependencyGraphExists;
    }

    public boolean getDependencyGraphExists() {
        return dependencyGraphExists;
    }

    public boolean getSourceCodeApis() {
        return sourceCodeApis;
    }

    public void setSourceCodeApis(boolean sourceCodeApis) {
        this.sourceCodeApis = sourceCodeApis;
    }

}
