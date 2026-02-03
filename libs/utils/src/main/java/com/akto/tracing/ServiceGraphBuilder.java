package com.akto.tracing;

import com.akto.dao.ApiCollectionsDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
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
            ApiCollection collection = ApiCollectionsDao.instance.getMeta(apiCollectionId);
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

            // Update in database
            Bson filter = Filters.eq(ApiCollection.ID, apiCollectionId);
            Bson update = Updates.set(ApiCollection.SERVICE_GRAPH_EDGES, existingEdges);
            ApiCollectionsDao.instance.updateOne(filter, update);

            logger.info("Updated service graph for collection {} with {} edges", apiCollectionId, edges.size());

            return true;

        } catch (Exception e) {
            logger.error("Failed to update service graph: {}", e.getMessage(), e);
            return false;
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
