package com.akto.action.testing_issues;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.URLMethods;
import com.akto.util.enums.GlobalEnums;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

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

        ApiCollection collection = new ApiCollection(
                123,
                "name",
                Context.now(),
                new HashSet<>(Arrays.asList("url1", "url2")),
                "hostName",
                12345
        );

        TestingRunIssuesDao.instance.insertOne(issue);
        ApiCollectionsDao.instance.insertOne(collection);
        IssuesAction action = new IssuesAction();
        action.fetchAllIssues();
        assertEquals(issue.getId(), action.getIssues().get(0).getId());
        assertEquals(collection.getId(), action.getCollections().get(0).getId());

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
        collection = new ApiCollection(
                1234,
                "name",
                Context.now(),
                new HashSet<>(Arrays.asList("url1", "url2")),
                "hostName",
                12345
        );

        ArrayList<ApiCollection> listOfCollection = new ArrayList<>();
        listOfCollection.add(collection);
        action.setCollections(listOfCollection);

        assertEquals(issue.getId(), action.getIssues().get(0).getId());
        assertEquals(collection.getId(), action.getCollections().get(0).getId());
    }
}
