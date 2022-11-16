package com.akto.parsers;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.*;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.RuntimeFilterDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.User;
import com.akto.dto.messaging.Message.Mode;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.type.SingleTypeInfo.ParamId;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import org.bson.conversions.Bson;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class TestDBSync extends MongoBasedTest {

    private static int currAccountId = 0;

    @Before
    public void changeAccountId() {
        Context.accountId.set(currAccountId);
        currAccountId += 1;
    }

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
        sync.syncWithDB(false, true);

        assertEquals(0, sync.getDelta(0).getStrictURLToMethods().size());
        assertEquals(1, sync.getDelta(0).getTemplateURLToMethods().size());

        Map.Entry<URLTemplate, RequestTemplate> entry = sync.getDelta(0).getTemplateURLToMethods().entrySet().iterator().next();

        assertEquals(url+"INTEGER", entry.getKey().getTemplateString());
        RequestTemplate reqTemplate = entry.getValue();

        assertEquals(30, reqTemplate.getUserIds().size());
        assertEquals(2, reqTemplate.getParameters().size());
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.getStatusCode());
        assertEquals(30, respTemplate.getUserIds().size());
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

        HttpCallParser parser = new HttpCallParser("access-token", 1,40,10, true);

        parser.syncFunction(responseParams, false, true);
        assertTrue(parser.getSyncCount() == 0);
        
        parser.syncFunction(responseParams, false, true);
        assertFalse(parser.getSyncCount() == 0);

        responseParams.get(0).setSource(Source.HAR);
        parser.syncFunction(responseParams, false, true);
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
        HttpCallParser parser = new HttpCallParser("access-token", 10,40,10, true);

        /* tryMergingWithKnownStrictURLs - put in delta-static */
        parser.syncFunction(responseParams, false, true);
        assertTrue(parser.getSyncCount() == 0);

        /* processKnownStaticURLs */
        parser.syncFunction(responseParams, false, true);

        /* tryMergingWithKnownStrictURLs - merge with delta-static */        
        responseParams.add(TestDump2.createSampleParams("user"+2, url+2));
        responseParams.add(TestDump2.createSampleParams("user"+3, url+3));

        /* tryMergingWithKnownStrictURLs - merge with delta-template */  
        responseParams.add(TestDump2.createSampleParams("user"+4, url+4));
        parser.syncFunction(responseParams, false, true);
        assertTrue(parser.getSyncCount() == 0);
        
        /* tryMergingWithKnownTemplates */
        parser.syncFunction(responseParams, false, true);
        assertTrue(parser.getSyncCount() == 0);

        /* tryMergingWithKnownStrictURLs - merge with Db url */
        url = "payment/";
        responseParams = new ArrayList<>();
        responseParams.add(TestDump2.createSampleParams("user"+2, url+2));
        responseParams.get(0).setSource(Source.HAR);
        parser.syncFunction(responseParams, false, true);
        responseParams = new ArrayList<>();
        responseParams.add(TestDump2.createSampleParams("user"+3, url+3));

        /* tryMergingWithKnownStrictURLs - merge with Db url - template already exists in delta */
        responseParams.add(TestDump2.createSampleParams("user"+4, url+4));
        responseParams.get(0).setSource(Source.HAR);
        parser.syncFunction(responseParams, false, true);

    }  

    @Test
    public void testInvalidMergeParameterizedURL() {
        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 1);

        for (int i = 1; i <= 30; i ++ ) {
            aggr.addURL(TestDump2.createSampleParams("user"+i, "payment/id"+i));
        }
        sync.computeDelta(aggr, true, 123);
        sync.syncWithDB(false, true);


        assertEquals(30, sync.getDelta(123).getStrictURLToMethods().size());
        assertEquals(0, sync.getDelta(123).getTemplateURLToMethods().size());

        HttpResponseParams resp2 = TestDump2.createSampleParams("user1", "payment/history");
        ArrayList<String> newHeader = new ArrayList<>();
        newHeader.add("hnew");
        resp2.getHeaders().put("new header", newHeader);
        URLAggregator aggr2 = new URLAggregator();
        aggr2.addURL(resp2);
        
        sync.computeDelta(aggr2, true, 123);
        sync.syncWithDB(false, true);

        assertEquals(0, sync.getDelta(123).getStrictURLToMethods().size());
        assertEquals(1, sync.getDelta(123).getTemplateURLToMethods().size());


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

    }

    @Test
    public void testFilterHttpResponseParamsEmpty() {
        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0, true);
        List<HttpResponseParams> ss = httpCallParser.filterHttpResponseParams(new ArrayList<>());
        assertEquals(ss.size(),0);
    }

    @Test
    public void testFilterHttpResponseParamsIpHost() {
        ApiCollection.useHost = true;
        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0, true);
        HttpResponseParams h1 = new HttpResponseParams();
        h1.requestParams = new HttpRequestParams();
        h1.requestParams.setHeaders(new HashMap<>());
        h1.requestParams.getHeaders().put("host", Collections.singletonList("127.1.2.3"));
        h1.statusCode = 200;
        h1.requestParams.setApiCollectionId(1000);
        h1.setSource(Source.MIRRORING);

        HttpResponseParams h2 = new HttpResponseParams();
        h2.requestParams = new HttpRequestParams();
        h2.requestParams.setHeaders(new HashMap<>());
        h2.requestParams.getHeaders().put("host", Collections.singletonList("avneesh32.com"));
        h2.statusCode = 200;
        h2.requestParams.setApiCollectionId(1000);
        h2.setSource(Source.MIRRORING);

        List<HttpResponseParams> ss = httpCallParser.filterHttpResponseParams(Arrays.asList(h1, h2));
        assertEquals(ss.size(),2);
        assertEquals(h1.requestParams.getApiCollectionId(), 1000);
        assertTrue(h2.requestParams.getApiCollectionId() != 1000);
    }

    @Test
    public void testFilterHttpResponseParamsWithoutHost() {
        ApiCollection.useHost = false;
        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0, true);

        String groupName1 = "groupName1";
        int vxlanId1 = 1;
        String domain1 = "domain1.com";

        ApiCollectionsDao.instance.insertOne(new ApiCollection(vxlanId1, groupName1, 0, new HashSet<>(), null, 0));

        HttpResponseParams h1 = new HttpResponseParams();
        h1.requestParams = new HttpRequestParams();
        h1.requestParams.setHeaders(new HashMap<>());
        h1.requestParams.getHeaders().put("host", Collections.singletonList(domain1));
        h1.statusCode = 200;
        h1.requestParams.setApiCollectionId(vxlanId1);

        List<HttpResponseParams> filterHttpResponseParamsList = httpCallParser.filterHttpResponseParams(Collections.singletonList(h1));

        Assertions.assertEquals(filterHttpResponseParamsList.size(),1);
        Assertions.assertEquals(filterHttpResponseParamsList.get(0).requestParams.getApiCollectionId(),vxlanId1);
        ApiCollection apiCollection1 = ApiCollectionsDao.instance.findOne("_id", vxlanId1);
        Assertions.assertEquals(apiCollection1.getVxlanId(), vxlanId1);
        Assertions.assertNull(apiCollection1.getHostName());

        int vxlanId2 = 2;
        String groupName2 = "groupName2";
        HttpResponseParams h2 = new HttpResponseParams();
        h2.requestParams = new HttpRequestParams();
        h2.statusCode = 200;
        h2.requestParams.setApiCollectionId(vxlanId2);

        filterHttpResponseParamsList = httpCallParser.filterHttpResponseParams(Collections.singletonList(h2));

        Assertions.assertEquals(filterHttpResponseParamsList.size(),1);
        Assertions.assertEquals(filterHttpResponseParamsList.get(0).requestParams.getApiCollectionId(),vxlanId2);
        ApiCollection apiCollection2 = ApiCollectionsDao.instance.findOne("_id", vxlanId2);
        Assertions.assertEquals(apiCollection2.getVxlanId(), vxlanId2);
        Assertions.assertNull(apiCollection2.getHostName());

        int vxlanId3 = 3;
        String groupName3 = "groupName3";
        HttpResponseParams h3 = new HttpResponseParams();
        h3.requestParams = new HttpRequestParams();
        h3.statusCode = 400;
        h3.requestParams.setApiCollectionId(vxlanId2);

        filterHttpResponseParamsList = httpCallParser.filterHttpResponseParams(Collections.singletonList(h3));

        Assertions.assertEquals(filterHttpResponseParamsList.size(),0);
        ApiCollection apiCollection3 = ApiCollectionsDao.instance.findOne("_id", vxlanId3);
        Assertions.assertNull(apiCollection3);


        Assertions.assertEquals(httpCallParser.getHostNameToIdMap().size(), 2);
        Assertions.assertNotNull(httpCallParser.getHostNameToIdMap().get("null 1"));
        Assertions.assertNotNull(httpCallParser.getHostNameToIdMap().get("null 2"));

    }

    @Test
    public void testFilterResponseParamsWithHost() {
        ApiCollection.useHost = true;
        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0, true);

        String groupName1 = "groupName1";
        int vxlanId1 = 1;
        String domain1 = "domain1.com";

        ApiCollectionsDao.instance.insertOne(new ApiCollection(vxlanId1, groupName1, 0, new HashSet<>(), null, 0));

        HttpResponseParams h1 = new HttpResponseParams();
        h1.requestParams = new HttpRequestParams();
        h1.requestParams.setHeaders(new HashMap<>());
        h1.requestParams.getHeaders().put("host", Collections.singletonList(domain1));
        h1.requestParams.setApiCollectionId(vxlanId1);
        h1.statusCode = 200;
        h1.setSource(Source.MIRRORING);

        httpCallParser.filterHttpResponseParams(Collections.singletonList(h1));

        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiCollections.size(),2);

        int id = domain1.hashCode();
        ApiCollection apiCollection1 = ApiCollectionsDao.instance.findOne(Filters.eq("_id", id));
        Assertions.assertEquals(apiCollection1.getVxlanId(), 0);
        Assertions.assertEquals(apiCollection1.getHostName(), domain1);
        Assertions.assertEquals(httpCallParser.getHostNameToIdMap().size(),1);
        Assertions.assertNotNull(httpCallParser.getHostNameToIdMap().get(domain1));

        // ***********************************

        String groupName2 = "groupName2";
        int vxlanId2 = 2;
        String domain2 = "domain2.com";

        HttpResponseParams h2 = new HttpResponseParams();
        h2.requestParams = new HttpRequestParams();
        h2.requestParams.setHeaders(new HashMap<>());
        h2.requestParams.getHeaders().put("host", Collections.singletonList(domain2));
        h2.requestParams.setApiCollectionId(vxlanId2);
        h2.statusCode = 200;
        h2.setSource(Source.MIRRORING);

        httpCallParser.filterHttpResponseParams(Collections.singletonList(h2));

        apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiCollections.size(),3);

        id = domain2.hashCode();
        ApiCollection apiCollection2 = ApiCollectionsDao.instance.findOne(Filters.eq("_id", id));
        Assertions.assertEquals(apiCollection2.getVxlanId(), 0);
        Assertions.assertEquals(apiCollection2.getHostName(), domain2);
        Assertions.assertEquals(httpCallParser.getHostNameToIdMap().size(),2);
        Assertions.assertNotNull(httpCallParser.getHostNameToIdMap().get(domain2));

        // same vxlan but different host

        String domain3 = "domain3.com";

        HttpResponseParams h3 = new HttpResponseParams();
        h3.requestParams = new HttpRequestParams();
        h3.requestParams.setHeaders(new HashMap<>());
        h3.requestParams.getHeaders().put("host", Collections.singletonList(domain3));
        h3.requestParams.setApiCollectionId(vxlanId2); // same vxlan but different host
        h3.statusCode = 200;
        h3.setSource(Source.MIRRORING);

        httpCallParser.filterHttpResponseParams(Collections.singletonList(h3));

        apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiCollections.size(),4);

        id = domain3.hashCode();
        ApiCollection apiCollection3 = ApiCollectionsDao.instance.findOne(Filters.eq("_id", id));
        Assertions.assertEquals(apiCollection3.getVxlanId(), 0);
        Assertions.assertEquals(apiCollection3.getHostName(), domain3);
        Assertions.assertEquals(httpCallParser.getHostNameToIdMap().size(),3);
        Assertions.assertNotNull(httpCallParser.getHostNameToIdMap().get(domain3));


        // different vxlan and host but same id collision
        int vxlanId4 = 4;
        String groupName4 = "groupName4";
        String domain4 = "domain4.com";

        HttpResponseParams h4 = new HttpResponseParams();
        h4.requestParams = new HttpRequestParams();
        h4.requestParams.setHeaders(new HashMap<>());
        h4.requestParams.getHeaders().put("host", Collections.singletonList(domain4));
        h4.requestParams.setApiCollectionId(vxlanId4);
        h4.statusCode = 200;
        h4.setSource(Source.MIRRORING);

        // before processing inserting apiCollection with same id but different vxlanId and host
        int dupId = domain4.hashCode();
        ApiCollectionsDao.instance.insertOne(
                new ApiCollection(dupId,"something", 0, new HashSet<>(), "hostRandom", 1234)
        );
        httpCallParser.getHostNameToIdMap().put("hostRandom 1234", dupId);

        httpCallParser.filterHttpResponseParams(Collections.singletonList(h4));

        apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiCollections.size(),5);

        id = domain4.hashCode();
        id += 1; // since duplicate so increased by 1 will work
        ApiCollection apiCollection4 = ApiCollectionsDao.instance.findOne(Filters.eq("_id", id));
        Assertions.assertNull(apiCollection4);
    }

    @Test
    public void testCollisionHostNameCollection() {
        ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "domain", 0, new HashSet<>(), null, 0));
        HttpResponseParams h1 = new HttpResponseParams();
        h1.requestParams = new HttpRequestParams();
        h1.requestParams.setHeaders(new HashMap<>());
        h1.requestParams.getHeaders().put("host", Collections.singletonList("domain"));
        h1.statusCode = 200;
        h1.setSource(Source.MIRRORING);

        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0, true);
        httpCallParser.filterHttpResponseParams(Collections.singletonList(h1));

        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiCollections.size(), 1);
        Assertions.assertEquals(apiCollections.get(0).getId(), 0);
    }

    // @Test
    // public void testRemovingStaleStaticURLs() {
    //     ParamId staticParam = new ParamId("api/payment1", "GET", -1, false, "payment_id", SingleTypeInfo.GENERIC, 0);
    //     staticParam.setSubTypeString("GENERIC");
    //     SingleTypeInfo staticInfo = new SingleTypeInfo(staticParam, null, new HashSet<>(), 1, 1, 1, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
    //     staticInfo.setSubTypeString("GENERIC");
    //     ParamId templateParam = new ParamId("api/STRING", "GET", -1, false, "payment_id", SingleTypeInfo.GENERIC, 0);
    //     templateParam.setSubTypeString("GENERIC");
    //     SingleTypeInfo templateInfo = new SingleTypeInfo(templateParam, null, new HashSet<>(), 1, 1, 1, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
    //     templateInfo.setSubTypeString("GENERIC");
    //     SingleTypeInfoDao.instance.insertOne(staticInfo);
    //     SingleTypeInfoDao.instance.insertOne(templateInfo);
    //     HttpCallParser parser1 = new HttpCallParser("access-token", 1,40,10);
    //     assertEquals(parser1.apiCatalogSync.dbState.get(0).getStrictURLToMethods().size(), 0);
    //     parser1.syncFunction(new ArrayList<>());
    //     assertEquals(SingleTypeInfoDao.instance.findAll(new BasicDBObject()).size(), 1);

    // }
}
