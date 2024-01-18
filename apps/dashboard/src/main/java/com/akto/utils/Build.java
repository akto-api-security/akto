package com.akto.utils;

import com.akto.DaoInit;
import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dao.DependencyNodeDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.dependency_flow.*;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.runtime.RelationshipSync;
import com.akto.test_editor.execution.Operations;
import com.akto.test_editor.filter.FilterAction;
import com.akto.testing.ApiExecutor;
import com.akto.util.JSONUtils;
import com.akto.util.modifier.SetValueModifier;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.checkerframework.checker.units.qual.K;

import java.net.URI;
import java.util.*;

public class Build {

    private Map<Integer, Node>  parentToChildMap = new HashMap<>();

    private static final LoggerMaker loggerMaker = new LoggerMaker(Build.class);

    private void buildParentToChildMap(List<Integer> apiCollectionIds) {
        List<DependencyNode> dependencyNodeList = DependencyNodeDao.instance.findNodesForCollectionIds(apiCollectionIds);
        DependencyFlow dependencyFlow = new DependencyFlow();
        for (DependencyNode dependencyNode: dependencyNodeList) dependencyFlow.fillNodes(dependencyNode);
        parentToChildMap = dependencyFlow.initialNodes;
    }

    private Map<Integer, List<SampleData>> buildLevelsToSampleDataMap(List<Integer> apiCollectionIds) {
        // get dependencyFlow
        List<Node> nodes = DependencyFlowNodesDao.instance.findNodesForCollectionIds(apiCollectionIds,false,0, 10_000);

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

    public void runPerLevel(List<SampleData> sdList, Map<String, String> hostRelations, Map<Integer, ReplaceDetail> replaceDetailsMap) {
        List<String> messages = new ArrayList<>();
        for (SampleData sampleData: sdList) {
            Key id = sampleData.getId();
            List<String> samples = sampleData.getSamples();
            if (samples.isEmpty()) continue;;

            for (String sample: samples) {
                OriginalHttpRequest request = new OriginalHttpRequest();
                request.buildFromSampleMessage(sample);
                String newHost = findNewHost(request, hostRelations);

                // do modifications
                ReplaceDetail replaceDetail = replaceDetailsMap.get(Objects.hash(id.getApiCollectionId()+"", id.getUrl(), id.getMethod().name()));
                modifyRequest(request, replaceDetail);

                TestingRunConfig testingRunConfig = new TestingRunConfig(0, new HashMap<>(), new ArrayList<>(), null,newHost);

                OriginalHttpResponse response = null;
                try {
                    response = ApiExecutor.sendRequest(request, true, testingRunConfig);
                    Node parentToChildNode = parentToChildMap.get(Objects.hash(id.getApiCollectionId()+"", id.getUrl(), id.getMethod().name()));
                    boolean foundValues = fillReplaceDetailsMap(parentToChildNode, response, replaceDetailsMap);
                    if (foundValues) {
                        RawApi rawApi = new RawApi(request, response, "");
                        rawApi.fillOriginalMessage(Context.accountId.get(), Context.now(), "", "MIRRORING");
                        messages.add(rawApi.getOriginalMessage());
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    loggerMaker.errorAndAddToDb(e, "error while sending request", LoggerMaker.LogDb.DASHBOARD);
                    e.printStackTrace();
                }
            }
        }

        try {
            Utils.pushDataToKafka(0, "", messages, new ArrayList<>(), true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public String findNewHost(OriginalHttpRequest request, Map<String, String> hostRelations) {
        try {
            String url = request.getFullUrlIncludingDomain();
            URI uri = new URI(url);
            String currentHost = uri.getHost();
            String newHost = hostRelations.get(currentHost);
            if (newHost == null) return null;
            return  uri.getScheme() + "://" + newHost;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public void run(List<Integer> apiCollectionsIds, Map<String, String> hostRelations, Map<Integer, ReplaceDetail> replaceDetailsMap) {
        if (replaceDetailsMap == null) replaceDetailsMap = new HashMap<>();
        if (hostRelations == null) hostRelations = new HashMap<>();

        buildParentToChildMap(apiCollectionsIds);
        Map<Integer, List<SampleData>> levelsToSampleDataMap = buildLevelsToSampleDataMap(apiCollectionsIds);

        // loop over levels and make requests
        for (int level: levelsToSampleDataMap.keySet()) {
            List<SampleData> sdList =levelsToSampleDataMap.get(level);
            sdList = fillSdList(sdList);
            if (sdList.isEmpty()) continue;

            loggerMaker.infoAndAddToDb("Running level: " + level, LoggerMaker.LogDb.DASHBOARD);
            try {
                runPerLevel(sdList, hostRelations, replaceDetailsMap);
                loggerMaker.infoAndAddToDb("Finished running level " + level, LoggerMaker.LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while running for level " + level , LoggerMaker.LogDb.DASHBOARD);
            }
        }
    }

    public void modifyRequest(OriginalHttpRequest request, ReplaceDetail replaceDetail) {
        RawApi rawApi = new RawApi(request, null, null);

        if (replaceDetail != null) {
            List<KVPair> kvPairs = replaceDetail.getKvPairs();
            if (kvPairs != null && !kvPairs.isEmpty())  {
                for (KVPair kvPair: kvPairs) {
                    if (kvPair.isHeader()) {
                        Operations.modifyHeader(rawApi, kvPair.getKey(), kvPair.getValue()+"");
                    } else {
                        Map<String, Object> store = new HashMap<>();
                        store.put(kvPair.getKey(), kvPair.getValue());
                        SetValueModifier setValueModifier = new SetValueModifier(store);

                        Set<String> values = new HashSet<>();
                        values.add(kvPair.getKey());
                        String modifiedBody = JSONUtils.modify(rawApi.getRequest().getJsonRequestBody(), values, setValueModifier);
                        rawApi.getRequest().setBody(modifiedBody);

                        Operations.modifyQueryParam(rawApi, kvPair.getKey(), kvPair.getValue());
                    }
                }
            }
        }

    }

    public List<SampleData> fillSdList(List<SampleData> sdList) {
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
    public boolean fillReplaceDetailsMap(Node node, OriginalHttpResponse response, Map<Integer, ReplaceDetail> replaceDetailsMap) {
        if (node == null) return true;

        Map<Integer, ReplaceDetail> deltaReplaceDetailsMap = new HashMap<>();

        String respPayload = response.getBody();
        if (respPayload == null || respPayload.isEmpty()) respPayload = "{}";
        if (respPayload.startsWith("[")) respPayload = "{\"json\": "+respPayload+"}";

        Map<String,Set<String>> valuesMap = new HashMap<>();
        try {
            JsonParser jp = factory.createParser(respPayload);
            JsonNode jsonNode = mapper.readTree(jp);
            RelationshipSync.extractAllValuesFromPayload(jsonNode, new ArrayList<>(), valuesMap);
        } catch (Exception e) {
            e.printStackTrace();
        }

        boolean found = true;

        Map<String, Connection> connections = node.getConnections();
        for (Connection connection: connections.values()) {
            String param = connection.getParam();
            Set<String> values = valuesMap.get(param);
            Object value = values != null && values.size() > 0 ? values.toArray()[0] : null; // todo:

            for (Edge edge: connection.getEdges()) {
                Integer id = Objects.hash(edge.getApiCollectionId(), edge.getUrl(), edge.getMethod());
                ReplaceDetail replaceDetail = replaceDetailsMap.get(id);
                found = value != null || replaceDetail != null;
                if (!found) continue;

                ReplaceDetail deltaReplaceDetail = deltaReplaceDetailsMap.get(id);
                if (deltaReplaceDetail == null) {
                    deltaReplaceDetail = new ReplaceDetail(Integer.parseInt(edge.getApiCollectionId()), edge.getUrl(), edge.getMethod(), new ArrayList<>());
                    deltaReplaceDetailsMap.put(id, deltaReplaceDetail);
                }
                KVPair kvPair = new KVPair(edge.getParam(), value, false, false);
                deltaReplaceDetail.addIfNotExist(kvPair);
            }
        }

        if (!found) return false;

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

        return true;
    }

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);

        Build build = new Build();
        build.run(Collections.singletonList(1705581931), new HashMap<>(), new HashMap<>());
    }

}
