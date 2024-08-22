package com.akto.dto.dependency_flow;

import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dao.DependencyNodeDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.DependencyNode;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertManyOptions;

import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.cycle.SzwarcfiterLauerSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DependencyFlow {

    public Map<ApiInfo.ApiInfoKey, Node> resultNodes = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(DependencyFlow.class);

    public void syncWithDb() {
        List<Node> nodes = new ArrayList<>(resultNodes.values());
        if (nodes.size() > 0) {
            for (Node node: nodes) {
                node.fillMaxDepth();
                node.replaceDots();
            }
            DependencyFlowNodesDao.instance.getMCollection().drop();
            DependencyFlowNodesDao.instance.getMCollection().insertMany(nodes, new InsertManyOptions().ordered(false));
        }
    }

    Queue<ApiInfo.ApiInfoKey> queue = new LinkedList<>();
    Set<ApiInfo.ApiInfoKey> done = new HashSet<>();

    public void addToQueue(ApiInfo.ApiInfoKey apiInfoKey) {
        queue.add(apiInfoKey);
        done.add(apiInfoKey);
    }

    public void findL0Apis(Set<ApiInfo.ApiInfoKey> neverL0, Set<ApiInfo.ApiInfoKey> maybeL0, List<DependencyNode> dependencyNodeList) {
        for (DependencyNode dependencyNode: dependencyNodeList) {
            ApiInfo.ApiInfoKey req = new ApiInfo.ApiInfoKey(Integer.parseInt(dependencyNode.getApiCollectionIdReq()), dependencyNode.getUrlReq(), URLMethods.Method.fromString(dependencyNode.getMethodReq()));
            neverL0.add(req);
            maybeL0.remove(req);

            ApiInfo.ApiInfoKey resp = new ApiInfo.ApiInfoKey(Integer.parseInt(dependencyNode.getApiCollectionIdResp()), dependencyNode.getUrlResp(), URLMethods.Method.fromString(dependencyNode.getMethodResp()));
            if (!neverL0.contains(resp)) maybeL0.add(resp);
        }
    }

    static String ID = "_id";
    // if apiCollectionId is null then don't apply any filter
    public void run(String apiCollectionId) {
        resultNodes = new HashMap<>();
        queue = new LinkedList<>();
        done = new HashSet<>();

        ObjectId lastId = null;
        int limit = 100;
        Set<ApiInfo.ApiInfoKey> neverL0 = new HashSet<>();
        Set<ApiInfo.ApiInfoKey> maybeL0 = new HashSet<>();
        
        Bson apiCollectionIDFilter = apiCollectionId == null ? null :  Filters.and(
                Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionId),
                Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionId)
        );
        
        int maxIterations = 100;
        while (maxIterations > 0) {
            maxIterations -= 1;
            Bson filter = lastId == null ? new BasicDBObject() : Filters.gt(ID, lastId);
            Bson mainFilter = apiCollectionIDFilter != null ? Filters.and(filter, apiCollectionIDFilter) : filter;
            List<DependencyNode> dependencyNodeList = DependencyNodeDao.instance.findAll(mainFilter, 0, limit, Sorts.ascending(ID));
            findL0Apis(neverL0, maybeL0, dependencyNodeList);
            for (DependencyNode dependencyNode: dependencyNodeList) {
                lastId = dependencyNode.getId();
                fillResultNodes(dependencyNode); // to fill who is receiving data from whom
            }
            if (dependencyNodeList.size() < limit) break;
        }

        List<ApiInfo.ApiInfoKey> l0Apis = new ArrayList<>(maybeL0);

        // build initial queue
        // depth 0 for initial queue
        for (ApiInfo.ApiInfoKey apiInfoKey: l0Apis) {
            addToQueue(apiInfoKey);
            resultNodes.put(apiInfoKey, new Node(apiInfoKey.getApiCollectionId()+"", apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), new HashMap<>())); // we want to still store level 0 nodes in db
        }

        parseTree(); // pops node from queue and fill all the child APIs of that particular

        int maxCycles = 20;
        while (maxCycles > 0) {
            maxCycles -= 1;
            ApiInfo.ApiInfoKey nodeToBeMarkedDone = markNodeAsDone();
            if (nodeToBeMarkedDone == null) return;
            addToQueue(nodeToBeMarkedDone);
            parseTree();
        }
    }

    public static ReverseNode buildReverseNode(ApiInfo.ApiInfoKey apiInfoKey) {
        List<DependencyNode> dependencyNodes = DependencyNodeDao.instance.findAll(
                Filters.and(
                        Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiInfoKey.getApiCollectionId()+""),
                        Filters.eq(DependencyNode.URL_RESP, apiInfoKey.getUrl()),
                        Filters.eq(DependencyNode.METHOD_RESP, apiInfoKey.getMethod().name())
                ), 0, 500, Sorts.ascending("_id")
        );

        ReverseNode reverseNode = new ReverseNode(
                apiInfoKey.getApiCollectionId()+"", apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), new HashMap<>()
        );
        for (DependencyNode dependencyNode: dependencyNodes) {
            for (DependencyNode.ParamInfo paramInfo: dependencyNode.getParamInfos()) {
                String paramResp = paramInfo.getResponseParam();
                ReverseConnection reverseConnection = reverseNode.getReverseConnections().getOrDefault(paramResp, new ReverseConnection(paramResp, new ArrayList<>()));

                String paramReq = paramInfo.getRequestParam();
                reverseConnection.getReverseEdges().add(
                        new ReverseEdge(
                                dependencyNode.getApiCollectionIdReq(), dependencyNode.getUrlReq(), dependencyNode.getMethodReq(), paramReq, paramInfo.getCount(), paramInfo.getIsUrlParam(), paramInfo.getIsHeader()
                        )
                );

                reverseNode.getReverseConnections().put(paramResp, reverseConnection);
            }
        }

        return reverseNode;
    }

    public void parseTree() {
        // pop queue until empty
        while (!queue.isEmpty()) {
            ApiInfo.ApiInfoKey apiInfoKey = queue.remove();
            ReverseNode reverseNode = buildReverseNode(apiInfoKey);

            if (reverseNode.getReverseConnections() == null) {
                // this means the node is not giving data to anyone so next node please!
                continue;
            }

            Node nodeFromQueue = resultNodes.get(apiInfoKey);
            if (nodeFromQueue == null) continue;
            nodeFromQueue.fillMaxDepth();
            int depth = nodeFromQueue.getMaxDepth();

            Map<String,ReverseConnection>  reverseConnections = reverseNode.getReverseConnections();
            for (ReverseConnection reverseConnection: reverseConnections.values()) {
                for (ReverseEdge reverseEdge: reverseConnection.getReverseEdges()) {
                    // resultNode is basically find which node is receiving the data and fill the edge with that particular parameter
                    ApiInfo.ApiInfoKey resultApiInfoKey = new ApiInfo.ApiInfoKey(Integer.parseInt(reverseEdge.getApiCollectionId()), reverseEdge.getUrl(), URLMethods.Method.fromString(reverseEdge.getMethod()));
                    Node resultNode = resultNodes.get(resultApiInfoKey);
                    if (resultNode == null) continue;
                    if (done.contains(resultApiInfoKey)) continue;
                    Edge edge = new Edge(reverseNode.getApiCollectionId(), reverseNode.getUrl(), reverseNode.getMethod(), reverseConnection.getParam(), reverseEdge.getIsHeader(), reverseEdge.getCount(), depth + 1);
                    String requestParam = reverseEdge.getParam();

                    if (reverseEdge.isUrlParam()) {
                        requestParam += "_isUrlParam";
                    } else if (reverseEdge.getIsHeader()) {
                        requestParam += "_isHeader";
                    }

                    Map<String, Connection> resultNodeconnections = resultNode.getConnections();
                    if (resultNodeconnections == null) resultNodeconnections = new HashMap<>();
                    Connection connection = resultNodeconnections.get(requestParam);
                    if (connection == null) {
                        connection = new Connection(edge.getParam(), new ArrayList<>(), reverseEdge.isUrlParam(), reverseEdge.getIsHeader());
                        resultNodeconnections.put(reverseEdge.getParam(), connection);
                    }
                    connection.getEdges().add(edge);

                    boolean flag = isDone(resultNode); // a node is marked done when all parameters have found a parent
                    if (flag) {
                        addToQueue(resultApiInfoKey);
                    }
                }
            }
        }
    }

    static String generateStringFromApiInfoKey(ApiInfo.ApiInfoKey apiInfoKey) {
        return apiInfoKey.getApiCollectionId() + "$" + apiInfoKey.getUrl() + "$" + apiInfoKey.getMethod().name();
    }

    static ApiInfo.ApiInfoKey generateApiInfoKeyFromString(String apiInfoKeyString) {
        String[] split = apiInfoKeyString.split("\\$");
        return new ApiInfo.ApiInfoKey(Integer.parseInt(split[0]), split[1], URLMethods.Method.fromString(split[2]));
    }

    public ApiInfo.ApiInfoKey markNodeAsDone() {
        List<Node> nodes = new ArrayList<>(resultNodes.values());

        // incompleteNodes are the nodes which have some parameters for which we were not able to figure out the parent
        // this happens when there is a circular dependency in the graph
        List<Node> incompleteNodes = new ArrayList<>();
        if (nodes.size() > 0) {
            for (Node node: nodes) {
                if (!isDone(node) ) incompleteNodes.add(node);
            }
        }

        if (incompleteNodes.isEmpty()) return null;

        // we add all the incomplete nodes to a graph
        Graph<String, DefaultEdge> directedGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (Node node: incompleteNodes) {
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), URLMethods.Method.fromString(node.getMethod()));
            directedGraph.addVertex(generateStringFromApiInfoKey(apiInfoKey));
        }

        // Building edges
        for (Node node: incompleteNodes) {
            // reverse node basically says this API is sending data to what other APIs
            ApiInfo.ApiInfoKey parentApi = new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), URLMethods.Method.fromString(node.getMethod()));
            ReverseNode reverseNode = buildReverseNode(parentApi);
            if (reverseNode.getReverseConnections() == null) {
                // this means the node is not giving data to anyone so next node please!
                continue;
            }

            Map<String,ReverseConnection>  reverseConnections = reverseNode.getReverseConnections();
            for (ReverseConnection reverseConnection: reverseConnections.values()) {
                for (ReverseEdge reverseEdge: reverseConnection.getReverseEdges()) {
                    ApiInfo.ApiInfoKey childApi = new ApiInfo.ApiInfoKey(Integer.parseInt(reverseEdge.getApiCollectionId()), reverseEdge.getUrl(), URLMethods.Method.fromString(reverseEdge.getMethod()));

                    Node resultNode = resultNodes.get(childApi);
                    if (resultNode == null) continue;
                    Map<String, Connection> connections = resultNode.getConnections();
                    if (connections == null) continue;

                    String param = reverseEdge.getParam();
                    if (reverseEdge.getIsHeader()) {
                        param += "_isHeader";
                    } else if (reverseEdge.isUrlParam()) {
                        param += "_isUrlParam";
                    }

                    Connection connection = connections.get(param);
                    if (connection == null) continue;
                    List<Edge> edges = connection.getEdges();

                    if (edges == null || !edges.isEmpty()) continue; // this is because if it already has an edge this means that parameter is not part of the loop, so skip it
                    if (directedGraph.containsVertex(generateStringFromApiInfoKey(childApi))) { // this check is added to make sure we are only making edges inside the loop
                        directedGraph.addEdge(generateStringFromApiInfoKey(parentApi), generateStringFromApiInfoKey(childApi));
                    }
                }
            }
        }

        CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<String, DefaultEdge>(directedGraph);

        boolean cycleDetected = cycleDetector.detectCycles();
        if (!cycleDetected) return null;
        Set<String> cycleVertices = cycleDetector.findCycles();

        int currentMinMissingCount = Integer.MAX_VALUE;
        ApiInfo.ApiInfoKey result = null;
        for (String apiInfoKeyString: cycleVertices) {
            ApiInfo.ApiInfoKey apiInfoKey = generateApiInfoKeyFromString(apiInfoKeyString);

            Node node = resultNodes.get(apiInfoKey);
            if (node == null) continue;
            Map<String, Connection> connections = node.getConnections();
            int missing = 0;
            int filled = 0;
            for (Connection connection: connections.values()) {
                if (connection.getIsHeader()) continue;
                if (connection.getEdges() == null || connection.getEdges().isEmpty()) {
                    missing += 1;
                } else {
                    filled += 1;
                }
            }

            if (missing == 0) return apiInfoKey; // this means except headers all values have been filled.

            if (filled == 0) continue;

            if (missing < currentMinMissingCount)  result = apiInfoKey;
        }

        if (result != null && resultNodes.get(result) != null) {
            logger.info(resultNodes.get(result).getUrl() + " is being marked done");
        }

        return result;
    }

    public static boolean isDone(Node node) {
        boolean flag = true;
        for (Connection c: node.getConnections().values()) {
            flag = flag && c.getEdges().size() > 0;
        }

        return flag;
    }


    public void fillResultNodes(DependencyNode dependencyNode) {
        Node node = new Node(
                dependencyNode.getApiCollectionIdReq(),
                dependencyNode.getUrlReq(),
                dependencyNode.getMethodReq(),
                new HashMap<>()
        );

        ApiInfo.ApiInfoKey key = new ApiInfo.ApiInfoKey(Integer.parseInt(dependencyNode.getApiCollectionIdReq()), dependencyNode.getUrlReq(), URLMethods.Method.fromString(dependencyNode.getMethodReq()));
        node = resultNodes.getOrDefault(key, node);

        for (DependencyNode.ParamInfo paramInfo: dependencyNode.getParamInfos()) {
            String paramReq = paramInfo.getRequestParam();
            Connection connection = new Connection(paramReq, new ArrayList<>(), paramInfo.getIsUrlParam(), paramInfo.getIsHeader());

            if (paramInfo.getIsHeader()) {
                paramReq += "_isHeader";
            } else if (paramInfo.getIsUrlParam()) {
                paramReq += "_isUrlParam";
            }

            node.getConnections().put(paramReq, connection);
        }

        resultNodes.put(key, node);
    }

}
