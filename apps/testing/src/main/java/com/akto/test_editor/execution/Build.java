package com.akto.test_editor.execution;

import com.akto.dao.AccountsDao;
import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.Account;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.dependency_flow.*;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.akto.testing.Utils;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.akto.util.grpc.ParameterTransformer;
import com.akto.util.modifier.SetValueModifier;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import joptsimple.internal.Strings;
import org.bson.conversions.Bson;
import java.net.URI;
import java.util.*;

import static com.akto.util.HttpRequestResponseUtils.FORM_URL_ENCODED_CONTENT_TYPE;
import static com.akto.util.HttpRequestResponseUtils.extractValuesFromPayload;
import static com.akto.runtime.utils.Utils.parseCookie;

public class Build {

    private Map<Integer, ReverseNode> parentToChildMap = new HashMap<>();

    private static final LoggerMaker loggerMaker = new LoggerMaker(Build.class, LogDb.DASHBOARD);

    public static void buildParentToChildMap(Collection<Node> nodes, Map<Integer, ReverseNode> parentToChildMap) {
        for (Node node: nodes) {
            if (node.getConnections() == null) continue;
            for (Connection connection: node.getConnections().values()) {
                String requestParam = connection.getParam();
                if (connection.getEdges() == null) continue;
                for (Edge edge: connection.getEdges()) {
                    String responseParam = edge.getParam();
                    ReverseNode reverseNode = new ReverseNode(
                            edge.getApiCollectionId(), edge.getUrl(), edge.getMethod(), new HashMap<>()
                    );

                    int key = reverseNode.hashCode();
                    reverseNode = parentToChildMap.getOrDefault(key, reverseNode);

                    Map<String, ReverseConnection> reverseConnections = reverseNode.getReverseConnections();
                    ReverseConnection reverseConnection = reverseConnections.getOrDefault(responseParam, new ReverseConnection(responseParam, new ArrayList<>()));
                    reverseConnection.getReverseEdges().add(new ReverseEdge(node.getApiCollectionId(), node.getUrl(), node.getMethod(), requestParam, edge.getCount(), connection.getIsUrlParam(), connection.getIsHeader()));

                    reverseConnections.put(responseParam, reverseConnection);
                    parentToChildMap.put(key, reverseNode);
                }
            }
        }

    }

    public static Map<Integer, List<SampleData>> buildLevelsToSampleDataMap(List<Node> nodes) {

        // divide them into levels
        Map<Integer,List<SampleData>> levelsToSampleDataMap = new HashMap<>();
        for (Node node: nodes) {
            int maxDepth = node.getMaxDepth();
            List<SampleData> list = levelsToSampleDataMap.getOrDefault(maxDepth, new ArrayList<>());
            int apiCollectionId = Integer.parseInt(node.getApiCollectionId());
            URLMethods.Method method = URLMethods.Method.valueOf(node.getMethod());
            list.add(new SampleData(new Key(apiCollectionId, node.getUrl(), method, 0,0,0), new ArrayList<>()));
            levelsToSampleDataMap.put(maxDepth, list);
        }

        return levelsToSampleDataMap;
    }

    public static class RunResult {

        private ApiInfo.ApiInfoKey apiInfoKey;
        private String currentMessage;
        private String originalMessage;
        private boolean success;

        public RunResult(ApiInfo.ApiInfoKey apiInfoKey, String currentMessage, String originalMessage, boolean success) {
            this.apiInfoKey = apiInfoKey;
            this.currentMessage = currentMessage;
            this.originalMessage = originalMessage;
            this.success = success;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RunResult runResult = (RunResult) o;
            return apiInfoKey.equals(runResult.apiInfoKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(apiInfoKey);
        }

        @Override
        public String toString() {
            return "RunResult{" +
                    "apiInfoKey=" + apiInfoKey +
                    ", currentMessage='" + currentMessage + '\'' +
                    ", originalMessage='" + originalMessage + '\'' +
                    ", success=" + success +
                    '}';
        }

        public ApiInfo.ApiInfoKey getApiInfoKey() {
            return apiInfoKey;
        }

        public void setApiInfoKey(ApiInfo.ApiInfoKey apiInfoKey) {
            this.apiInfoKey = apiInfoKey;
        }

        public String getCurrentMessage() {
            return currentMessage;
        }

        public void setCurrentMessage(String currentMessage) {
            this.currentMessage = currentMessage;
        }

        public String getOriginalMessage() {
            return originalMessage;
        }

        public void setOriginalMessage(String originalMessage) {
            this.originalMessage = originalMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean getIsSuccess() {
            return success;
        }

        public void setIsSuccess(boolean success) {
            this.success = success;
        }
    }

    Set<ApiInfo.ApiInfoKey> apisReplayedSet = new HashSet<>();
    public static List<RunResult> runPerLevel(List<SampleData> sdList, Map<String, ModifyHostDetail> modifyHostDetailMap, Map<Integer, ReplaceDetail> replaceDetailsMap, Map<Integer, ReverseNode> parentToChildMap, Set<ApiInfo.ApiInfoKey> apisReplayedSet, boolean sourceCodeApis) {
        List<RunResult> runResults = new ArrayList<>();
        for (SampleData sampleData: sdList) {
            Key id = sampleData.getId();
            try {
                List<String> samples = sampleData.getSamples();
                ReplaceDetail replaceDetail = replaceDetailsMap.get(Objects.hash(id.getApiCollectionId(), id.getUrl(), id.getMethod().name()));
                boolean usedReplaceDetail = false;
                if (sourceCodeApis) {
                    usedReplaceDetail = true;
                    samples.addAll(ParameterTransformer.createSampleUsingReplaceDetails(id, replaceDetail.getKvPairs()));
                }

                if (samples.isEmpty()) continue;

                // Use latest sample.
                String sample = samples.get(samples.size()-1);

                OriginalHttpRequest request = new OriginalHttpRequest();
                request.buildFromSampleMessage(sample);
                String newHost = findNewHost(request, modifyHostDetailMap);

                OriginalHttpResponse originalHttpResponse = new OriginalHttpResponse();
                originalHttpResponse.buildFromSampleMessage(sample);

                // do modifications
                if (!usedReplaceDetail) {
                    modifyRequest(request, replaceDetail);
                }

                OriginalHttpRequest originalRequest = request.copy();

                TestingRunConfig testingRunConfig = new TestingRunConfig(0, new HashMap<>(), new ArrayList<>(), null,newHost, null);
                // in case it is being executed on a mini testing module, we need to set the url
                try {
                    if (newHost != null) {
                        String url = ApiExecutor.prepareUrl(request, testingRunConfig);
                        request.setUrl(url);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while preparing url for " + id.getUrl());
                }

                OriginalHttpResponse response = null;
                try {
                    Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, Context.accountId.get()));
                    if (account != null && account.getHybridTestingEnabled()) {
                        ModuleInfo moduleInfo = ModuleInfoDao.instance.getMCollection()
                                .find(Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MINI_TESTING))
                                .sort(Sorts.descending(ModuleInfo.LAST_HEARTBEAT_RECEIVED)).limit(1).first();
                        if (moduleInfo != null) {
                            String version = moduleInfo.getCurrentVersion().split(" - ")[0];
                            if (Utils.compareVersions("1.44.9", version) <= 0) { // latest version
                                response = Utils.runRequestOnHybridTesting(request);
                            }
                        }
                    }
                    if (response == null) {
                        response = ApiExecutor.sendRequest(request,false, testingRunConfig, false, new ArrayList<>());
                    }

                    apisReplayedSet.add(new ApiInfo.ApiInfoKey(id.getApiCollectionId(), id.getUrl(), id.getMethod()));
                    request.getHeaders().remove(Constants.AKTO_IGNORE_FLAG);
                    ReverseNode parentToChildNode = parentToChildMap.get(Objects.hash(id.getApiCollectionId()+"", id.getUrl(), id.getMethod().name()));
                    fillReplaceDetailsMap(parentToChildNode, response, replaceDetailsMap);

                    /*
                     * We do not want payload transformations being done by ApiExecutor,
                     * primarily the test-pre script, to be shown,
                     * but we needed them to hit the request.
                     */
                    if (sourceCodeApis) {
                        request.setBody(originalRequest.getBody());
                        request.setUrl(originalRequest.getUrl());
                    }

                    RawApi rawApi = new RawApi(request, response, "");
                    rawApi.fillOriginalMessage(Context.accountId.get(), Context.now(), "HTTP/1.1", "HAR");
                    RunResult runResult = new RunResult(
                            new ApiInfo.ApiInfoKey(id.getApiCollectionId(), id.getUrl(), id.getMethod()),
                            rawApi.getOriginalMessage(),
                            sample,
                            rawApi.getResponse().getStatusCode() == originalHttpResponse.getStatusCode()
                    );
                    runResults.add(runResult);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "error while sending request in invoke dependency graph: " + id.getUrl());
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "error while running runPerLevel for " + id.getUrl());
            }

        }

        return runResults;
    }

    public static String findNewHost(OriginalHttpRequest request, Map<String, ModifyHostDetail> modifyHostDetailMap) {
        try {
            String url = request.getFullUrlIncludingDomain();
            URI uri = new URI(url);
            String currentHost = uri.getHost();
            ModifyHostDetail modifyHostDetail = modifyHostDetailMap.get(currentHost);
            if (modifyHostDetail == null) {
                if (uri.getPort() != -1) currentHost += ":" + uri.getPort();
                modifyHostDetail = modifyHostDetailMap.get(currentHost);
            }

            if (modifyHostDetail == null) return null;

            String newHost = modifyHostDetail.getNewHost();
            if (newHost == null) return null;
            if (newHost.startsWith("http")) {
                return newHost;
            } else {
                return  uri.getScheme() + "://" + newHost;
            }
        } catch (Exception ignored) {
        }

        try {
            String url = request.getUrl();
            URI uri = new URI(url);
            String currentHost = uri.getHost();
            ModifyHostDetail modifyHostDetail = modifyHostDetailMap.get(currentHost);
                        String newHost = modifyHostDetail.getNewHost();
            if (newHost == null) return null;
            if (newHost.startsWith("http")) {
                return newHost;
            } else {
                return  uri.getScheme() + "://" + newHost;
            }
        } catch (Exception ignored) {

        }

        return null;
    }


    public List<RunResult> run(List<Integer> apiCollectionsIds, List<ModifyHostDetail> modifyHostDetails, Map<Integer, ReplaceDetail> replaceDetailsMap, boolean sourceCodeApis) {
        if (replaceDetailsMap == null) replaceDetailsMap = new HashMap<>();
        if (modifyHostDetails == null) modifyHostDetails = new ArrayList<>();

        Map<String, ModifyHostDetail> modifyHostDetailMap = new HashMap<>();
        for (ModifyHostDetail modifyHostDetail: modifyHostDetails) {
            modifyHostDetailMap.put(modifyHostDetail.getCurrentHost(), modifyHostDetail);
        }

        List<Node> nodes = DependencyFlowNodesDao.instance.findNodesForCollectionIds(apiCollectionsIds,false,0, 10_000);
        buildParentToChildMap(nodes, parentToChildMap);
        Map<Integer, List<SampleData>> levelsToSampleDataMap = buildLevelsToSampleDataMap(nodes);

        List<RunResult> runResults = new ArrayList<>();
        // loop over levels and make requests
        for (int level: levelsToSampleDataMap.keySet()) {
            List<SampleData> sdList =levelsToSampleDataMap.get(level);
            List<SampleData> sdListDb = fillSdList(sdList);
            if (sdListDb != null && !sdListDb.isEmpty()) {
                sdList = sdListDb;
            }
            /*
             * For source code APIs, the sample data is filled in the next step.
             */
            if (sdList.isEmpty() && !sourceCodeApis){
                continue;
            }

            loggerMaker.debugAndAddToDb("Running level: " + level, LoggerMaker.LogDb.DASHBOARD);
            try {
                List<RunResult> runResultsPerLevel = runPerLevel(sdList, modifyHostDetailMap, replaceDetailsMap, parentToChildMap, apisReplayedSet, sourceCodeApis);
                runResults.addAll(runResultsPerLevel);
                loggerMaker.debugAndAddToDb("Finished running level " + level, LoggerMaker.LogDb.DASHBOARD);
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "Error while running for level " + level);
            }
        }

        loggerMaker.debugAndAddToDb("Running independent APIs", LoggerMaker.LogDb.DASHBOARD);
        int skip = 0;
        int limit = 1000;
        while (true) {
            List<SampleData> all = SampleDataDao.instance.findAll(Filters.in("_id.apiCollectionId", apiCollectionsIds), skip,limit, null);
            if (all.isEmpty()) break;
            List<SampleData> filtered = new ArrayList<>();
            for (SampleData sampleData: all) {
                Key key = sampleData.getId();
                if (apisReplayedSet.contains(new ApiInfo.ApiInfoKey(key.getApiCollectionId(), key.getUrl(), key.getMethod()))) continue;
                filtered.add(sampleData);
            }
            List<RunResult> runResultsAll = runPerLevel(filtered, modifyHostDetailMap, replaceDetailsMap, parentToChildMap, apisReplayedSet, sourceCodeApis);
            runResults.addAll(runResultsAll);
            skip += limit;
            if (all.size() < limit) break;
        }
        loggerMaker.debugAndAddToDb("Finished running independent APIs", LoggerMaker.LogDb.DASHBOARD);

        return runResults;
    }

    public static void modifyRequest(OriginalHttpRequest request, ReplaceDetail replaceDetail) {
        RawApi rawApi = new RawApi(request, null, null);

        if (replaceDetail != null) {
            List<KVPair> kvPairs = replaceDetail.getKvPairs();
            if (kvPairs != null && !kvPairs.isEmpty())  {
                for (KVPair kvPair: kvPairs) {
                    if (kvPair.isHeader()) {
                        String value = kvPair.getValue()+"";
                        String headerValue = rawApi.getRequest().findHeaderValueIncludingInCookie(kvPair.getKey());
                        if (headerValue == null) continue;
                        String[] headerValueSplit = headerValue.split(" ");
                        if (headerValueSplit.length == 2) {
                            String prefix = headerValueSplit[0];
                            if (Arrays.asList("bearer", "basic").contains(prefix.toLowerCase())) {
                                value = prefix + " " + value;
                            }
                        }
                        Operations.modifyHeader(rawApi, kvPair.getKey(), value);
                    } else if (kvPair.isUrlParam()) {
                        String url = request.getUrl();
                        String[] urlSplit = url.split("/");
                        int position = Integer.parseInt(kvPair.getKey());
                        urlSplit[position] = kvPair.getValue()+"";
                        String newUrl = Strings.join(urlSplit, "/");
                        request.setUrl(newUrl);
                    } else {
                        Map<String, Object> store = new HashMap<>();
                        store.put(kvPair.getKey(), kvPair.getValue());
                        SetValueModifier setValueModifier = new SetValueModifier(store);

                        Set<String> values = new HashSet<>();
                        values.add(kvPair.getKey());
                        String modifiedBody = JSONUtils.modify(rawApi.getRequest().getJsonRequestBody(), values, setValueModifier);
                        String contentType = rawApi.getRequest().findContentType();
                        if (contentType != null && contentType.equals(FORM_URL_ENCODED_CONTENT_TYPE)) {
                            modifiedBody = HttpRequestResponseUtils.jsonToFormUrlEncoded(modifiedBody);
                        }
                        rawApi.getRequest().setBody(modifiedBody);

                        Operations.modifyQueryParam(rawApi, kvPair.getKey(), kvPair.getValue());
                    }
                }
            }
        }

    }

    public static List<SampleData> fillSdList(List<SampleData> sdList) {
        if (sdList == null || sdList.isEmpty()) return new ArrayList<>();

        List<Bson> filters = new ArrayList<>();
        for (SampleData sampleData: sdList) {
            // todo: batch for bigger lists
            Key id = sampleData.getId();
            filters.add(Filters.and(
                    Filters.eq("_id.apiCollectionId", id.getApiCollectionId()),
                    Filters.eq("_id.url", id.getUrl()),
                    Filters.eq("_id.method", id.getMethod().name())
            ));
        }
        return SampleDataDao.instance.findAll(Filters.or(filters));
    }


    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();
    public static void fillReplaceDetailsMap(ReverseNode reverseNode, OriginalHttpResponse response, Map<Integer, ReplaceDetail> replaceDetailsMap) {
        if (reverseNode == null) return;

        Map<Integer, ReplaceDetail> deltaReplaceDetailsMap = new HashMap<>();

        Map<String, Set<Object>> valuesMap = getValuesMap(response);

        Map<String,ReverseConnection> connections = reverseNode.getReverseConnections();
        for (ReverseConnection reverseConnection: connections.values()) {
            String param = reverseConnection.getParam();
            Set<Object> values = valuesMap.get(param);
            Object value = values != null && values.size() > 0 ? values.toArray()[0] : null; // todo:
            if (value == null) continue;

            for (ReverseEdge reverseEdge: reverseConnection.getReverseEdges()) {
                int apiCollectionId = Integer.parseInt(reverseEdge.getApiCollectionId());
                Integer id = Objects.hash(apiCollectionId, reverseEdge.getUrl(), reverseEdge.getMethod());

                ReplaceDetail deltaReplaceDetail = deltaReplaceDetailsMap.get(id);
                if (deltaReplaceDetail == null) {
                    deltaReplaceDetail = new ReplaceDetail(apiCollectionId, reverseEdge.getUrl(), reverseEdge.getMethod(), new ArrayList<>());
                    deltaReplaceDetailsMap.put(id, deltaReplaceDetail);
                }

                KVPair.KVType type = value instanceof Integer ? KVPair.KVType.INTEGER : KVPair.KVType.STRING;
                KVPair kvPair = new KVPair(reverseEdge.getParam(), value.toString(), reverseEdge.getIsHeader(), reverseEdge.isUrlParam(), type);
                deltaReplaceDetail.addIfNotExist(kvPair);
            }
        }

        for (Integer key: deltaReplaceDetailsMap.keySet()) {
            ReplaceDetail replaceDetail = replaceDetailsMap.get(key);
            ReplaceDetail deltaReplaceDetail = deltaReplaceDetailsMap.get(key);
            if (replaceDetail == null) {
                replaceDetail = deltaReplaceDetail;
            } else {
                replaceDetail.addIfNotExist(deltaReplaceDetail.getKvPairs());
            }

            replaceDetailsMap.put(key, replaceDetail);
        }

    }

    public static Map<String, Set<Object>> getValuesMap(OriginalHttpResponse response) {
        String respPayload = response.getBody();
        Map<String, Set<Object>> valuesMap = extractValuesFromPayload(respPayload);

        Map<String, List<String>> responseHeaders = response.getHeaders();
        for (String headerKey: responseHeaders.keySet()) {
            List<String> values = responseHeaders.get(headerKey);
            if (values == null) continue;

            if (headerKey.equalsIgnoreCase("set-cookie")) {
                Map<String, String> cookieMap = parseCookie(values);
                for (String cookieKey : cookieMap.keySet()) {
                    String cookieVal = cookieMap.get(cookieKey);
                    valuesMap.put(cookieKey, new HashSet<>(Collections.singletonList(cookieVal)));
                }
            } else {
                valuesMap.put(headerKey, new HashSet<>(values));
            }

        }

        return valuesMap;
    }
}
