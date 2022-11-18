package com.akto.action.testing_issues;

import com.akto.MongoBasedTest;
import com.akto.action.testing_issues.IssuesAction;
import com.akto.dto.test_run_findings.TestingRunIssues;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class IssuesActionTest extends MongoBasedTest {

    @Test
    public void fetchAllIssues () {

        List<TestingRunIssues> list = new ArrayList<>();
        list.add(new TestingRunIssues());

        IssuesAction action = new IssuesAction();
        String success = action.fetchAllIssues();
        action.setIssues(action.getIssues());
        action.setCollections(action.getCollections());
        Assert.assertTrue("success".equalsIgnoreCase(success));
    }
}
