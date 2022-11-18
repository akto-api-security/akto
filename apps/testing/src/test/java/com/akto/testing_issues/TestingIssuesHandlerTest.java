package com.akto.testing_issues;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static com.akto.util.Constants.ID;

public class TestingIssuesHandlerTest extends MongoBasedTest {

    private static int COLLECTION_ID = 123;
    private static String COLLECTION_NAME = "Testing_issue_collection";
    private static String[] urls = new String[]{
            "url1"
    };

    private void insertIgnoredKey () {
        TestingRunIssues issues = TestingRunIssuesDao.instance.findOne(new BasicDBObject());
        issues.setTestRunIssueStatus(GlobalEnums.TestRunIssueStatus.IGNORED);
        TestingRunIssuesDao.instance.replaceOne(new BasicDBObject(ID, issues.getId()),issues);
    }

    private void insertNotVulnerable () {
    }

    private int getIndex (int length, Random random) {
        return Math.abs(random.nextInt()) % length;
    }
    private TestingRunResult getTestingRunResult (ApiInfo.ApiInfoKey apiInfoKey, String testSuperType, Random random) {
        List<ObjectId> ids = new ArrayList<>();
        ids.add(new ObjectId(new Date(System.currentTimeMillis())));
        ids.add(new ObjectId(new Date(System.currentTimeMillis() - 1000 * 60 * 60)));
        ids.add(new ObjectId(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 2)));
        ObjectId summaryId = new ObjectId();

        List<TestResult> results = new ArrayList<>();
        List<SingleTypeInfo> singleTypeInfosList = new ArrayList<>();
        return new TestingRunResult(ids.get(getIndex(ids.size(),random)),
                apiInfoKey,
                testSuperType,
                "",
                results,
                true,
                singleTypeInfosList,
                100,
                Context.now(),
                Context.now(),
                summaryId);
    }

    @Test
    public void testHandler() {
        List<TestingRunResult> testingRunResultList = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 100; i++) {
            testingRunResultList.add(getTestingRunResult(new ApiInfo.ApiInfoKey(COLLECTION_ID, urls[getIndex(urls.length,random)],
                            URLMethods.Method.values[getIndex(URLMethods.Method.values.length,random)]),
                    GlobalEnums.TestCategory.values()[getIndex(GlobalEnums.TestCategory.values.length, random)].getName(), random));
        }

        TestingIssuesHandler.handleIssuesCreationFromTestingRunResults(testingRunResultList);
        insertIgnoredKey();
        TestingIssuesHandler.handleIssuesCreationFromTestingRunResults(testingRunResultList);
        TestingRunResult runResult = testingRunResultList.get(5);
        runResult.setVulnerable(false);
        TestingIssuesHandler.handleIssuesCreationFromTestingRunResults(testingRunResultList);
        //When all said and done, total issue can't be more than 36

        int size = TestingRunIssuesDao.instance.findAll(new BasicDBObject()).size();
        Assert.assertTrue(size <= 36);

    }
}
