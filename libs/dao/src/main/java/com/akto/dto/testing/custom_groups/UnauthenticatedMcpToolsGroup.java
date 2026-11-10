package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

/**
 * Keeps the "Unauthenticated MCP Tools" group in step with api_info: every tools/call endpoint of an MCP server whose
 * auth types include UNAUTHENTICATED. MCP tools are few, so each run checks all of them: that also catches tools whose
 * auth types were corrected without new traffic.
 */
public class UnauthenticatedMcpToolsGroup {

    private static final Logger logger = LoggerFactory.getLogger(UnauthenticatedMcpToolsGroup.class);

    public static final int GROUP_ID = UnauthenticatedEndpoint.UNAUTHENTICATED_MCP_TOOLS_GROUP_ID;
    public static final String GROUP_NAME = "Unauthenticated MCP Tools";

    private static final String TOOLS_CALL_URL_REGEX = "tools/call/";
    private static final Set<AuthType> UNAUTHENTICATED_AUTH_SET = Collections.singleton(AuthType.UNAUTHENTICATED);
    private static final int BATCH_SIZE = 1000;

    /** MCP server collections, without groups and without collections from the endpoint (developer machine) agent. */
    private static Set<Integer> fetchMcpServerCollectionIds() {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
            Filters.and(
                Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_MCP_SERVER_TAG)),
                Filters.not(Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.and(
                    Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_ENDPOINT_SOURCE_TAG),
                    Filters.eq(CollectionTags.VALUE, Constants.AKTO_ENDPOINT_SOURCE_VALUE)))),
                Filters.ne(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.toString())
            ), Projections.include("_id")
        );
        Set<Integer> ids = new HashSet<>();
        for (ApiCollection apiCollection : apiCollections) {
            ids.add(apiCollection.getId());
        }
        return ids;
    }

    public static boolean hasMcpServers() {
        return !fetchMcpServerCollectionIds().isEmpty();
    }

    public static void updateCollections() {
        Set<Integer> mcpServerCollectionIds = fetchMcpServerCollectionIds();
        Bson filter = Filters.and(
            Filters.in(ApiInfo.ID_API_COLLECTION_ID, mcpServerCollectionIds),
            Filters.regex(ApiInfo.ID_URL, TOOLS_CALL_URL_REGEX));

        List<ApiInfoKey> members = new ArrayList<>();
        try (MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().find(filter)
                .projection(Projections.include("_id", ApiInfo.ALL_AUTH_TYPES_FOUND))
                .batchSize(BATCH_SIZE).cursor()) {
            while (cursor.hasNext()) {
                ApiInfo apiInfo = cursor.next();
                Set<Set<AuthType>> authTypesFound = apiInfo.getAllAuthTypesFound();
                if (authTypesFound != null && authTypesFound.contains(UNAUTHENTICATED_AUTH_SET)) {
                    members.add(apiInfo.getId());
                }
            }
        }

        // add the members, then take out everything else (tools that got auth, or whose collection is no longer an MCP server)
        UnauthenticatedEndpoint endpoint = new UnauthenticatedEndpoint();
        endpoint.setApiInfos(members);
        List<TestingEndpoints> conditions = Collections.singletonList(endpoint);
        ApiCollectionUsers.addToCollectionsForCollectionId(conditions, GROUP_ID);
        ApiCollectionUsers.removeFromCollectionsForCollectionId(conditions, GROUP_ID);
        logger.info(String.format("AccountId: %d unauthenticated mcp tools group synced, members: %d",
                Context.accountId.get(), members.size()));
    }
}
