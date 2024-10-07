package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.action.observe.TestInventoryAction;
import com.akto.action.observe.Utils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.CodeAnalysisApiInfoDao;
import com.akto.dao.CodeAnalysisCollectionDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.CodeAnalysisApi;
import com.akto.dto.CodeAnalysisApiInfo;
import com.akto.dto.CodeAnalysisApiLocation;
import com.akto.dto.CodeAnalysisCollection;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import io.jsonwebtoken.lang.Arrays;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.bson.types.Code;
import org.junit.Test;

public class TestCodeAnalysisAction extends MongoBasedTest {

    public void addTrafficApi(int apiCollectionId, String url, URLMethods.Method method) {
        ApiInfo apiInfo = new ApiInfo(apiCollectionId, url, method);
        ApiInfoDao.instance.insertOne(apiInfo);

        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, method.toString(), -1, true, "host", SingleTypeInfo.GENERIC, apiCollectionId, false);
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, 1650287116, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, Long.MAX_VALUE, Long.MIN_VALUE);
        SingleTypeInfoDao.instance.insertOne(singleTypeInfo);
    }
    
    @Test
    public void testSyncExtractedAPIs() {
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        CodeAnalysisCollectionDao.instance.getMCollection().drop();
        CodeAnalysisApiInfoDao.instance.getMCollection().drop();

        int apiCollectionId = 1713174995;
        String apiCollectionName = "code-analysis-test";
        ApiCollection apiCollection = ApiCollection.createManualCollection(apiCollectionId, apiCollectionName);
        ApiCollectionsDao.instance.insertOne(apiCollection);

        addTrafficApi(apiCollectionId, "https://code-analysis-test.com/api/books/INTEGER", URLMethods.Method.GET);
        addTrafficApi(apiCollectionId, "https://code-analysis-test.com/api/books/INTEGER", URLMethods.Method.POST);

        List<CodeAnalysisApi> codeAnalysisApisList = new ArrayList<>();
        CodeAnalysisApi ca1 = new CodeAnalysisApi("GET", "/api/books/STRING", new CodeAnalysisApiLocation(), "{'user': 'String', 'friends': [{'name': 'String', 'age': 'int'}] }", "{'registered': 'boolean'}");
        codeAnalysisApisList.add(ca1);

        CodeAnalysisAction codeAnalysisAction = new CodeAnalysisAction();
        codeAnalysisAction.setApiCollectionName("code-analysis-test");
        codeAnalysisAction.setProjectDir("code-analysis-test");
        codeAnalysisAction.setCodeAnalysisApisList(codeAnalysisApisList);
        codeAnalysisAction.syncExtractedAPIs();

        CodeAnalysisCollection codeAnalysisCollection = CodeAnalysisCollectionDao.instance.findOne(Filters.eq("name", apiCollectionName));
        List<CodeAnalysisApiInfo> codeAnalysisApiInfosList = CodeAnalysisApiInfoDao.instance.findAll(Filters.eq("_id.codeAnalysisCollectionId", codeAnalysisCollection.getId()));
       
        List<String> codeAnalysisApisMapKeyList = new ArrayList<>();
        for(CodeAnalysisApiInfo codeAnalysisApiInfo : codeAnalysisApiInfosList) {
            codeAnalysisApisMapKeyList.add(codeAnalysisApiInfo.generateCodeAnalysisApisMapKey());
        }
        
        // Check (T) GET /api/books/INTEGER | (CA) GET /api/books/STRING -> (CA) GET /api/books/INTEGER
        Boolean checkAktoTemplateMatch = codeAnalysisApisMapKeyList.contains("GET /api/books/INTEGER");
        // Check (T) POST /api/books/INTEGER -> (CA) POST /api/books/INTEGER
        Boolean checkMethodMatching = codeAnalysisApisMapKeyList.contains("POST /api/books/INTEGER");

        assertTrue(checkAktoTemplateMatch);
        assertTrue(checkMethodMatching);
    }
}
