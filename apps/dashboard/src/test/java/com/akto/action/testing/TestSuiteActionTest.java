package com.akto.action.testing;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.testing.config.TestSuiteDao;
import com.akto.dto.User;
import com.akto.dto.testing.config.TestSuite;
import com.mongodb.client.model.Filters;

public class TestSuiteActionTest extends MongoBasedTest {
    public void SetupTestSuiteAction(TestSuiteAction action) {
        String userEmail = "test@akto.io";
        User user = new User();

        user.setLogin(userEmail);
        
        Map<String, Object> session = new HashMap<>();
        session.put("user", user);
        action.setSession(session);
    }
    
    @Test
    public void testCreateTestSuite() {
        TestSuiteAction action = new TestSuiteAction();
        SetupTestSuiteAction(action);
        action.setTestSuiteName("sampleName");
        List<String> subCategoryList = new ArrayList<>(Arrays.asList("Test1", "Test2", "Test3", "Test4"));


        action.setSubCategoryList(subCategoryList);
        assertEquals("SUCCESS", action.createTestSuite());
        TestSuite newTestSuite = TestSuiteDao.instance.findOne(Filters.eq(TestSuite.FIELD_ID, action.getTestSuiteId()));
        assertEquals("sampleName", newTestSuite.getName());
        assertEquals(subCategoryList, newTestSuite.getSubCategoryList());

        action.setTestSuiteName(null);
        assertEquals("ERROR", action.createTestSuite());
        action.setTestSuiteName("");
        assertEquals("ERROR", action.createTestSuite());
        action.setTestSuiteName("   ");
        assertEquals("ERROR", action.createTestSuite());
        action.setSubCategoryList(null);
        assertEquals("ERROR", action.createTestSuite());

        action.setSession(new HashMap<>()); 
        action.setTestSuiteName("Valid Name");
        action.setSubCategoryList(subCategoryList);
        assertEquals("ERROR", action.createTestSuite());

    } 

    @Test
    public void testModifyTestSuite(){
        TestSuiteAction action = new TestSuiteAction();
        SetupTestSuiteAction(action);
        action.setTestSuiteName("sampleName");
        List<String> subCategoryList = new ArrayList<>(Arrays.asList("Test1", "Test2", "Test3", "Test4"));


        action.setSubCategoryList(subCategoryList);
        action.createTestSuite();

        action.setTestSuiteName("sampleName2");
        assertEquals("SUCCESS", action.modifyTestSuite());

        subCategoryList.add("Test5");
        action.setSubCategoryList(subCategoryList);
        assertEquals("SUCCESS", action.modifyTestSuite());

        TestSuite newTestSuite = TestSuiteDao.instance.findOne(Filters.eq(TestSuite.FIELD_ID, action.getTestSuiteId()));
        assertEquals("sampleName2", newTestSuite.getName());
        assertEquals(subCategoryList, newTestSuite.getSubCategoryList());

        action.setTestSuiteName(null);
        assertEquals("ERROR", action.modifyTestSuite());
        action.setTestSuiteName("");
        assertEquals("ERROR", action.modifyTestSuite());
        action.setTestSuiteName("   ");
        assertEquals("ERROR", action.modifyTestSuite());
        action.setSubCategoryList(null);
        assertEquals("ERROR", action.modifyTestSuite());
        action.setTestSuiteId(-1);
        assertEquals("ERROR", action.modifyTestSuite());

        action.setTestSuiteId(4234234);
        assertEquals("ERROR", action.modifyTestSuite());
    }

    @Test
    public void testDeleteTestSuite(){
        TestSuiteAction action = new TestSuiteAction();
        SetupTestSuiteAction(action);
        action.setTestSuiteName("sampleName");
        List<String> subCategoryList = new ArrayList<>(Arrays.asList("Test1", "Test2", "Test3", "Test4"));
        action.setSubCategoryList(subCategoryList);
        action.createTestSuite();
        int testSuiteId = action.getTestSuiteId();
        assertEquals("SUCCESS", action.deleteTestSuite());
        assertEquals(null, TestSuiteDao.instance.findOne(Filters.eq(TestSuite.FIELD_ID, testSuiteId)));
        action.setTestSuiteId(-1);
        assertEquals("ERROR", action.deleteTestSuite());
    }
}
