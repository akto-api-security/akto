package com.akto.tracing;

import com.akto.dao.ApiCollectionsDao;
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

    public int getApiCollectionIdFromWorkflowId(String workflowId) {
        ApiCollection collection = ApiCollectionsDao.instance.findOne(
            Filters.eq(ApiCollection.NAME, workflowId)
        );

        if (collection != null) {
            logger.info("Found collection {} for workflowId: {}", collection.getId(), workflowId);
            return collection.getId();
        } else {
            logger.info("No collection found for workflowId: {}", workflowId);
            return -1;
        }
        
    }
}
