package com.akto.tracing;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ServiceGraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ServiceGraphBuilder.class);
    private static final ServiceGraphBuilder INSTANCE = new ServiceGraphBuilder();
    private Map<String, Integer> workflowIdToApiCollectionIdMap = new HashMap<>();
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public static ServiceGraphBuilder getInstance() {
        return INSTANCE;
    }

    public boolean updateServiceGraph(int apiCollectionId, Map<String, ServiceGraphEdgeInfo> edges) {
        if (edges == null || edges.isEmpty()) {
            logger.info("No service graph edges to update for collection: {}", apiCollectionId);
            return true;
        }

        try {
            // Fetch current collection
            ApiCollection collection = dataActor.fetchApiCollectionMeta(apiCollectionId);
            if (collection == null) {
                logger.error("API Collection not found: {}", apiCollectionId);
                return false;
            }

            // Get existing service graph or create new
            Map<String, ServiceGraphEdgeInfo> existingEdges = collection.getServiceGraphEdges();
            if (existingEdges == null) {
                existingEdges = new HashMap<>();
            }

            // Merge new edges with existing
            for (Map.Entry<String, ServiceGraphEdgeInfo> entry : edges.entrySet()) {
                String targetService = entry.getKey();
                ServiceGraphEdgeInfo newEdge = entry.getValue();

                ServiceGraphEdgeInfo edgeInfo = existingEdges.get(targetService);
                if (edgeInfo == null) {
                    edgeInfo = newEdge;
                } else {
                    // Edge already exists — keep any metadata the incoming edge has that the existing one lacks; never overwrite.
                    mergeMetadata(edgeInfo, newEdge);
                }

                existingEdges.put(targetService, edgeInfo);
            }

            boolean success = dataActor.updateServiceGraphEdges(apiCollectionId, existingEdges);
            if (!success) {
                logger.error("Failed to update service graph edges for collection: {}", apiCollectionId);
                return false;
            }

            logger.info("Updated service graph for collection {} with {} edges", apiCollectionId, edges.size());

            return true;

        } catch (Exception e) {
            logger.error("Failed to update service graph: {}", e.getMessage(), e);
            return false;
        }
    }

    /** Same additive merge as updateServiceGraph, plus pruning: an existing edge matching sourceService+scope is dropped if freshEdges no longer has it. */
    public boolean pruneAndUpdateServiceGraph(int apiCollectionId, Map<String, ServiceGraphEdgeInfo> existingEdges,
                                               String sourceService, Map<String, String> scope,
                                               Map<String, ServiceGraphEdgeInfo> freshEdges) {
        try {
            Map<String, ServiceGraphEdgeInfo> merged =
                pruneAndMerge(existingEdges, sourceService, scope, freshEdges);

            boolean success = dataActor.updateServiceGraphEdges(apiCollectionId, merged);
            if (!success) {
                logger.error("Failed to update service graph edges for collection: {}", apiCollectionId);
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to prune and update service graph: {}", e.getMessage(), e);
            return false;
        }
    }

    /** Pure prune-then-merge, split out from {@link #pruneAndUpdateServiceGraph} so it's testable without a DB. */
    static Map<String, ServiceGraphEdgeInfo> pruneAndMerge(Map<String, ServiceGraphEdgeInfo> existingEdges,
            String sourceService, Map<String, String> scope, Map<String, ServiceGraphEdgeInfo> freshEdges) {
        Map<String, ServiceGraphEdgeInfo> result = existingEdges == null
            ? new HashMap<>() : new HashMap<>(existingEdges);
        Map<String, ServiceGraphEdgeInfo> fresh = freshEdges != null ? freshEdges : new HashMap<>();

        result.entrySet().removeIf(e -> {
            ServiceGraphEdgeInfo edge = e.getValue();
            return edge != null && sourceService.equals(edge.getSourceService())
                && inScope(edge, scope) && !fresh.containsKey(e.getKey());
        });

        for (Map.Entry<String, ServiceGraphEdgeInfo> entry : fresh.entrySet()) {
            ServiceGraphEdgeInfo existing = result.get(entry.getKey());
            if (existing == null) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                mergeMetadata(existing, entry.getValue());
            }
        }
        return result;
    }

    /** True only if the edge matches every scope key; an empty scope is treated as no-match, never as match-all. */
    private static boolean inScope(ServiceGraphEdgeInfo edge, Map<String, String> scope) {
        if (scope == null || scope.isEmpty() || edge.getMetadata() == null) return false;
        for (Map.Entry<String, String> entry : scope.entrySet()) {
            if (!entry.getValue().equals(edge.getMetadata().get(entry.getKey()))) return false;
        }
        return true;
    }

    /** Copies metadata keys the existing edge doesn't already have. Never overwrites. */
    private static void mergeMetadata(ServiceGraphEdgeInfo existing, ServiceGraphEdgeInfo incoming) {
        if (incoming == null || incoming.getMetadata() == null || incoming.getMetadata().isEmpty()) {
            return;
        }
        if (existing.getMetadata() == null) {
            existing.setMetadata(new HashMap<>(incoming.getMetadata()));
            return;
        }
        for (Map.Entry<String, Object> entry : incoming.getMetadata().entrySet()) {
            existing.getMetadata().putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    public int getApiCollectionIdFromWorkflowId(String workflowId, String hostName) {
        // Check cache first
        if (workflowIdToApiCollectionIdMap.containsKey(workflowId)) {
            int apiCollectionId = workflowIdToApiCollectionIdMap.get(workflowId);
            logger.debug("Found cached collection {} for workflowId: {}", apiCollectionId, workflowId);
            return apiCollectionId;
        }

        // Query database if not in cache
        ApiCollection collection = dataActor.findApiCollectionByName(hostName);

        if (collection != null) {
            int apiCollectionId = collection.getId();
            // Cache the result
            workflowIdToApiCollectionIdMap.put(workflowId, apiCollectionId);
            logger.info("Found collection {} for workflowId: {} and cached it", apiCollectionId, workflowId);
            return apiCollectionId;
        } else {
            logger.info("No collection found for workflowId: {}", workflowId);
            return -1;
        }

    }

    public Map<String, Integer> getWorkflowIdToApiCollectionIdMap() {
        return workflowIdToApiCollectionIdMap;
    }

    public void setWorkflowIdToApiCollectionIdMap(Map<String, Integer> workflowIdToApiCollectionIdMap) {
        this.workflowIdToApiCollectionIdMap = workflowIdToApiCollectionIdMap;
    }
}
