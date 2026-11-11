package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiCollection;
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
 * auth types include UNAUTHENTICATED. The plain unauthenticated group leaves MCP collections out on purpose.
 * The group reuses the UnauthenticatedEndpoint condition; its members come only from this sync.
 */
public class UnauthenticatedMcpToolsGroup {

    public static final int GROUP_ID = UnauthenticatedEndpoint.UNAUTHENTICATED_MCP_TOOLS_GROUP_ID;
    public static final String GROUP_NAME = "Unauthenticated MCP Tools";

    private static final String TOOLS_CALL_URL_REGEX = "tools/call/";
    private static final Set<AuthType> UNAUTHENTICATED_AUTH_SET = Collections.singleton(AuthType.UNAUTHENTICATED);
    private static final int BATCH_SIZE = 1000;

    /**
     * MCP tools are few, so every run checks all of them instead of only the ones seen since the last sync: that also
     * catches tools whose auth types were corrected without new traffic, and tools last seen long ago.
     */
    private static final ApiGroupSync GROUP_SYNC = new ApiGroupSync() {
        @Override
        protected int groupId() {
            return GROUP_ID;
        }

        @Override
        protected String groupName() {
            return "unauthenticated mcp tools group";
        }

        @Override
        protected void scanUntimed(Predicate<Candidate> visitor) {
            Set<Integer> mcpServerCollectionIds = fetchMcpServerCollectionIds();
            // also the current members, so tools whose collection is no longer an MCP server leave the group
            Bson filter = Filters.or(
                Filters.and(
                    Filters.in(ApiInfo.ID_API_COLLECTION_ID, mcpServerCollectionIds),
                    Filters.regex(ApiInfo.ID_URL, TOOLS_CALL_URL_REGEX)),
                Filters.eq(ApiInfo.COLLECTION_IDS, GROUP_ID));

            try (MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().find(filter)
                    .projection(Projections.include("_id", ApiInfo.ALL_AUTH_TYPES_FOUND, ApiInfo.COLLECTION_IDS))
                    .batchSize(BATCH_SIZE).cursor()) {
                while (cursor.hasNext()) {
                    ApiInfo apiInfo = cursor.next();
                    ApiInfoKey key = apiInfo.getId();
                    boolean isMcpTool = mcpServerCollectionIds.contains(key.getApiCollectionId())
                            && key.getUrl().contains(TOOLS_CALL_URL_REGEX);
                    Set<Set<AuthType>> authTypesFound = apiInfo.getAllAuthTypesFound();
                    boolean isUnauthenticated = authTypesFound != null && authTypesFound.contains(UNAUTHENTICATED_AUTH_SET);
                    List<Integer> apiGroupIds = apiInfo.getCollectionIds();
                    boolean inGroup = apiGroupIds != null && apiGroupIds.contains(GROUP_ID);
                    if (!visitor.test(new Candidate(key, isMcpTool && isUnauthenticated, inGroup, 0))) {
                        return;
                    }
                }
            }
        }

        @Override
        protected void scan(int from, int to, Predicate<Candidate> visitor) {
            // scanUntimed already checks every MCP tool on each run
        }

        @Override
        protected TestingEndpoints endpointsFor(List<ApiInfoKey> keys) {
            UnauthenticatedEndpoint endpoint = new UnauthenticatedEndpoint();
            endpoint.setApiInfos(new ArrayList<>(keys));
            return endpoint;
        }
    };

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
        GROUP_SYNC.run();
    }
}
