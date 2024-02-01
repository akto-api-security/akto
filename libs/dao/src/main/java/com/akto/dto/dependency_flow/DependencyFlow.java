package com.akto.dto.dependency_flow;

import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dao.DependencyNodeDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.DependencyNode;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.SzwarcfiterLauerSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;

public class DependencyFlow {

    public Map<Integer, ReverseNode> initialNodes = new HashMap<>();
    public Map<Integer, Node> resultNodes = new HashMap<>();

    public void syncWithDb() {
        List<Node> nodes = new ArrayList<>(resultNodes.values());
        if (nodes.size() > 0) {
            for (Node node: nodes) {
                node.fillMaxDepth();
            }
            DependencyFlowNodesDao.instance.getMCollection().drop();
            DependencyFlowNodesDao.instance.insertMany(nodes);
        }
    }

    Queue<Integer> queue = new LinkedList<>();
    Set<Integer> done = new HashSet<>();

    public void addToQueue(int nodeId) {
        queue.add(nodeId);
        done.add(nodeId);
    }

    public void run() {
        initialNodes = new HashMap<>();
        resultNodes = new HashMap<>();
        queue = new LinkedList<>();
        done = new HashSet<>();

        List<DependencyNode> dependencyNodeList = DependencyNodeDao.instance.findAll(new BasicDBObject());

        for (DependencyNode dependencyNode: dependencyNodeList) {
            fillNodes(dependencyNode); // to fill who is giving data to whom
            fillResultNodes(dependencyNode); // to fill who is receiving data from whom
        }

        // build initial queue
        // depth 0 for initial queue
        for (ReverseNode reverseNode: initialNodes.values()) {
            int id = reverseNode.hashCode();
            if (resultNodes.containsKey(id)) continue; // to check if the nodes is getting data from anywhere. If yes then don't add to queue now.
            addToQueue(id);
            Node resultNodeForZeroLevel = new Node(reverseNode.getApiCollectionId(), reverseNode.getUrl(), reverseNode.getMethod(), new HashMap<>());
            resultNodes.put(id, resultNodeForZeroLevel); // we want to still store level 0 nodes in db
        }

        parseTree(); // pops node from queue and fill all the child APIs of that particular

        int maxCycles = 20;
        while (maxCycles > 0) {
            maxCycles -= 1;
            Integer nodeToBeMarkedDone = markNodeAsDone();
            if (nodeToBeMarkedDone == null) return;
            addToQueue(nodeToBeMarkedDone);
            parseTree();
        }
    }

    public void parseTree() {
        // pop queue until empty
        while (!queue.isEmpty()) {
            int nodeId = queue.remove();
            ReverseNode reverseNode = initialNodes.get(nodeId); // reverse node basically says this API is sending data to what other APIs

            if (reverseNode == null || reverseNode.getReverseConnections() == null) {
                // this means the node is not giving data to anyone so next node please!
                continue;
            }

            Node nodeFromQueue = resultNodes.get(nodeId);
            nodeFromQueue.fillMaxDepth();
            int depth = nodeFromQueue.getMaxDepth();

            Map<String,ReverseConnection>  reverseConnections = reverseNode.getReverseConnections();
            for (ReverseConnection reverseConnection: reverseConnections.values()) {
                for (ReverseEdge reverseEdge: reverseConnection.getReverseEdges()) {
                    // resultNode is basically find which node is receiving the data and fill the edge with that particular parameter
                    Node resultNode = resultNodes.get(Objects.hash(reverseEdge.getApiCollectionId(), reverseEdge.getUrl(), reverseEdge.getMethod()));
                    int key = resultNode.hashCode();
                    if (done.contains(key)) continue;
                    Edge edge = new Edge(reverseNode.getApiCollectionId(), reverseNode.getUrl(), reverseNode.getMethod(), reverseConnection.getParam(), reverseEdge.getIsHeader(), reverseEdge.getCount(), depth + 1);
                    Connection connection = resultNode.getConnections().get(reverseEdge.getParam());
                    if (connection == null) {
                        connection = new Connection(edge.getParam(), new ArrayList<>(), reverseEdge.isUrlParam(), reverseEdge.getIsHeader());
                        resultNode.getConnections().put(reverseEdge.getParam(), connection);
                    }
                    connection.getEdges().add(edge);

                    boolean flag = isDone(resultNode); // a node is marked done when all parameters have found a parent
                    if (flag) {
                        addToQueue(key);
                    }
                }
            }
        }
    }

    public Integer markNodeAsDone() {
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
            directedGraph.addVertex(node.hashCode()+"");
        }

        // Building edges
        for (Node node: incompleteNodes) {
            int nodeId = node.hashCode();
            // reverse node basically says this API is sending data to what other APIs
            ReverseNode reverseNode = initialNodes.get(nodeId);
            if (reverseNode == null || reverseNode.getReverseConnections() == null) {
                // this means the node is not giving data to anyone so next node please!
                continue;
            }

            Map<String,ReverseConnection>  reverseConnections = reverseNode.getReverseConnections();
            for (ReverseConnection reverseConnection: reverseConnections.values()) {
                for (ReverseEdge reverseEdge: reverseConnection.getReverseEdges()) {
                    int id = Objects.hash(reverseEdge.getApiCollectionId(), reverseEdge.getUrl(), reverseEdge.getMethod());

                    Node resultNode = resultNodes.get(id);
                    Map<String, Connection> connections = resultNode.getConnections();
                    Connection connection = connections.get(reverseEdge.getParam());
                    List<Edge> edges = connection.getEdges();

                    if (edges == null || !edges.isEmpty()) continue; // this is because if it already has an edge this means that parameter is not part of the loop, so skip it
                    if (directedGraph.containsVertex(id+"")) { // this check is added to make sure we are only making edges inside the loop
                        directedGraph.addEdge(nodeId+"", id+"");
                    }
                }
            }
        }

        SzwarcfiterLauerSimpleCycles<String, DefaultEdge> simpleCyclesFinder = new SzwarcfiterLauerSimpleCycles<>(directedGraph);
        List<List<String>> cycles = simpleCyclesFinder.findSimpleCycles();

        if (cycles.isEmpty()) return null;

        System.out.println("We found " + cycles.size() + " circular dependency");

        int currentMinMissingCount = Integer.MAX_VALUE;
        Integer result = null;
        for (List<String> cycle : cycles) {
            for (String nodeId: cycle) {
                int key = Integer.parseInt(nodeId);
                Node node = resultNodes.get(key);
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

                if (missing == 0) return key; // this means except headers all values have been filled.

                if (filled == 0) continue;

                if (missing < currentMinMissingCount)  result = key;
            }
        }

        if (result != null) {
            System.out.println(resultNodes.get(result).getUrl() + " is being marked done");
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

    public void fillNodes(DependencyNode dependencyNode) {
        ReverseNode reverseNode = new ReverseNode(
                dependencyNode.getApiCollectionIdResp(),
                dependencyNode.getUrlResp(),
                dependencyNode.getMethodResp(),
                new HashMap<>()
        );

        Integer key = reverseNode.hashCode();
        reverseNode = initialNodes.getOrDefault(key, reverseNode);

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

        initialNodes.put(key, reverseNode);
    }

    public void fillResultNodes(DependencyNode dependencyNode) {
        Node node = new Node(
                dependencyNode.getApiCollectionIdReq(),
                dependencyNode.getUrlReq(),
                dependencyNode.getMethodReq(),
                new HashMap<>()
        );

        Integer key = node.hashCode();
        node = resultNodes.getOrDefault(key, node);

        for (DependencyNode.ParamInfo paramInfo: dependencyNode.getParamInfos()) {
            String paramReq = paramInfo.getRequestParam();
            Connection connection = new Connection(paramReq, new ArrayList<>(), paramInfo.getIsUrlParam(), paramInfo.getIsHeader());
            node.getConnections().put(paramReq, connection);
        }

        resultNodes.put(key, node);
    }

}
