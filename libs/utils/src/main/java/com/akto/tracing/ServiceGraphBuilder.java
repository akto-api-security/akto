package com.akto.tracing;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.tracing.bedrock.BedrockAgentTraceParser;
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

    /**
     * updateServiceGraph, but an incoming edge may supersede an existing one when it describes
     * the call in more detail — a strictly deeper position in the producer's chain.
     *
     * The plain additive merge is first-write-wins: whichever record created a node fixes its
     * sourceService and metadata for good. That is right for producers whose records all
     * describe the same shape, but the Bedrock interceptor's do not — a tools/list request
     * cannot name the MCP target a tools/call request can, and an unattributable caller leaves
     * out the agent hop that a resolvable one supplies. Without this, the first (shortest)
     * record a collection ever sees pins the graph to its shape permanently.
     *
     * Monotonic by construction: equal or shallower records are ignored, so a later shallow
     * record can never demote a node back up the chain or blank out metadata already shown.
     * Edges with no chainDepth — every other producer — keep first-write-wins untouched.
     */
    public boolean updateServiceGraphPreferringDeeperChain(int apiCollectionId,
            Map<String, ServiceGraphEdgeInfo> edges) {
        if (edges == null || edges.isEmpty()) {
            logger.info("No service graph edges to update for collection: {}", apiCollectionId);
            return true;
        }

        try {
            ApiCollection collection = dataActor.fetchApiCollectionMeta(apiCollectionId);
            if (collection == null) {
                logger.error("API Collection not found: {}", apiCollectionId);
                return false;
            }

            Map<String, ServiceGraphEdgeInfo> existingEdges = collection.getServiceGraphEdges();
            if (existingEdges == null) {
                existingEdges = new HashMap<>();
            }

            Map<String, ServiceGraphEdgeInfo> merged = mergePreferringDeeperChain(existingEdges, edges);

            boolean success = dataActor.updateServiceGraphEdges(apiCollectionId, merged);
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

    /** Pure merge, split out from the method above so it is testable without a DB. */
    Map<String, ServiceGraphEdgeInfo> mergePreferringDeeperChain(
            Map<String, ServiceGraphEdgeInfo> existingEdges, Map<String, ServiceGraphEdgeInfo> edges) {
        Map<String, ServiceGraphEdgeInfo> merged =
            existingEdges == null ? new HashMap<>() : new HashMap<>(existingEdges);
        if (edges == null) {
            return merged;
        }
        for (Map.Entry<String, ServiceGraphEdgeInfo> entry : edges.entrySet()) {
            ServiceGraphEdgeInfo existing = merged.get(entry.getKey());
            if (existing == null || supersedes(entry.getValue(), existing)) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    /**
     * True when the incoming edge sits deeper in the chain than the one already stored.
     *
     * An existing edge with no depth predates this field (or came from another producer), so it
     * is superseded once — a one-time upgrade to the chain-aware shape, after which the normal
     * strictly-deeper rule applies.
     */
    private boolean supersedes(ServiceGraphEdgeInfo incoming, ServiceGraphEdgeInfo existing) {
        Integer incomingDepth = chainDepth(incoming);
        if (incomingDepth == null) {
            return false;
        }
        Integer existingDepth = chainDepth(existing);
        return existingDepth == null || incomingDepth > existingDepth;
    }

    private Integer chainDepth(ServiceGraphEdgeInfo edge) {
        if (edge == null || edge.getMetadata() == null) {
            return null;
        }
        Object depth = edge.getMetadata().get(BedrockAgentTraceParser.CHAIN_DEPTH);
        // Mongo hands back a Double or Long depending on how the document was written, so read
        // through Number rather than casting to the Integer that was originally put in.
        return depth instanceof Number ? ((Number) depth).intValue() : null;
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
