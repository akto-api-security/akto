package com.akto.testing_issues;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import org.junit.Test;

import java.util.List;

public class TestingIssuesHandlerTest {
//    @Test
//    public void testHandler () {
//
//        String mongoURI = System.getenv("AKTO_MONGO_CONN");
//        DaoInit.init(new ConnectionString(mongoURI));
//        Context.accountId.set(1_000_000);
//
//        List<TestingRunIssues> list = TestingRunIssuesDao.instance.findAll(new BasicDBObject());
//        System.out.println("asdf");
//    }
}
