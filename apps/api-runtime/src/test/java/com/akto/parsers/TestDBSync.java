package com.akto.parsers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.MongoBasedTest;
import com.akto.dao.RuntimeFilterDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.dto.messaging.Message.Mode;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import org.bson.conversions.Bson;
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
        resp.getHeaders().put("new header", newHeader);
        aggr.addURL(resp);
        APICatalogSync sync = new APICatalogSync("access-token", 5);

        for (int i = 2; i <= 30; i ++ ) {
            aggr.addURL(TestDump2.createSampleParams("user"+i, url+i));
        }
        sync.computeDelta(aggr, true, 0);
        sync.syncWithDB(); 

        assertEquals(1, sync.getDelta(0).getStrictURLToMethods().size());
        assertEquals(1, sync.getDelta(0).getTemplateURLToMethods().size());

        Map.Entry<URLTemplate, RequestTemplate> entry = sync.getDelta(0).getTemplateURLToMethods().entrySet().iterator().next();

        assertEquals(url+"INTEGER", entry.getKey().getTemplateString());
        RequestTemplate reqTemplate = entry.getValue();

        assertEquals(29, reqTemplate.getUserIds().size());
        assertEquals(2, reqTemplate.getParameters().size());
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.getStatusCode());
        assertEquals(29, respTemplate.getUserIds().size());
        assertEquals(3, respTemplate.getParameters().size());
    }    

    @Test
    public void testImmediateSync() {
        String url = "immediate/";

        List<HttpResponseParams> responseParams = new ArrayList<>();

        HttpResponseParams resp = TestDump2.createSampleParams("user1", url+1);
        ArrayList<String> newHeader = new ArrayList<>();
        newHeader.add("hnew");
        resp.getHeaders().put("new header", newHeader);
        responseParams.add(resp);

        for (int i = 2; i <= 30; i ++ ) {
            responseParams.add(TestDump2.createSampleParams("user"+i, url+i));
        }

        HttpCallParser parser = new HttpCallParser("access-token", 1,40,10);

        parser.syncFunction(responseParams);
        assertTrue(parser.getSyncCount() == 0);
        
        parser.syncFunction(responseParams);
        assertFalse(parser.getSyncCount() == 0);

        responseParams.get(0).setSource(Source.HAR);
        parser.syncFunction(responseParams);
        assertTrue(parser.getSyncCount() == 0);

        SampleData sd = SampleDataDao.instance.findOne(Filters.eq("_id.url", "immediate/INTEGER"));
        assertEquals(10, sd.getSamples().size());

    }  

    @Test
    public void testAllPaths() {
        String url = "link/";

        List<HttpResponseParams> responseParams = new ArrayList<>();

        HttpResponseParams resp = TestDump2.createSampleParams("user1", url+1);
        ArrayList<String> newHeader = new ArrayList<>();
        newHeader.add("hnew");
        resp.getHeaders().put("new header", newHeader);
        responseParams.add(resp);
        resp.setSource(Source.HAR);
        HttpCallParser parser = new HttpCallParser("access-token", 10,40,10);

        /* tryMergingWithKnownStrictURLs - put in delta-static */
        parser.syncFunction(responseParams);
        assertTrue(parser.getSyncCount() == 0);

        /* processKnownStaticURLs */
        parser.syncFunction(responseParams);

        /* tryMergingWithKnownStrictURLs - merge with delta-static */        
        responseParams.add(TestDump2.createSampleParams("user"+2, url+2));
        responseParams.add(TestDump2.createSampleParams("user"+3, url+3));

        /* tryMergingWithKnownStrictURLs - merge with delta-template */  
        responseParams.add(TestDump2.createSampleParams("user"+4, url+4));
        parser.syncFunction(responseParams);
        assertTrue(parser.getSyncCount() == 0);
        
        /* tryMergingWithKnownTemplates */
        parser.syncFunction(responseParams);
        assertTrue(parser.getSyncCount() == 0);

        /* tryMergingWithKnownStrictURLs - merge with Db url */
        url = "payment/";
        responseParams = new ArrayList<>();
        responseParams.add(TestDump2.createSampleParams("user"+2, url+2));
        responseParams.get(0).setSource(Source.HAR);
        parser.syncFunction(responseParams);
        responseParams = new ArrayList<>();
        responseParams.add(TestDump2.createSampleParams("user"+3, url+3));

        /* tryMergingWithKnownStrictURLs - merge with Db url - template already exists in delta */
        responseParams.add(TestDump2.createSampleParams("user"+4, url+4));
        responseParams.get(0).setSource(Source.HAR);
        parser.syncFunction(responseParams);

    }  

    @Test
    public void testInvalidMergeParameterizedURL() {
        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 1);

        for (int i = 1; i <= 30; i ++ ) {
            aggr.addURL(TestDump2.createSampleParams("user"+i, "payment/id"+i));
        }
        sync.computeDelta(aggr, true, 0);
        sync.syncWithDB(); 


        assertEquals(0, sync.getDelta(0).getStrictURLToMethods().size());
        assertEquals(1, sync.getDelta(0).getTemplateURLToMethods().size());

        HttpResponseParams resp2 = TestDump2.createSampleParams("user1", "payment/history");
        ArrayList<String> newHeader = new ArrayList<>();
        newHeader.add("hnew");
        resp2.getHeaders().put("new header", newHeader);
        URLAggregator aggr2 = new URLAggregator();
        aggr2.addURL(resp2);
        
        sync.computeDelta(aggr2, true, 0);
        sync.syncWithDB(); 

        assertEquals(1, sync.getDelta(0).getStrictURLToMethods().size());
        assertEquals(1, sync.getDelta(0).getTemplateURLToMethods().size());


    }

    @Test
    public void testInitialiseFilters() throws InterruptedException {
        Context.accountId.set(10000);
        int totalFilters = 2;
        RuntimeFilterDao.instance.initialiseFilters();
        List<RuntimeFilter> runtimeFilters = RuntimeFilterDao.instance.findAll(new BasicDBObject());
        assertEquals(runtimeFilters.size(), totalFilters);

        Bson filter = Filters.eq(RuntimeFilter.NAME, RuntimeFilter.OPEN_ENDPOINTS_FILTER);
        RuntimeFilterDao.instance.getMCollection().deleteOne(filter);
        runtimeFilters = RuntimeFilterDao.instance.findAll(filter);
        assertEquals(runtimeFilters.size(), 0);

        runtimeFilters = RuntimeFilterDao.instance.findAll(filter);
        assertEquals(runtimeFilters.size(), 1);

    }

}
