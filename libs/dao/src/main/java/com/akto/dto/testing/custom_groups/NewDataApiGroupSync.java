package com.akto.dto.testing.custom_groups;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

/**
 * For groups that can only change when new data arrives: reports every api with new data in the time range, as a
 * member. New docs are created with only their own api collection id, so the apis with new single type infos, newly
 * discovered apis and new test issues are the ones that may need the group tag. A group that has a rule for membership
 * can override {@link #scan} and re-decide membership for what this reports.
 */
public abstract class NewDataApiGroupSync extends ApiGroupSync {

    @Override
    protected void scan(int from, int to, Predicate<Candidate> visitor) {
        // new params
        Bson newParams = Filters.and(Filters.gte(SingleTypeInfo._TIMESTAMP, from), Filters.lt(SingleTypeInfo._TIMESTAMP, to));
        if (!scanSingleTypeInfo(newParams, visitor)) {
            return;
        }

        // newly discovered apis
        Bson discovered = Filters.and(Filters.gte(ApiInfo.DISCOVERED_TIMESTAMP, from), Filters.lt(ApiInfo.DISCOVERED_TIMESTAMP, to));
        try (MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().find(discovered)
                .projection(Projections.include("_id", SingleTypeInfo._COLLECTION_IDS)).cursor()) {
            while (cursor.hasNext()) {
                ApiInfo apiInfo = cursor.next();
                List<Integer> groupIds = apiInfo.getCollectionIds();
                if (!visitor.test(new Candidate(apiInfo.getId(), true, groupIds != null && groupIds.contains(groupId())))) {
                    return;
                }
            }
        }

        // new test issues; status is part of the index prefix, so list every status instead of leaving it out
        List<String> allStatuses = Arrays.stream(TestRunIssueStatus.values()).map(Enum::name).collect(Collectors.toList());
        Bson newIssues = Filters.and(
            Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, allStatuses),
            Filters.gte(TestingRunIssues.CREATION_TIME, from),
            Filters.lt(TestingRunIssues.CREATION_TIME, to));
        try (MongoCursor<TestingRunIssues> cursor = TestingRunIssuesDao.instance.getMCollection().find(newIssues)
                .projection(Projections.include("_id", SingleTypeInfo._COLLECTION_IDS)).cursor()) {
            while (cursor.hasNext()) {
                TestingRunIssues issue = cursor.next();
                List<Integer> groupIds = issue.getCollectionIds();
                if (!visitor.test(new Candidate(issue.getId().getApiInfoKey(), true, groupIds != null && groupIds.contains(groupId())))) {
                    return;
                }
            }
        }
    }
}
