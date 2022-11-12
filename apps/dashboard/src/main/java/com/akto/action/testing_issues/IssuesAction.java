package com.akto.action.testing_issues;

import com.akto.action.UserAction;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.List;

public class IssuesAction extends UserAction {

    private List<TestingRunIssues> issues;
    //todo Fetch top 1000 sorted by latest timestamp
    public String fetchAllIssues() {
        Bson sort = Sorts.descending(TestingRunIssues.CREATION_TIME);
        issues = TestingRunIssuesDao.instance.findAll(new BasicDBObject(),0,1000,sort);
        return SUCCESS.toUpperCase();
    }

    public List<TestingRunIssues> getIssues() {
        return issues;
    }

    public void setIssues(List<TestingRunIssues> issues) {
        this.issues = issues;
    }
}
