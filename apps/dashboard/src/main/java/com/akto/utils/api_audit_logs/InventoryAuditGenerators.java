package com.akto.utils.api_audit_logs;

import java.util.List;

import com.akto.action.ApiCollectionsAction;
import com.akto.action.CustomDataTypeAction;
import com.akto.action.HarAction;
import com.akto.action.OpenApiAction;
import com.akto.action.PostmanAction;
import com.akto.action.observe.InventoryAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.pii.PIISourceDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CollectionConditions.ConditionUtils;
import com.akto.dto.pii.PIISource;

import static com.akto.utils.api_audit_logs.ApiAuditLogsUtils.register;

public class InventoryAuditGenerators {

    public static void addToRegistry() {
        // Audit generators for API collections screen actions
        register("api/deactivateCollections", ApiCollectionsAction.class, InventoryAuditGenerators::deactivateCollections);
        register("api/activateCollections", ApiCollectionsAction.class, InventoryAuditGenerators::activateCollections);
        register("api/deleteMultipleCollections", ApiCollectionsAction.class, InventoryAuditGenerators::deleteMultipleCollections);
        register("api/toggleCollectionsOutOfTestScope", ApiCollectionsAction.class, InventoryAuditGenerators::toggleCollectionsOutOfTestScope);
        register("api/updateEnvType", ApiCollectionsAction.class, InventoryAuditGenerators::updateEnvType);
        register("api/createCollection", ApiCollectionsAction.class, InventoryAuditGenerators::createCollection);
        register("api/createCustomCollection", ApiCollectionsAction.class, InventoryAuditGenerators::createCustomCollection);
        register("api/updateCustomCollection", ApiCollectionsAction.class, InventoryAuditGenerators::updateCustomCollection);

        // Audit generators for Sensitive Data screen actions
        register("api/fillSensitiveDataTypes", CustomDataTypeAction.class, InventoryAuditGenerators::fillSensitiveDataTypes);
        register("api/saveCustomDataType", CustomDataTypeAction.class, InventoryAuditGenerators::saveCustomDataType);
        register("api/saveAktoDataType", CustomDataTypeAction.class, InventoryAuditGenerators::saveAktoDataType);
        register("api/resetDataTypeRetro", CustomDataTypeAction.class, InventoryAuditGenerators::resetDataTypeRetro);

        // Audit generators for API endpoints screen actions
        register("api/computeCustomCollections", ApiCollectionsAction.class, InventoryAuditGenerators::computeCustomCollections);
        register("api/redactCollection", ApiCollectionsAction.class, InventoryAuditGenerators::redactCollection);
        register("api/saveCollectionDescription", ApiCollectionsAction.class, InventoryAuditGenerators::saveCollectionDescription);
        register("api/addApisToCustomCollection", ApiCollectionsAction.class, InventoryAuditGenerators::addApisToCustomCollection);
        register("api/removeApisFromCustomCollection", ApiCollectionsAction.class, InventoryAuditGenerators::removeApisFromCustomCollection);
        
        register("api/deMergeApi", InventoryAction.class, InventoryAuditGenerators::deMergeApi);
        register("api/saveEndpointDescription", InventoryAction.class, InventoryAuditGenerators::saveEndpointDescription);

        register("api/uploadHar", HarAction.class, InventoryAuditGenerators::uploadHar);
        register("api/importDataFromOpenApiSpec", OpenApiAction.class, InventoryAuditGenerators::importDataFromOpenApiSpec);
        register("api/generateOpenApiFile", OpenApiAction.class, InventoryAuditGenerators::generateOpenApiFile);
        register("api/createPostmanApi", PostmanAction.class, InventoryAuditGenerators::createPostmanApi);
    }
    
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

        return String.format("User deleted %d API collection(s)", ActionAuditGeneratorUtils.getItemsCount(apiCollections));
    }


    public static String toggleCollectionsOutOfTestScope(ApiCollectionsAction action) {
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
        return String.format("User created a new API collection: %s", collectionName);
    }

    public static String createCustomCollection(ApiCollectionsAction action) {
        String collectionName = action.getCollectionName();
        List<ConditionUtils> conditions = action.getConditions();

        return String.format("User created a new custom API collection %s with %d condition(s): %s",
                collectionName,
                ActionAuditGeneratorUtils.getItemsCount(conditions),
                ActionAuditGeneratorUtils.getItemsCSFromEnum(
                        conditions,
                        ConditionUtils::getType));
    }

    public static String updateCustomCollection(ApiCollectionsAction action) {
        int apiCollectionId = action.getApiCollectionId();
        List<ConditionUtils> conditions = action.getConditions();
        String collectionName = ActionAuditGeneratorUtils.getApiCollectionDisplayName(apiCollectionId);
        
        return String.format("User updated custom API collection %s with %d condition(s): %s",
                collectionName,
                ActionAuditGeneratorUtils.getItemsCount(conditions),
                ActionAuditGeneratorUtils.getItemsCSFromEnum(
                        conditions,
                        ConditionUtils::getType));
    }

    public static String fillSensitiveDataTypes(CustomDataTypeAction action) {
        List<PIISource> piiSources = PIISourceDao.instance.findAll("active", true);

        return String.format("User triggered filling of sensitive data type(s) from sources: %s",
                ActionAuditGeneratorUtils.getItemsCS(piiSources, PIISource::getId));
    }

    public static String saveCustomDataType(CustomDataTypeAction action) {
        boolean createNew = action.getCreateNew();
        String name = action.getName();

        if (createNew) {
            return String.format("User created a new custom sensitive data type: %s", name);
        } else {
            return String.format("User updated custom sensitive data type: %s", name);
        }
    }

    public static String saveAktoDataType(CustomDataTypeAction action) {
        String name = action.getName();

        return String.format("User updated akto sensitive data type: %s", name);
    }

    public static String resetDataTypeRetro(CustomDataTypeAction action) {
        String name = action.getName();

        return String.format("User reset sensitive data type: %s", name);
    }

    public static String computeCustomCollections(ApiCollectionsAction action) {
        String collectionName = action.getCollectionName();
        return String.format("User re-computed custom API collection: %s", collectionName);
    }

    public static String redactCollection(ApiCollectionsAction action) {
        int apiCollectionId = action.getApiCollectionId();
        String collectionName = ActionAuditGeneratorUtils.getApiCollectionDisplayName(apiCollectionId);

        boolean isRedacted = action.isRedacted();

        if (isRedacted) {
            return String.format("User enabled redaction of sample data values for API collection: %s", collectionName);
        } else {
            return String.format("User disabled redaction of sample data values for API collection: %s", collectionName);
        }
    }

    public static String saveCollectionDescription(ApiCollectionsAction action) {
        int apiCollectionId = action.getApiCollectionId();
        String collectionName = ActionAuditGeneratorUtils.getApiCollectionDisplayName(apiCollectionId);

        return String.format("User edited description for API collection: %s", collectionName);
    }

    public static String addApisToCustomCollection(ApiCollectionsAction action) {
        String collectionName = action.getCollectionName();
        List<ApiInfoKey> apisList = action.getApiList();

        return String.format("User added %d APIs to custom API collection: %s", 
            ActionAuditGeneratorUtils.getItemsCount(apisList),
            collectionName);
    }

    public static String removeApisFromCustomCollection(ApiCollectionsAction action) {
        String collectionName = action.getCollectionName();
        List<ApiInfoKey> apisList = action.getApiList();

        return String.format("User removed %d APIs to custom API collection: %s", 
            ActionAuditGeneratorUtils.getItemsCount(apisList),
            collectionName);
    }

    public static String uploadHar(HarAction action) {
        int apiCollectionId = action.getApiCollectionId();
        String collectionName = ActionAuditGeneratorUtils.getApiCollectionDisplayName(apiCollectionId);

        return String.format("User uploaded HAR file for API collection: %s", collectionName);
    }

    public static String importDataFromOpenApiSpec(OpenApiAction action) {
        int apiCollectionId = action.getApiCollectionId();
        String collectionName = ActionAuditGeneratorUtils.getApiCollectionDisplayName(apiCollectionId);

        return String.format("User uploaded OpenAPI specification file for API collection: %s", collectionName);
    }

    public static String generateOpenApiFile(OpenApiAction action) {
        int apiCollectionId = action.getApiCollectionId();
        String collectionName = ActionAuditGeneratorUtils.getApiCollectionDisplayName(apiCollectionId);

        return String.format("User generated OpenAPI specification file for API collection: %s", collectionName);
    }

    public static String createPostmanApi(PostmanAction action) {
        int apiCollectionId = action.getApiCollectionId();
        String collectionName = ActionAuditGeneratorUtils.getApiCollectionDisplayName(apiCollectionId);

        return String.format("User created Postman APIs for API collection: %s", collectionName);
    }

    public static String deMergeApi(InventoryAction action) {
        int apiCollectionId = action.getApiCollectionId();
        String collectionName = ActionAuditGeneratorUtils.getApiCollectionDisplayName(apiCollectionId);
        String method = action.getMethod();
        String url = action.getUrl();

        return String.format("User de-merged the following API (collection | method | url): ( %s | %s | %s )", collectionName, method, url);
    }

    public static String saveEndpointDescription(InventoryAction action) {
        int apiCollectionId = action.getApiCollectionId();
        String collectionName = ActionAuditGeneratorUtils.getApiCollectionDisplayName(apiCollectionId);
        String method = action.getMethod();
        String url = action.getUrl();

        return String.format("User updated description for following API (collection | method | url): ( %s | %s | %s )", collectionName, method, url);
    }
}
