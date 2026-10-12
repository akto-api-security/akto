package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.client.model.Filters;

public class AllAPIsGroup extends TestingEndpoints {

    /**
     * When set, the group is only (re)computed for these api collections. Lets crons add the group id to just the
     * collections that received new data, so the update can use the apiCollectionId indexes instead of scanning
     * every document with a $nin on collectionIds. Never persisted.
     */
    @BsonIgnore
    private List<Integer> scopeApiCollectionIds;

    public AllAPIsGroup() {
        super(Type.ALL, Operator.OR);
    }

    public AllAPIsGroup(List<Integer> scopeApiCollectionIds) {
        this();
        this.scopeApiCollectionIds = scopeApiCollectionIds;
    }

    @Override
    public List<ApiInfoKey> returnApis() {
        throw new UnsupportedOperationException("Unimplemented method 'returnApis'");
    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        return true;
    }

    public final static int ALL_APIS_GROUP_ID = 111_111_121;

    @Override
    public Bson createFilters(CollectionType type) {
        if (scopeApiCollectionIds == null) {
            return Filters.empty();
        }
        return Filters.in(getFilterPrefix(type) + SingleTypeInfo._API_COLLECTION_ID, scopeApiCollectionIds);
    }

    /**
     * Adds the group id to everything that received new data since the given timestamp. Anything older was already
     * covered by an earlier run, and new docs are only ever created under their own api collection.
     */
    public static void updateCollections(int since) {
        Set<Integer> apiCollectionIds = new HashSet<>();

        SingleTypeInfoDao.instance.getMCollection()
                .distinct(SingleTypeInfo._API_COLLECTION_ID, Filters.gte(SingleTypeInfo._TIMESTAMP, since), Integer.class)
                .into(apiCollectionIds);

        ApiInfoDao.instance.getMCollection()
                .distinct(ApiInfo.ID_API_COLLECTION_ID, Filters.gte(ApiInfo.DISCOVERED_TIMESTAMP, since), Integer.class)
                .into(apiCollectionIds);

        // status is part of the index prefix, so list every status instead of leaving it out
        List<String> allStatuses = Arrays.stream(TestRunIssueStatus.values()).map(Enum::name).collect(Collectors.toList());
        TestingRunIssuesDao.instance.getMCollection()
                .distinct("_id.apiInfoKey." + ApiInfoKey.API_COLLECTION_ID, Filters.and(
                        Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, allStatuses),
                        Filters.gte(TestingRunIssues.CREATION_TIME, since)
                ), Integer.class)
                .into(apiCollectionIds);

        if (apiCollectionIds.isEmpty()) {
            return;
        }
        AllAPIsGroup scopedGroup = new AllAPIsGroup(new ArrayList<>(apiCollectionIds));
        ApiCollectionUsers.addToCollectionsForCollectionId(Collections.singletonList(scopedGroup), ALL_APIS_GROUP_ID);
    }
}
