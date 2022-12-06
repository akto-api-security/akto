package com.akto.action.testing_issues;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing_issues.TestingRunIssuesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_issues.TestingIssuesId;
import com.akto.dto.test_issues.TestingRunIssues;
import com.akto.dto.type.URLMethods;
import com.akto.util.enums.GlobalEnums;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class IssuesActionTest extends MongoBasedTest {

    @Test
    public void fetchAllIssues() {

        TestingRunIssues issue = new TestingRunIssues(
                new TestingIssuesId(
                        new ApiInfo.ApiInfoKey(
                                123,
                                "url",
                                URLMethods.Method.POST
                        ),
                        GlobalEnums.TestErrorSource.AUTOMATED_TESTING,
                        GlobalEnums.TestSubCategory.ADD_METHOD_IN_PARAMETER
                ),
                GlobalEnums.Severity.HIGH,
                GlobalEnums.TestRunIssueStatus.OPEN,
                Context.now(),
                Context.now(),
                new ObjectId()
        );

        TestingRunIssuesDao.instance.insertOne(issue);
        IssuesAction action = new IssuesAction();
        action.fetchAllIssues();
        assertEquals(issue.getId(), action.getIssues().get(0).getId());

        issue = new TestingRunIssues(
                new TestingIssuesId(
                        new ApiInfo.ApiInfoKey(
                                1234,
                                "url",
                                URLMethods.Method.POST
                        ),
                        GlobalEnums.TestErrorSource.AUTOMATED_TESTING,
                        GlobalEnums.TestSubCategory.ADD_METHOD_IN_PARAMETER
                ),
                GlobalEnums.Severity.HIGH,
                GlobalEnums.TestRunIssueStatus.OPEN,
                Context.now(),
                Context.now(),
                new ObjectId()
        );

        ArrayList<TestingRunIssues> list = new ArrayList<>();
        list.add(issue);
        action.setIssues(list);

        assertEquals(issue.getId(), action.getIssues().get(0).getId());
    }
}
