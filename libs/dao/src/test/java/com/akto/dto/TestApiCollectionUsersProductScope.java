package com.akto.dto;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.custom_groups.AllAPIsGroup;
import com.akto.dto.testing.custom_groups.UnauthenticatedEndpoint;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.URLMethods.Method;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

public class TestApiCollectionUsersProductScope extends MongoBasedTest {

    private static final int API_COLLECTION = 1001;
    private static final int ARGUS_COLLECTION = 1002;
    private static final int API_GROUP = 111_222_001;

    private final ApiInfoKey apiKey = new ApiInfoKey(API_COLLECTION, "/api/admin/users", Method.GET);
    private final ApiInfoKey argusKey = new ApiInfoKey(ARGUS_COLLECTION, "/mcp/admin/tools", Method.POST);

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();

        ApiCollectionsDao.instance.insertOne(new ApiCollection(API_COLLECTION, "api.example.com", 0, null, null, 0, false, false));

        ApiCollection argus = new ApiCollection(ARGUS_COLLECTION, "argus.example.com", 0, null, null, 0, false, false);
        argus.setTagsList(Collections.singletonList(
            new CollectionTags(0, Constants.AKTO_MCP_SERVER_TAG, "true", CollectionTags.TagSource.AKTO)));
        ApiCollectionsDao.instance.insertOne(argus);

        ApiCollection group = new ApiCollection(API_GROUP, "Admin APIs", 0, null, null, 0, false, false);
        group.setType(ApiCollection.Type.API_GROUP);
        group.setAutomated(true);
        ApiCollectionsDao.instance.insertOne(group);

        // Untagged like API Security groups, but also part of the MCP / Agentic scope
        ApiCollection mcpToolsGroup = new ApiCollection(UnauthenticatedEndpoint.UNAUTHENTICATED_MCP_TOOLS_GROUP_ID,
            "Unauthenticated MCP Tools", 0, null, null, 0, false, false);
        mcpToolsGroup.setType(ApiCollection.Type.API_GROUP);
        mcpToolsGroup.setAutomated(true);
        ApiCollectionsDao.instance.insertOne(mcpToolsGroup);
    }

    private List<Integer> collectionIdsOf(ApiInfoKey key) {
        ApiInfo apiInfo = ApiInfoDao.instance.getMCollection().find(ApiInfoDao.getFilter(key)).first();
        return new ArrayList<>(apiInfo.getCollectionIds());
    }

    @Test
    public void addSkipsApisOwnedOutsideApiSecurity() throws Exception {
        ApiInfoDao.instance.insertMany(Arrays.asList(new ApiInfo(apiKey), new ApiInfo(argusKey)));

        List<TestingEndpoints> conditions = Collections.singletonList(
            new CustomTestingEndpoints(Arrays.asList(apiKey, argusKey)));
        ApiCollectionUsers.addToCollectionsForCollectionId(conditions, API_GROUP);
        Thread.sleep(2000); // membership is written on a background executor

        assertEquals(Arrays.asList(API_COLLECTION, API_GROUP), collectionIdsOf(apiKey));
        assertEquals(Collections.singletonList(ARGUS_COLLECTION), collectionIdsOf(argusKey));
    }

    @Test
    public void pruneRemovesApiSecurityGroupsFromArgusApisOnly() {
        ApiInfo api = new ApiInfo(apiKey);
        api.setCollectionIds(Arrays.asList(API_COLLECTION, API_GROUP, AllAPIsGroup.ALL_APIS_GROUP_ID));
        ApiInfo argusApi = new ApiInfo(argusKey);
        argusApi.setCollectionIds(Arrays.asList(ARGUS_COLLECTION, API_GROUP, AllAPIsGroup.ALL_APIS_GROUP_ID,
            UnauthenticatedEndpoint.UNAUTHENTICATED_MCP_TOOLS_GROUP_ID));
        ApiInfoDao.instance.insertMany(Arrays.asList(api, argusApi));

        ApiCollectionUsers.removeNonApiSecurityApisFromApiSecurityGroups();

        assertEquals(Arrays.asList(API_COLLECTION, API_GROUP, AllAPIsGroup.ALL_APIS_GROUP_ID), collectionIdsOf(apiKey));
        assertEquals(Arrays.asList(ARGUS_COLLECTION, AllAPIsGroup.ALL_APIS_GROUP_ID,
            UnauthenticatedEndpoint.UNAUTHENTICATED_MCP_TOOLS_GROUP_ID), collectionIdsOf(argusKey));
        assertEquals(2, ApiInfoDao.instance.getMCollection().countDocuments(new BasicDBObject()));
        assertEquals(0, ApiInfoDao.instance.getMCollection().countDocuments(
            Filters.and(Filters.eq(ApiInfo.ID_API_COLLECTION_ID, ARGUS_COLLECTION), Filters.in("collectionIds", API_GROUP))));
    }
}
