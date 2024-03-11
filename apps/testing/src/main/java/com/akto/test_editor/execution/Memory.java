package com.akto.test_editor.execution;

import com.akto.DaoInit;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.dependency_flow.*;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.*;

import static com.akto.test_editor.execution.Build.*;


public class Memory {

    Map<Integer, RawApi> resultMap = new HashMap<>();
    private final Map<Integer, ReverseNode>  parentToChildMap = new HashMap<>();
    private final Map<Integer, Node> nodesMap = new HashMap<>();

    Map<Integer, SampleData> sampleDataMap = new HashMap<>();

    public static Memory memory = new Memory(
            Arrays.asList(
                    new ApiInfo.ApiInfoKey(1710156663, "https://juiceshop.akto.io/rest/products/reviews", URLMethods.Method.PATCH),
                    new ApiInfo.ApiInfoKey(1710156663, "https://juiceshop.akto.io/rest/user/login", URLMethods.Method.POST)
            ), new HashMap<>()
    );

    private Map<Integer, ReplaceDetail> replaceDetailsMap = new HashMap<>();

    public static void main(String[] args) throws Exception {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);

//        https://juiceshop.akto.io/rest/user/login
        List<ApiInfo.ApiInfoKey> apiInfoKeys = new ArrayList<>();
        apiInfoKeys.add(new ApiInfo.ApiInfoKey(1710156663, "https://juiceshop.akto.io/rest/products/reviews", URLMethods.Method.PATCH));
        apiInfoKeys.add(new ApiInfo.ApiInfoKey(1710156663, "https://juiceshop.akto.io/rest/user/login", URLMethods.Method.POST));

        Memory memory = new Memory(apiInfoKeys, new HashMap<>());

        OriginalHttpRequest req1 = memory.run(1710156663, "https://juiceshop.akto.io/rest/user/login", URLMethods.Method.POST.name());
        System.out.println("new");
        OriginalHttpResponse resp1 = ApiExecutor.sendRequest(req1, true, null, false, new ArrayList<>());
        memory.fillResponse(req1, resp1, 1710156663, "https://juiceshop.akto.io/rest/user/login", URLMethods.Method.POST.name());
        memory.reset(1710156663, "https://juiceshop.akto.io/rest/user/login", URLMethods.Method.POST.name());
        System.out.println("done");


        OriginalHttpRequest req2 = memory.run(1710156663, "https://juiceshop.akto.io/rest/products/reviews", URLMethods.Method.PATCH.name());
        System.out.println("new");
        OriginalHttpResponse resp2 = ApiExecutor.sendRequest(req2, true, null, false, new ArrayList<>());
        memory.fillResponse(req2, resp2, 1710156663, "https://juiceshop.akto.io/rest/products/reviews", URLMethods.Method.PATCH.name());
        memory.reset(1710156663, "https://juiceshop.akto.io/rest/products/reviews", URLMethods.Method.PATCH.name());
        System.out.println("done");


        OriginalHttpRequest req3 = memory.run(1710156663, "https://juiceshop.akto.io/rest/user/login", URLMethods.Method.POST.name());
        System.out.println("new");
        OriginalHttpResponse resp3 = ApiExecutor.sendRequest(req3, true, null, false, new ArrayList<>());
        memory.fillResponse(req3, resp3, 1710156663, "https://juiceshop.akto.io/rest/user/login", URLMethods.Method.POST.name());
        memory.reset(1710156663, "https://juiceshop.akto.io/rest/user/login", URLMethods.Method.POST.name());
        System.out.println("done");


        OriginalHttpRequest req4 = memory.run(1710156663, "https://juiceshop.akto.io/rest/products/reviews", URLMethods.Method.PATCH.name());
        System.out.println("new");
        OriginalHttpResponse resp4 = ApiExecutor.sendRequest(req4, true, null, false, new ArrayList<>());
        memory.fillResponse(req4, resp4, 1710156663, "https://juiceshop.akto.io/rest/products/reviews", URLMethods.Method.PATCH.name());
        memory.reset(1710156663, "https://juiceshop.akto.io/rest/products/reviews", URLMethods.Method.PATCH.name());
        System.out.println("done");


    }

    public Memory(List<ApiInfo.ApiInfoKey> apiInfoKeys, Map<Integer, ReplaceDetail> replaceDetailsMap) {
        this.replaceDetailsMap = replaceDetailsMap;

        // find all parent APIs
        TreeHelper treeHelper = new TreeHelper();
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeys) {
            treeHelper.buildTree(apiInfoKey.getApiCollectionId()+"", apiInfoKey.getUrl(), apiInfoKey.getMethod().name());
        }
        Collection<Node> nodes = treeHelper.result.values();
        List<Bson> filters = new ArrayList<>();
        for (Node node: nodes) {
            nodesMap.put(node.hashCode(), node);
            filters.add(Filters.and(
                    Filters.eq("_id.apiCollectionId", Integer.parseInt(node.getApiCollectionId())),
                    Filters.eq("_id.url", node.getUrl()),
                    Filters.eq("_id.method", node.getMethod())
            ));
        }

        // fetch sample data
        List<SampleData> sdList = SampleDataDao.instance.findAll(Filters.or(filters));
        for (SampleData sampleData: sdList) {
            Key id = sampleData.getId();
            sampleDataMap.put(Objects.hash(id.getApiCollectionId(), id.getUrl(), id.getMethod().name()), sampleData);
        }

        buildParentToChildMap(nodes, parentToChildMap);
    }


    public OriginalHttpRequest run(int apiCollectionId, String url, String method) {
        int hash = Objects.hash(apiCollectionId+"", url, method);
        if (resultMap.get(hash) != null) return resultMap.get(hash).getRequest();

        // todo: optimise this.. no need to make db calls everytime
        TreeHelper treeHelper = new TreeHelper();
        treeHelper.buildTree(apiCollectionId+"", url, method);
        List<Node> nodes = new ArrayList<>(treeHelper.result.values());

        nodes.sort(Comparator.comparingInt(Node::getMaxDepth));

        List<SampleData> sampleDataList = new ArrayList<>();
        for (Node node: nodes) {
            Integer nodeHash = Objects.hash(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), node.getMethod());
            sampleDataList.add(sampleDataMap.get(nodeHash));
        }

        return execute(sampleDataList);
    }


    private OriginalHttpRequest execute(List<SampleData> sdList) {
        int idx = 0;
        for (SampleData sampleData: sdList) {
            idx++;
            boolean isFinal =  sdList.size() == idx; // todo: find a better way
            Key id = sampleData.getId();
            int hash = Objects.hash(id.getApiCollectionId()+"", id.getUrl(), id.getMethod().name());
            if (resultMap.containsKey(hash)) continue;
            try {
                List<String> samples = sampleData.getSamples();
                if (samples.isEmpty()) continue;;

                String sample = samples.get(0);
                OriginalHttpRequest request = new OriginalHttpRequest();
                request.buildFromSampleMessage(sample);
                // todo: String newHost = findNewHost(request, modifyHostDetailMap);
                String newHost = null;

                OriginalHttpResponse originalHttpResponse = new OriginalHttpResponse();
                originalHttpResponse.buildFromSampleMessage(sample);

                // do modifications
                Node node = nodesMap.get(hash);
                ReplaceDetail finalReplaceDetail = getReplaceDetail(node);
                modifyRequest(request, finalReplaceDetail);

                if (isFinal) return request;

                TestingRunConfig testingRunConfig = new TestingRunConfig(0, new HashMap<>(), new ArrayList<>(), null,newHost, null);

                OriginalHttpResponse response = null;
                try {
                    response = ApiExecutor.sendRequest(request,false, testingRunConfig, false, new ArrayList<>());
                    request.getHeaders().remove(Constants.AKTO_IGNORE_FLAG);
                    RawApi rawApi = new RawApi(request, response, "");
                    resultMap.put(hash, rawApi);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        return null;
    }


    public void fillResponse(OriginalHttpRequest request, OriginalHttpResponse response, int apiCollectionId, String url, String method) {
        int hash = Objects.hash(apiCollectionId+"", url, method);
        RawApi rawApi = new RawApi(request, response, "");
        resultMap.put(hash, rawApi);
    }

    public void reset(int apiCollectionId, String url, String method) {
        // find all children
        int hash = Objects.hash(apiCollectionId+"", url, method);
        ReverseNode reverseNode = parentToChildMap.get(hash);
        if (reverseNode == null) return;

        for (ReverseConnection reverseConnection: reverseNode.getReverseConnections().values()) {
            for (ReverseEdge reverseEdge: reverseConnection.getReverseEdges()) {
                int childApiCollectionId = Integer.parseInt(reverseEdge.getApiCollectionId());
                String childUrl = reverseEdge.getUrl();
                String childMethod = reverseEdge.getMethod();

                int childHash = Objects.hash(childApiCollectionId+"", childUrl, childMethod);
                resultMap.remove(childHash);
                reset(childApiCollectionId, childUrl, childMethod);
            }
        }
    }


    public ReplaceDetail getReplaceDetail(Node node) {
        ReplaceDetail replaceDetail = new ReplaceDetail(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), node.getMethod(), new ArrayList<>());
        Map<String, Connection> connections = node.getConnections();
        for (Connection connection: connections.values()) {
            String requestParam = connection.getParam();
            List<Edge> edges = connection.getEdges();
            if (edges.isEmpty()) continue;
            Edge edge = edges.get(0);
            String responseParam = edge.getParam();
            String parentApiCollectionId = edge.getApiCollectionId();
            String parentUrl = edge.getUrl();
            String parentMethod = edge.getMethod();
            int parentHash = Objects.hash(parentApiCollectionId, parentUrl, parentMethod);
            RawApi rawApi = resultMap.get(parentHash);
            if (rawApi == null) continue;
            Map<String, Set<Object>> valuesMap = getValuesMap(rawApi.getResponse());
            Set<Object> values = valuesMap.get(responseParam);
            Object value = values != null && values.size() > 0 ? values.toArray()[0] : null; // todo:
            if (value == null) continue;

            KVPair.KVType type = value instanceof Integer ? KVPair.KVType.INTEGER : KVPair.KVType.STRING;
            KVPair kvPair = new KVPair(requestParam, value.toString(), connection.getIsHeader(), connection.getIsUrlParam(), type);
            replaceDetail.addIfNotExist(kvPair);
        }

        return replaceDetail;
    }

}
