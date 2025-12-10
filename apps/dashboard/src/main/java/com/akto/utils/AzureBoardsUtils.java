package com.akto.utils;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.akto.dao.AzureBoardsIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.azure_boards_integration.AzureBoardsIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.akto.util.Pair;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class AzureBoardsUtils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AzureBoardsUtils.class, LogDb.DASHBOARD);

    public static final String version = "7.1";

    private static final String FIELDS_ENDPOINT = "/%s/_apis/wit/fields?api-version=%s";
    public static final String WORK_ITEM_TYPE_FIELDS_ENDPOINT = "/%s/%s/_apis/wit/workitemtypes/%s/fields?$expand=allowedValues&api-version=%s";
    public static final String CLASSIFICATION_NODES_ENDPOINT = "/%s/%s/_apis/wit/classificationnodes/%s?$depth=%s&api-version=%s";
    private static final int MAX_AREA_CLASSIFICATION_TREE_DEPTH = 3;
    private static final int MAX_AREA_CLASSIFICATION_NODES = 50;

    // Caching for Account Wise Azure Boards Integration Work Item Creation Fields
    private static final ConcurrentHashMap<Integer, Pair<Map<String, Map<String, BasicDBList>>, Integer>> accountWiseABFields = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 30 * 60; // 30 minutes

    // Thread pool for making calls to Azure Devops in parallel
    private static final ExecutorService adoPool = Executors.newFixedThreadPool(10);

    public static BasicDBObject callFieldsEndpoint(AzureBoardsIntegration azureBoardsIntegration) throws Exception {
        /*
         * Fetch work item fields for organization from Azure Boards fields endpoint
         * docs: https://learn.microsoft.com/en-us/rest/api/azure/devops/wit/fields/list?view=azure-devops-rest-7.1&tabs=HTTP
         * 
         * Example endpoint: GET https://dev.azure.com/{organization}/{project}/_apis/wit/fields?api-version=7.1
         */

        String formattedEndpoint = String.format(FIELDS_ENDPOINT, azureBoardsIntegration.getOrganization(), version);
        String requestUrl = azureBoardsIntegration.getBaseUrl() + formattedEndpoint;

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + azureBoardsIntegration.getPersonalAuthToken()));

        OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "GET", null, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
        String responsePayload = response.getBody();

        if (response.getStatusCode() > 201 || responsePayload == null) {
            loggerMaker.errorAndAddToDb(String.format("Error while making Azure boards work item fields request. Response Code %d", response.getStatusCode()));
            return null;
        }

        BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
        return respPayloadObj;
    }

    public static BasicDBObject callWorkItemTypeFieldsEndpoint(AzureBoardsIntegration azureBoardsIntegration, String projectName, String workItemType) throws Exception {
        /*
         * Fetch work item fields for a given work item type in a Azure Boards project
         * docs: https://learn.microsoft.com/en-us/rest/api/azure/devops/wit/work-item-types-field/list?view=azure-devops-rest-7.1&tabs=HTTP
         * 
         * Example endpoint: GET https://dev.azure.com/{organization}/{project}/_apis/wit/workitemtypes/{type}/fields?$expand={$expand}&api-version=7.1
         */
        String formattedEndpoint = String.format(WORK_ITEM_TYPE_FIELDS_ENDPOINT, azureBoardsIntegration.getOrganization(), projectName, workItemType, version);
        String requestUrl = azureBoardsIntegration.getBaseUrl() + formattedEndpoint;

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + azureBoardsIntegration.getPersonalAuthToken()));

        OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "GET", null, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
        String responsePayload = response.getBody();

        if (response.getStatusCode() > 201 || responsePayload == null) {
            loggerMaker.errorAndAddToDb(String.format("Error while making Azure boards request for fetching work item fields - (%s,%s). Response Code %d", projectName, workItemType, response.getStatusCode()));
            return null;
        }

        BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
        return respPayloadObj;
    }

    public static BasicDBObject callClassificationNodesEndpoint(AzureBoardsIntegration azureBoardsIntegration, String projectName, String structureGroup, String depth) throws Exception {
        /*
         * Fetch classification nodes of a given structure group in a Azure Boards project
         * docs:https://learn.microsoft.com/en-us/rest/api/azure/devops/wit/classification-nodes/get?view=azure-devops-rest-7.1&tabs=HTTP
         */
        String formattedEndpoint = String.format(CLASSIFICATION_NODES_ENDPOINT, azureBoardsIntegration.getOrganization(), projectName, structureGroup, depth, version);
        String requestUrl = azureBoardsIntegration.getBaseUrl() + formattedEndpoint;

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + azureBoardsIntegration.getPersonalAuthToken()));

        OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "GET", null, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
        String responsePayload = response.getBody();

        if (response.getStatusCode() > 201 || responsePayload == null) {
            loggerMaker.errorAndAddToDb(String.format("Error while making Azure boards request for fetching classification nodes - (%s,%s). Response Code %d", projectName, structureGroup, response.getStatusCode()));
            return null;
        }

        BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
        return respPayloadObj;
    }

    public static void traverse(BasicDBObject node, StringBuilder currentPath, List<String> result, int currentDepth, AtomicInteger nodeCount) {
        if (currentDepth > MAX_AREA_CLASSIFICATION_TREE_DEPTH) return;  // Safety Guard: Stop recursion if we exceed max depth
        if (nodeCount.get() >= MAX_AREA_CLASSIFICATION_NODES) return; // Safety Guard: Stop if we have processed max nodes
        
        nodeCount.incrementAndGet(); // Increment the counter for the current node

        String name = node.getString("name");
        if (name == null) return;
    
        int originalLength = currentPath.length(); // Remember length before append for backtracking

        // Append separator only if not root
        if (originalLength > 0) {
            currentPath.append("\\\\");
        }
        currentPath.append(name);

        // Add string to result
        result.add(currentPath.toString());

        // Check for children and recurse
        Object childrenObj = node.get("children");
        
        if (childrenObj instanceof List) {
            List<?> children = (List<?>) childrenObj;
            for (Object child : children) {
                if (child instanceof BasicDBObject) {
                    if (nodeCount.get() >= MAX_AREA_CLASSIFICATION_NODES) break; // Pre-check count to prevent unnecessary recursion
                    traverse((BasicDBObject) child, currentPath, result, currentDepth + 1, nodeCount);
                }
            }
        }

        currentPath.setLength(originalLength); // Backtracking: Reset StringBuilder to previous state
    }

    public static List<String> parseAreasClassificationNodesPayload(BasicDBObject payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        
        List<String> areasClassificationNodes = new ArrayList<>();
        AtomicInteger nodeCount = new AtomicInteger(0);
        traverse(payload, new StringBuilder(), areasClassificationNodes, 0, nodeCount);

        if (areasClassificationNodes.isEmpty()) {
            return null;
        }

        return areasClassificationNodes;
    }

    public static BasicDBList parseWorkItemTypeFieldsEndpointPayload(BasicDBList payloadFieldsList,  Map<String, BasicDBObject> organizationFieldsMap, List<String> areasClassificationNodes) {         
        if (payloadFieldsList == null || payloadFieldsList.isEmpty()) {
            return null;
        }

        BasicDBList workItemTypeFieldsList = new BasicDBList();
        for (Object fieldObj: payloadFieldsList) { 
            try {
                if (fieldObj == null) {
                    continue;
                }
                BasicDBObject workTypeFieldDetails = (BasicDBObject) fieldObj;
                
                String fieldReferenceName = workTypeFieldDetails.getString("referenceName");
                if (fieldReferenceName == null || fieldReferenceName.isEmpty()) {
                    continue;
                }

                BasicDBObject organizationFieldDetails = organizationFieldsMap.get(fieldReferenceName);
                if (organizationFieldDetails == null) {
                    continue;
                }

                BasicDBObject combinedFieldDetails = new BasicDBObject();
                combinedFieldDetails.put("fieldReferenceName", fieldReferenceName);
                combinedFieldDetails.put("workItemTypeFieldDetails", workTypeFieldDetails);
                combinedFieldDetails.put("organizationFieldDetails", organizationFieldDetails);

                if ("System.AreaPath".equals(fieldReferenceName)) {
                    combinedFieldDetails.put("areasClassificationNodes", areasClassificationNodes);
                }
                
                workItemTypeFieldsList.add(combinedFieldDetails);
            } catch (Exception e) {
                // continue processing other fields
            }
        }

        return workItemTypeFieldsList;
    }

    public static Map<String, Map<String, BasicDBList>> fetchAccountAzureBoardFields() {
        AzureBoardsIntegration azureBoardsIntegration = AzureBoardsIntegrationDao.instance.findOne(new BasicDBObject());
        if(azureBoardsIntegration == null) {
            loggerMaker.errorAndAddToDb("Azure boards integration not found. Cannot fetch account work items fields.");
            return Collections.emptyMap();
        }

        int accountId = Context.accountId.get();

        /*
         * Fetch work item fields for organization from Azure Boards fields endpoint
         * docs: https://learn.microsoft.com/en-us/rest/api/azure/devops/wit/fields/list?view=azure-devops-rest-7.1&tabs=HTTP
         */
        
        Map<String, BasicDBObject> organizationFieldsMap = new HashMap<>();
    
        try {
            loggerMaker.infoAndAddToDb("Calling Azure Boards fields endpoint to fetch work item fields for organization.");
            BasicDBObject fieldsEndpointPayload;
            fieldsEndpointPayload = callFieldsEndpoint(azureBoardsIntegration);

            if (fieldsEndpointPayload == null) {
                loggerMaker.errorAndAddToDb("Failed to fetch work item fields for organization from Azure Boards.");
                return Collections.emptyMap();
            }

            int count = fieldsEndpointPayload.getInt("count", 0);
            loggerMaker.infoAndAddToDb("Fetched " + count + " work item fields from Azure Boards for organization: " + azureBoardsIntegration.getOrganization());

            BasicDBList fieldsList = (BasicDBList) fieldsEndpointPayload.get("value");    
            if (fieldsList == null || fieldsList.isEmpty()) {
                loggerMaker.errorAndAddToDb("No work item fields found in Azure Boards response.");
                return Collections.emptyMap();
            }

            for (Object fieldObj : fieldsList) {
                try {
                    BasicDBObject field = (BasicDBObject) fieldObj;
                    String fieldReferenceName = field.getString("referenceName");

                    if (fieldReferenceName == null || fieldReferenceName.isEmpty()) {
                        continue;
                    }

                    organizationFieldsMap.put(fieldReferenceName, field);
                } catch (Exception e) {
                    // continue processing other fields
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Exception while fetching work item fields from Azure Boards: " + e.getMessage());
            return Collections.emptyMap();
        }

        Map<String, List<String>> projectToWorkItemsMap = azureBoardsIntegration.getProjectToWorkItemsMap();
        if (projectToWorkItemsMap == null || projectToWorkItemsMap.isEmpty()) {
            loggerMaker.errorAndAddToDb("No projects found in Azure Boards integration.");
            return Collections.emptyMap();
        }
        
        List<Future<Map.Entry<String, Map<String, BasicDBList>>>> futures = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : projectToWorkItemsMap.entrySet()) {
            String projectName = entry.getKey();
            List<String> workItemTypes = entry.getValue();
            
            futures.add(adoPool.submit(() -> {
                Context.accountId.set(accountId);

                List<String> areasClassificationNodes = null;
                try {
                    loggerMaker.infoAndAddToDb("Fetching classification nodes for project: " + projectName);
                    BasicDBObject areasClassificationNodesPayload = callClassificationNodesEndpoint(
                            azureBoardsIntegration, projectName, "Areas", String.valueOf(MAX_AREA_CLASSIFICATION_TREE_DEPTH));
                    loggerMaker.infoAndAddToDb(String.format("Fetched classification nodes from Azure Boards for project: %s", projectName));
                    areasClassificationNodes = parseAreasClassificationNodesPayload(areasClassificationNodesPayload);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Exception in fetching classification nodes for project: " + projectName);
                }

                if (areasClassificationNodes == null) {
                    areasClassificationNodes = new ArrayList<>();
                    areasClassificationNodes.add(projectName);
                }

                // Map of work item type to its fields for a given project
                Map<String, BasicDBList> workItemTypeToFieldsMap = new HashMap<>();

                loggerMaker.infoAndAddToDb("Fetching work item fields for project: " + projectName);
                for (String workItemType : workItemTypes) {
                    loggerMaker.infoAndAddToDb(String.format("Fetching fields for - (%s, %s)", projectName, workItemType));
                    BasicDBList workItemTypeFieldsList = null;

                    try {
                        BasicDBObject workItemTypeFieldsEndpointPayload = callWorkItemTypeFieldsEndpoint(
                                azureBoardsIntegration, projectName, workItemType);
                        int count = workItemTypeFieldsEndpointPayload.getInt("count", 0);
                        loggerMaker.infoAndAddToDb(String.format(
                                "Fetched %d work item fields from Azure Boards for work item type: %s in project: %s",
                                count, workItemType, projectName));
                        BasicDBList payloadFieldsList = (BasicDBList) workItemTypeFieldsEndpointPayload.get("value");
                        workItemTypeFieldsList = parseWorkItemTypeFieldsEndpointPayload(payloadFieldsList, organizationFieldsMap, areasClassificationNodes);    
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e,
                                String.format("Exception while fetching work item fields for - (%s, %s): %s", projectName,
                                        workItemType, e.getMessage()));
                    }
                    workItemTypeToFieldsMap.put(workItemType, workItemTypeFieldsList);
                }
                return new AbstractMap.SimpleEntry<>(projectName, workItemTypeToFieldsMap);
            }));
        }

        // Map of project name to (given project's work item types to their fields)
        Map<String, Map<String, BasicDBList>> createWorkItemFieldMetaData = new HashMap<>();
        // Wait for all tasks to finish and fill the map
        for (Future<Map.Entry<String, Map<String, BasicDBList>>> future : futures) {
            try {
                Map.Entry<String, Map<String, BasicDBList>> entry = future.get(60, TimeUnit.SECONDS);
                if (entry != null) {
                    createWorkItemFieldMetaData.put(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error waiting for Azure boards field metadata fetch task");
            }
        }
        
        return createWorkItemFieldMetaData;
    }
    
    public static Map<String, Map<String, BasicDBList>> getAccountAzureBoardFields() {
        Integer accountId = Context.accountId.get();
        Pair<Map<String, Map<String, BasicDBList>>, Integer> cacheEntry = accountWiseABFields.get(accountId);
        Map<String, Map<String, BasicDBList>> createWorkItemFieldMetaData;

         if (cacheEntry == null || (Context.now() - cacheEntry.getSecond() > EXPIRY_TIME)) {
            createWorkItemFieldMetaData = fetchAccountAzureBoardFields();
            accountWiseABFields.put(accountId, new Pair<>(createWorkItemFieldMetaData, Context.now()));
        } else {
            createWorkItemFieldMetaData = cacheEntry.getFirst();
        }
        
        return createWorkItemFieldMetaData; 
    }
}
