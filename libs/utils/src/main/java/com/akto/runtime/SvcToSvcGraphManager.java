package com.akto.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.graph.K8sDaemonsetGraphParams;
import com.akto.dto.graph.SvcToSvcGraph;
import com.akto.dto.graph.SvcToSvcGraphEdge;
import com.akto.dto.graph.SvcToSvcGraphNode;
import com.akto.dto.graph.SvcToSvcGraphParams;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SvcToSvcGraphManager {
    
    Map<String, Set<String>> edges;
    Set<String> nodes;
    
    Map<String, Set<String>> newEdges;
    Set<String> newNodes;

    Map<String, String> mapCompleteProcessIdToServiceNames;
    int lastFetchFromDb;

    public static SvcToSvcGraphManager createFromEdgesAndNodes(DataActor dataActor) {
        int now = Context.now();
        List<SvcToSvcGraphEdge> edges = dataActor.findAllSvcToSvcGraphEdges(0, now);
        List<SvcToSvcGraphNode> nodes = dataActor.findAllSvcToSvcGraphNodes(0, now);
        SvcToSvcGraphManager instance = new SvcToSvcGraphManager(createEdgesMap(edges), createNodesSet(nodes), new HashMap<>(), new HashSet<>(), new HashMap<>(), now);
        return instance;
        
    }

    private static Map<String, Set<String>> createEdgesMap(List<SvcToSvcGraphEdge> edges) {
        Map<String, Set<String>> ret = new HashMap<>();
        for (SvcToSvcGraphEdge svcToSvcGraphEdge : edges) {
            String source = svcToSvcGraphEdge.getSource();
            String target = svcToSvcGraphEdge.getTarget();
            Set<String> currEdges = ret.get(source);

            if (currEdges == null) {
                currEdges = new HashSet<>();
                ret.put(source, currEdges);
            }
            
            currEdges.add(target);
        }

        return ret;

    }
        
    private static Set<String> createNodesSet(List<SvcToSvcGraphNode> nodes) {
        Set<String> ret = new HashSet<>();
        for (SvcToSvcGraphNode svcToSvcGraphNode : nodes) {
            
            ret.add(svcToSvcGraphNode.getId());
        }

        return ret;

    }

    private boolean addToMap(Map<String, Set<String>> edges, String source, String target) {
        Set<String> currEdges = edges.get(source);

        if (currEdges == null) {
            currEdges = new HashSet<>();
            edges.put(source, currEdges);
        }

        if (currEdges.contains(target)) return false;

        currEdges.add(target);

        return true;
    }
        
    public void processRecord(SvcToSvcGraphParams svcToSvcGraphParams) {
        if (svcToSvcGraphParams == null) {
            return;
        }

        switch (svcToSvcGraphParams.getType()) {
            case K8S:
                K8sDaemonsetGraphParams k8sDaemonsetGraphParams = (K8sDaemonsetGraphParams) svcToSvcGraphParams;
                
                String completeProcessId = k8sDaemonsetGraphParams.getDaemonsetId() + "_" + k8sDaemonsetGraphParams.getProcessId();
                String serviceName = k8sDaemonsetGraphParams.getHostInApiRequest();

                addNode(serviceName);

                switch (k8sDaemonsetGraphParams.getDirection()) {
                    case K8sDaemonsetGraphParams.DIRECTION_INCOMING:
                        addCompleteProcessId(completeProcessId, serviceName);
                        // addEdge (from other tools or from other services using ip address)
                        break;
                    case K8sDaemonsetGraphParams.DIRECTION_OUTGOING:
                        addEdge(completeProcessId, serviceName);
                        break;
                    default:
                        break;
                }

                break;
            default:
                throw new RuntimeException("Unknown type: " + svcToSvcGraphParams.getType());
        }
    }

    private void addCompleteProcessId(String completeProcessId, String serviceName) {
        if (mapCompleteProcessIdToServiceNames.containsKey(completeProcessId)) {
            String existingServiceName = mapCompleteProcessIdToServiceNames.get(completeProcessId);
            if (existingServiceName.equalsIgnoreCase(serviceName)) {
                return;
            } else {
                // this is strange. Process id is same but service name is different
            }
        }
            
        mapCompleteProcessIdToServiceNames.put(completeProcessId, serviceName);
        
    }

    private void addEdge(String completeProcessId, String serviceName) {
        String source = mapCompleteProcessIdToServiceNames.get(completeProcessId);
        if (source == null) return;

        String target = serviceName;

        boolean isNewEdge = addToMap(edges, source, target);

        if (isNewEdge) {
            addToMap(newEdges, source, target);
        }
    }

    private boolean addNode(String serviceName) {
        boolean isNewNode = nodes.add(serviceName);

        if (isNewNode) {
            newNodes.add(serviceName);
            return true;
        } 

        return false;
    }

    private List<SvcToSvcGraphEdge> updateWithNewEdgesAndReturnDelta(List<SvcToSvcGraphEdge> incrEdges) {
        List<SvcToSvcGraphEdge> updates = new ArrayList<>();
        for (SvcToSvcGraphEdge svcToSvcGraphEdge : incrEdges) {
            String newSrc = svcToSvcGraphEdge.getSource();
            String newTgt = svcToSvcGraphEdge.getTarget();
            boolean isNewEdge = addToMap(edges, newSrc, newTgt);

            if (!isNewEdge) {
                continue;
            }

            if (newEdges.containsKey(newSrc)) {
                newEdges.get(newSrc).remove(newTgt);
                continue;
            }
            
        }
        
        for (String src: newEdges.keySet()) {
            Set<String> targets = newEdges.get(src);
            for (String tgt: targets) {
                SvcToSvcGraphEdge svcToSvcGraphEdge = SvcToSvcGraphEdge.createFromK8s(src, tgt);
                updates.add(svcToSvcGraphEdge);
            }
        }

        return updates;
    }   

    private List<SvcToSvcGraphNode> updateWithNewNodesAndReturnDelta(List<SvcToSvcGraphNode> incrNodes) {
        List<SvcToSvcGraphNode> updates = new ArrayList<>();
        for (String node: newNodes) {
            boolean isNewNode = addNode(node);
            
            if (!isNewNode) {
                continue;
            }

            newNodes.remove(node);

        }

        for (String node: newNodes) {
            SvcToSvcGraphNode svcToSvcGraphNode = SvcToSvcGraphNode.createFromK8s(node);
            updates.add(svcToSvcGraphNode);
        }

        return updates;
    }

    private void resetWithNewTs(int fetchFromDbTs) {
        newEdges.clear();
        newNodes.clear();
        this.lastFetchFromDb = fetchFromDbTs;
    }

    public SvcToSvcGraph updateWithNewDataAndReturnDelta(DataActor dataActor) {
        int now = Context.now();
        

        List<SvcToSvcGraphEdge> updateEdges = updateWithNewEdgesAndReturnDelta(dataActor.findAllSvcToSvcGraphEdges(lastFetchFromDb, now));
        List<SvcToSvcGraphNode> updateNodes = updateWithNewNodesAndReturnDelta(dataActor.findAllSvcToSvcGraphNodes(lastFetchFromDb, now));
        resetWithNewTs(now);

        dataActor.updateNewEdgesInBatches(updateEdges);
        dataActor.updateNewNodesInBatches(updateNodes);

        return new SvcToSvcGraph(updateEdges, updateNodes, now);
    }

}
