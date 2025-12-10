package com.akto.utils.api_audit_logs;

import java.util.List;
import java.util.Map;

import com.akto.action.ApiCollectionsAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.CollectionConditions.ConditionUtils;

public class InventoryAuditGenerators {
    
    public static String deactivateCollections(ApiCollectionsAction action) {
        List<ApiCollection> apiCollections = action.getApiCollections();
        return String.format("User deactivated %d API collection(s): %s",
                ActionAuditGeneratorUtils.getItemsCount(apiCollections),
                ActionAuditGeneratorUtils.getItemsCS(apiCollections, ApiCollection::getDisplayName));
    }

    public static String activateCollections(ApiCollectionsAction action) {
        List<ApiCollection> apiCollections = action.getApiCollections();
        return String.format("User activated %d API collection(s): %s",
                ActionAuditGeneratorUtils.getItemsCount(apiCollections),
                ActionAuditGeneratorUtils.getItemsCS(apiCollections, ApiCollection::getDisplayName));
    }

    public static String deleteMultipleCollections(ApiCollectionsAction action) {
        List<ApiCollection> apiCollections = action.getApiCollections();
        return String.format("User deleted %d API collection(s): %s",
                ActionAuditGeneratorUtils.getItemsCount(apiCollections),
                ActionAuditGeneratorUtils.getItemsCS(apiCollections, ApiCollection::getDisplayName));
    }

    public static String updateUserCollections(ApiCollectionsAction action) {
        // todo:
        Map<String, List<Integer>> getUserCollectionMap = action.getUserCollectionMap();
        
        for (Map.Entry<String, List<Integer>> entry : getUserCollectionMap.entrySet()) {
            String operation = entry.getKey();
            List<Integer> collectionIds = entry.getValue();
            List<ApiCollection> apiCollections = ApiCollectionsDao.instance.getMetaForIds(collectionIds);
            
        }

        return String.format("");
    }

    public static String toggleCollectionsOutOfTestScope(ApiCollectionsAction action) {
        // todo: check read write access type

        boolean currentIsOutOfTestingScopeVal = action.getCurrentIsOutOfTestingScopeVal();
        String updatedState = !currentIsOutOfTestingScopeVal ? "out of testing scope" : "in testing scope";

        List<Integer> apiCollectionIds = action.getApiCollectionIds();
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.getMetaForIds(apiCollectionIds);

        return String.format("User marked %d API collection(s) as %s: %s",
                ActionAuditGeneratorUtils.getItemsCount(apiCollections),
                updatedState,
                ActionAuditGeneratorUtils.getItemsCS(apiCollections, ApiCollection::getDisplayName));
    }

    public static String updateEnvType(ApiCollectionsAction action) {
        String actionAuditDescription = null;
        List<Integer> apiCollectionIds = action.getApiCollectionIds();
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.getMetaForIds(apiCollectionIds);

        boolean resetEnvTypes = action.getResetEnvTypes();

        if (resetEnvTypes) {
            actionAuditDescription = String.format("User reset tags for %d API collection(s): %s",
                ActionAuditGeneratorUtils.getItemsCount(apiCollections),
                ActionAuditGeneratorUtils.getItemsCS(apiCollections, ApiCollection::getDisplayName));
        } else {
            actionAuditDescription = String.format("User updated tags for %d API collection(s): %s",
                ActionAuditGeneratorUtils.getItemsCount(apiCollections),
                ActionAuditGeneratorUtils.getItemsCS(apiCollections, ApiCollection::getDisplayName));
        }

        return actionAuditDescription;
    }

    public static String createCollection(ApiCollectionsAction action) {
        String collectionName = action.getCollectionName();
        return String.format("User created a new API collection %s", collectionName);
    }

    public static String createCustomCollection(ApiCollectionsAction action) {
        String collectionName = action.getCollectionName();
        List<ConditionUtils> conditions = action.getConditions();

        return String.format("User created a new API collection %s with %d condition(s): %s",
                collectionName,
                ActionAuditGeneratorUtils.getItemsCount(conditions),
                ActionAuditGeneratorUtils.getItemsCSFromEnum(
                        conditions,
                        ConditionUtils::getType));
    }

    public static String updateCustomCollection(ApiCollectionsAction action) {
        int apiCollectionId = action.getApiCollectionId();
        ApiCollection apiCollection = ApiCollectionsDao.instance.getMetaForId(apiCollectionId);
        String collectionName = apiCollection.getDisplayName();

        List<ConditionUtils> conditions = action.getConditions();

        return String.format("User updated API collection %s with %d condition(s): %s",
                collectionName,
                ActionAuditGeneratorUtils.getItemsCount(conditions),
                ActionAuditGeneratorUtils.getItemsCSFromEnum(
                        conditions,
                        ConditionUtils::getType));
    }
}
