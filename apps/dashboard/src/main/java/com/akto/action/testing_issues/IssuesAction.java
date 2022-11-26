package com.akto.action.testing_issues;

import com.akto.action.UserAction;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

import static com.akto.util.Constants.ID;

public class IssuesAction extends UserAction {
    private List<TestingRunIssues> issues;
    private TestingIssuesId issueId;
    private GlobalEnums.TestRunIssueStatus statusToBeUpdated;
    private String ignoreReason;
    private int skip;
    private int limit;
    private long totalIssuesCount;
    public String fetchAllIssues() {
        Bson sort = Sorts.orderBy(Sorts.descending(TestingRunIssues.TEST_RUN_ISSUES_STATUS),
                Sorts.descending(TestingRunIssues.CREATION_TIME));
        Bson filters = new BasicDBObject();
        totalIssuesCount = TestingRunIssuesDao.instance.getMCollection().countDocuments(filters);
        List<TestingRunIssues> listFromDB = TestingRunIssuesDao.instance.findAll(filters, skip,limit, sort);
        List<TestingRunIssues> finalList = new ArrayList<>();
        for (TestingRunIssues issue : listFromDB) {
            if (!existsInFinalList(finalList, issue)) {
                finalList.add(issue);
            }
        }
        issues = finalList;
        return SUCCESS.toUpperCase();
    }

    public String updateIssueStatus () {
        if (issueId == null || statusToBeUpdated == null || ignoreReason == null) {
            throw new IllegalStateException();
        }

        System.out.println("Issue id from db to be updated " + issueId);
        System.out.println("status id from db to be updated " + statusToBeUpdated);
        System.out.println("status reason from db to be updated " + ignoreReason);
        Bson update = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, statusToBeUpdated);

        if (statusToBeUpdated == GlobalEnums.TestRunIssueStatus.IGNORED) { //Changing status to ignored
            update = Updates.combine(update, Updates.set(TestingRunIssues.IGNORE_REASON, ignoreReason));
        } else {
            update = Updates.combine(update, Updates.unset(TestingRunIssues.IGNORE_REASON));
        }
        TestingRunIssues updatedIssue = TestingRunIssuesDao.instance.updateOne(Filters.eq(ID, issueId), update);
        issueId = updatedIssue.getId();
        ignoreReason = updatedIssue.getIgnoreReason();
        statusToBeUpdated = updatedIssue.getTestRunIssueStatus();
        return SUCCESS.toUpperCase();
    }

    private boolean existsInFinalList(List<TestingRunIssues> finalList, TestingRunIssues issue) {
        for (TestingRunIssues finalListIssue : finalList) {
            if (finalListIssue.getId().getApiInfoKey().equals(issue.getId().getApiInfoKey())
                    && finalListIssue.getId().getTestErrorSource().equals(issue.getId().getTestErrorSource())
                    && finalListIssue.getId().getTestSubCategory().getSuperCategory().equals(issue.getId().getTestSubCategory().getSuperCategory())
            ) {

                return true;
            }
        }
        return false;
    }

    public List<TestingRunIssues> getIssues() {
        return issues;
    }

    public void setIssues(List<TestingRunIssues> issues) {
        this.issues = issues;
    }

    public TestingIssuesId getIssueId() {
        return issueId;
    }

    public void setIssueId(TestingIssuesId issueId) {
        this.issueId = issueId;
    }

    public GlobalEnums.TestRunIssueStatus getStatusToBeUpdated() {
        return statusToBeUpdated;
    }

    public void setStatusToBeUpdated(GlobalEnums.TestRunIssueStatus statusToBeUpdated) {
        this.statusToBeUpdated = statusToBeUpdated;
    }

    public String getIgnoreReason() {
        return ignoreReason;
    }

    public void setIgnoreReason(String ignoreReason) {
        this.ignoreReason = ignoreReason;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public long getTotalIssuesCount() {
        return totalIssuesCount;
    }

    public void setTotalIssuesCount(long totalIssuesCount) {
        this.totalIssuesCount = totalIssuesCount;
    }
}
