package com.akto.listener;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.BackwardCompatibility;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.SingleTypeInfo.SubType;
import com.akto.dto.type.URLMethods;
import com.akto.types.CappedSet;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.akto.utils.GithubSync;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestInitializerListener extends MongoBasedTest {

    private static SingleTypeInfo generateSti(String url, int apiCollectionId, boolean isHost) {
        String param = isHost ? "host" : "param#key";
        SubType subType = isHost ? SingleTypeInfo.GENERIC : SingleTypeInfo.EMAIL;
        int responseCode = isHost ? -1 : 200;
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, "GET", responseCode,isHost, param, subType, apiCollectionId, false
        );
        return new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,1000,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
    }

    @Test
    public void testChangesInfo() {
        Context.userId.set(null);
        ApiCollection apiCollection1 = new ApiCollection(0, "coll1", Context.now(), new HashSet<>(), "akto.io", 1, false, true);
        ApiCollection apiCollection2 = new ApiCollection(1, "coll2", Context.now(), new HashSet<>(), "app.akto.io", 2, false, true);
        ApiCollection apiCollection3 = new ApiCollection(2, "coll3", Context.now(), new HashSet<>(), null, 3, false, true);
        ApiCollectionsDao.instance.insertMany(Arrays.asList(apiCollection1, apiCollection2, apiCollection3));

        SingleTypeInfo sti1 = generateSti("/api/books", 0, false);
        SingleTypeInfo sti1h = generateSti("/api/books", 0,true);
        SingleTypeInfo sti2 = generateSti("api/books/INTEGER", 0, false);
        SingleTypeInfo sti2h = generateSti("api/books/INTEGER", 0, true);
        SingleTypeInfo sti3 = generateSti("/api/cars", 1, false);
        SingleTypeInfo sti3h = generateSti("/api/cars", 1, true);
        SingleTypeInfo sti4 = generateSti("/api/toys", 2, false);
        SingleTypeInfo sti5 = generateSti("/api/bus",2, false);

        SingleTypeInfoDao.instance.insertMany(Arrays.asList(sti1, sti2, sti3, sti4, sti5, sti1h, sti2h, sti3h));

        InitializerListener.ChangesInfo changesInfo = InitializerListener.getChangesInfo(Context.now(), Context.now(), null, null, false);
        assertNotNull(changesInfo);
        List<String> newEndpointsLast7Days = changesInfo.newEndpointsLast7Days;
        Map<String, String> newSensitiveParams = changesInfo.newSensitiveParams;

        assertEquals(5,newEndpointsLast7Days.size());

        assertTrue(newEndpointsLast7Days.contains("GET akto.io/api/books"));
        assertTrue(newEndpointsLast7Days.contains("GET akto.io/api/books/INTEGER"));
        assertTrue(newEndpointsLast7Days.contains("GET app.akto.io/api/cars"));
        assertTrue(newEndpointsLast7Days.contains("GET /api/toys"));
        assertTrue(newEndpointsLast7Days.contains("GET /api/bus"));

        assertTrue(newSensitiveParams.containsKey("GET akto.io/api/books: EMAIL"));
        assertTrue(newSensitiveParams.containsKey("GET akto.io/api/books/INTEGER: EMAIL"));
        assertTrue(newSensitiveParams.containsKey("GET app.akto.io/api/cars: EMAIL"));
        assertTrue(newSensitiveParams.containsKey("GET /api/toys: EMAIL"));
        assertTrue(newSensitiveParams.containsKey("GET /api/bus: EMAIL"));
    }

    @Test
    public void testReadAndSaveBurpPluginVersion() {
        assertTrue(InitializerListener.burpPluginVersion < 0);
        InitializerListener initializerListener = new InitializerListener();
        initializerListener.readAndSaveBurpPluginVersion();
        assertTrue(InitializerListener.burpPluginVersion > 0);
    }

    @Test
    public void deleteNullSubCategoryIssues() {
        TestingRunIssuesDao.instance.getMCollection().drop();
        Context.userId.set(null);

        ApiInfo.ApiInfoKey apiInfoKey1 = new ApiInfo.ApiInfoKey(0, "url1", URLMethods.Method.GET);
        TestingRunIssues testingRunIssues1 = new TestingRunIssues(new TestingIssuesId(apiInfoKey1, GlobalEnums.TestErrorSource.AUTOMATED_TESTING,null, "something"), GlobalEnums.Severity.HIGH, GlobalEnums.TestRunIssueStatus.OPEN, 0, 0, new ObjectId(),null, 0);

        ApiInfo.ApiInfoKey apiInfoKey2 = new ApiInfo.ApiInfoKey(0, "url2", URLMethods.Method.GET);
        TestingRunIssues testingRunIssues2 = new TestingRunIssues(new TestingIssuesId(apiInfoKey2, GlobalEnums.TestErrorSource.AUTOMATED_TESTING,"BFLA", null), GlobalEnums.Severity.HIGH, GlobalEnums.TestRunIssueStatus.OPEN, 0, 0, new ObjectId(), null, 0);

        TestingRunIssuesDao.instance.insertMany(Arrays.asList(testingRunIssues1, testingRunIssues2));

        TestingRunIssues testingRunIssues = TestingRunIssuesDao.instance.findOne(Filters.eq("_id.apiInfoKey.url", apiInfoKey1.url));
        assertNotNull(testingRunIssues);

        BackwardCompatibility backwardCompatibility = new BackwardCompatibility();
        InitializerListener.deleteNullSubCategoryIssues(backwardCompatibility);

        testingRunIssues = TestingRunIssuesDao.instance.findOne(Filters.eq("_id.apiInfoKey.url", apiInfoKey1.url));
        assertNull(testingRunIssues);

        testingRunIssues = TestingRunIssuesDao.instance.findOne(Filters.eq("_id.apiInfoKey.url", apiInfoKey2.url));
        assertNotNull(testingRunIssues);

    }
    
    @Test
    public void testSaveTestEditorYaml() {
        YamlTemplateDao.instance.getMCollection().drop();
        long count = YamlTemplateDao.instance.getMCollection().estimatedDocumentCount();
        assertFalse(count > 0);

        GithubSync githubSync = new GithubSync();
        byte[] repoZip = githubSync.syncRepo("akto-api-security/tests-library", "master");

        InitializerListener.processTemplateFilesZip(repoZip, Constants._AKTO, YamlTemplateSource.AKTO_TEMPLATES.toString(), "");

        count = YamlTemplateDao.instance.getMCollection().estimatedDocumentCount();
        assertTrue(count > 0);
    }

}
