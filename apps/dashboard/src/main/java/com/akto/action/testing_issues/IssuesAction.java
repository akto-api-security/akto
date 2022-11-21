package com.akto.action.testing_issues;

import com.akto.action.UserAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class IssuesAction extends UserAction {

    private List<TestingRunIssues> issues;
    private List<ApiCollection> collections;

    public String fetchAllIssues() {
        Bson sort = Sorts.descending(TestingRunIssues.CREATION_TIME);
        List<TestingRunIssues> listFromDB = TestingRunIssuesDao.instance.findAll(new BasicDBObject(), 0, 1000, sort);
        List<TestingRunIssues> finalList = new ArrayList<>();
        for (TestingRunIssues issue : listFromDB) {
            if (!existsInFinalList(finalList, issue)) {
                finalList.add(issue);
            }
        }
        issues = finalList;
        collections = ApiCollectionsDao.instance.getMetaAll();
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

    public List<ApiCollection> getCollections() {
        return collections;
    }

    public void setCollections(List<ApiCollection> collections) {
        this.collections = collections;
    }
}
