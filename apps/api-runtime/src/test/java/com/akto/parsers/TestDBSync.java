package com.akto.parsers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.MongoBasedTest;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.dto.messaging.Message.Mode;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.type.URLMethods.Method;
import com.akto.parsers.HttpCallParser.HttpResponseParams;
import com.akto.parsers.HttpCallParser.HttpResponseParams.Source;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;

import org.junit.Test;

public class TestDBSync extends MongoBasedTest {

    @Test
    public void testMongo() {

        long u = UsersDao.instance.getMCollection().countDocuments();
        UsersDao.instance.insertOne(new User("Abc", "abc@def.gmail", new HashMap<>(), new HashMap<>(), Mode.EMAIL));

        // then
        long v = UsersDao.instance.getMCollection().countDocuments();

        System.out.println("some new print" + u + " " +v);

        assertEquals(u+1, v);

    }    

    @Test
    public void testParameterizedURL() {
        String url = "link/";
        HttpResponseParams resp = TestDump2.createSampleParams("user1", url+1);
        URLAggregator aggr = new URLAggregator();
        ArrayList<String> newHeader = new ArrayList<>();
        newHeader.add("hnew");
        resp.headers.put("new header", newHeader);
        aggr.addURL(resp);
        APICatalogSync sync = new APICatalogSync("access-token", 5);

        for (int i = 2; i <= 30; i ++ ) {
            aggr.addURL(TestDump2.createSampleParams("user"+i, url+i));
        }
        sync.computeDelta(aggr, true, 0);
        sync.syncWithDB(); 
        Map<String, URLMethods> urlMethodsMap = sync.getDelta(0).getStrictURLToMethods();
        assertEquals(0, urlMethodsMap.size());

        Map<URLTemplate, URLMethods> urlTemplateMap = sync.getDelta(0).getTemplateURLToMethods();
        assertEquals(1, urlTemplateMap.size());

        Map.Entry<URLTemplate, URLMethods> entry = urlTemplateMap.entrySet().iterator().next();

        assertEquals(url+"INTEGER", entry.getKey().getTemplateString());
        RequestTemplate reqTemplate = entry.getValue().getMethodToRequestTemplate().get(Method.POST);

        assertEquals(30, reqTemplate.getUserIds().size());
        assertEquals(2, reqTemplate.getParameters().size());
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
        assertEquals(30, respTemplate.getUserIds().size());
        assertEquals(3, respTemplate.getParameters().size());
    }    

    @Test
    public void testImmediateSync() {
        String url = "link/";

        List<HttpResponseParams> responseParams = new ArrayList<>();

        HttpResponseParams resp = TestDump2.createSampleParams("user1", url+1);
        ArrayList<String> newHeader = new ArrayList<>();
        newHeader.add("hnew");
        resp.headers.put("new header", newHeader);
        responseParams.add(resp);

        for (int i = 2; i <= 30; i ++ ) {
            responseParams.add(TestDump2.createSampleParams("user"+i, url+i));
        }

        HttpCallParser parser = new HttpCallParser("access-token", 10,40,10);

        parser.syncFunction(responseParams);
        assertTrue(parser.getSyncCount() == 0);
        
        parser.syncFunction(responseParams);
        assertFalse(parser.getSyncCount() == 0);

        responseParams.get(0).setSource(Source.HAR);
        parser.syncFunction(responseParams);
        assertTrue(parser.getSyncCount() == 0);
    }    
}
