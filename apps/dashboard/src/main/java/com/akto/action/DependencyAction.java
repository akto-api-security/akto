package com.akto.action;

import com.akto.dao.*;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.dependency_flow.*;
import com.akto.dto.type.URLMethods;
import com.akto.utils.Build;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.apache.logging.log4j.util.Strings;
import org.bson.conversions.Bson;

import java.util.*;

public class DependencyAction extends UserAction {

    private int apiCollectionId;
    private String url;
    private URLMethods.Method method;

    private Collection<Node> result;

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
        List<Node> nodes = DependencyFlowNodesDao.instance.findNodesForCollectionIds(apiCollectionIds, true, skip, 50);
        dependencyTableList = new ArrayList<>();

        List<ApiInfo.ApiInfoKey> apiInfoKeys = new ArrayList<>();
        for (Node node: nodes) {
            apiInfoKeys.add(new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), URLMethods.Method.fromString(node.getMethod())));
        }
        Map<ApiInfo.ApiInfoKey, List<String>> parametersMap = SingleTypeInfoDao.instance.fetchRequestParameters(apiInfoKeys);

        replaceDetails = ReplaceDetailsDao.instance.findAll(Filters.in(ReplaceDetail._API_COLLECTION_ID, apiCollectionIds));

        for (Node node: nodes) {
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), URLMethods.Method.fromString(node.getMethod()));
            List<String> params = parametersMap.get(apiInfoKey);
            BasicDBObject res = new BasicDBObject("node", node);
            res.put("params", params);
            dependencyTableList.add(res);
        }

        this.total = DependencyFlowNodesDao.instance.findTotalNodesCount(apiCollectionIds, true);
        return SUCCESS.toUpperCase();
    }

    private List<Build.RunResult> runResults;
    public String invokeDependencyTable() {
        Build build = new Build();
        runResults = build.run(Collections.singletonList(1705668952), new HashMap<>(), new HashMap<>());
        for (Build.RunResult runResult: runResults) {
            runResult.setCurrentMessage("");;
            runResult.setOriginalMessage("");
        }
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


    public List<Build.RunResult> getRunResults() {
        return runResults;
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
}
