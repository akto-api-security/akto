package com.akto.parsers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.*;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.RuntimeFilterDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpRequestParams;
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

    }

    @Test
    public void testFilterHttpResponseParamsEmpty() {
        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0);
        List<HttpResponseParams> ss = httpCallParser.filterHttpResponseParams(new ArrayList<>());
        assertEquals(ss.size(),0);
    }

    @Test
    public void testFilterHttpResponseParams() {
        String domain = "domain.com";
        String domain1 = "domain1.com";
        String domain2 = "domain2.com";
        String domain3 = "domain3.com";
        ApiCollectionsDao.instance.insertOne(new ApiCollection(0, domain, 0, new HashSet<>()));
        // id as domain2 hashcode to see if collision case works or not
        ApiCollectionsDao.instance.insertOne(new ApiCollection(domain2.hashCode(), domain1, 0, new HashSet<>()));

        HttpResponseParams h1 = new HttpResponseParams();
        h1.requestParams = new HttpRequestParams();
        h1.requestParams.setHeaders(new HashMap<>());
        h1.requestParams.getHeaders().put("host", Collections.singletonList(domain2));
        h1.statusCode = 300;
        HttpResponseParams h2 = new HttpResponseParams();
        h2.requestParams = new HttpRequestParams();
        h2.requestParams.setHeaders(new HashMap<>());
        h2.requestParams.getHeaders().put("host", Collections.singletonList(domain2));
        h2.statusCode = 499;
        HttpResponseParams h3 = new HttpResponseParams();
        h3.requestParams = new HttpRequestParams();
        h3.requestParams.setHeaders(new HashMap<>());
        h3.requestParams.getHeaders().put("host", Collections.singletonList(domain2));
        h3.statusCode = 200;
        HttpResponseParams h4 = new HttpResponseParams();
        h4.requestParams = new HttpRequestParams();
        h4.requestParams.setHeaders(new HashMap<>());
        h4.requestParams.getHeaders().put("host", Collections.singletonList(domain2));
        h4.statusCode = 199;
        HttpResponseParams h5 = new HttpResponseParams();
        h5.requestParams = new HttpRequestParams();
        h5.requestParams.setHeaders(new HashMap<>());
        h5.requestParams.getHeaders().put("host", Collections.singletonList(domain2));
        h5.statusCode = 233;
        HttpResponseParams h6 = new HttpResponseParams();
        h6.requestParams = new HttpRequestParams();
        h6.requestParams.setHeaders(new HashMap<>());
        h6.requestParams.getHeaders().put("host", Collections.singletonList(domain3));
        h6.statusCode = 299;

        List<HttpResponseParams> httpResponseParamsList = Arrays.asList(h1,h2,h3,h4,h5,h6);

        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0);
        List<HttpResponseParams> filterHttpResponseParamsList = httpCallParser.filterHttpResponseParams(httpResponseParamsList);

        Assertions.assertEquals(filterHttpResponseParamsList.size(), 3);
        Assertions.assertTrue(filterHttpResponseParamsList.containsAll(Arrays.asList(h3,h5,h6)));

        Map<String, Integer> hostNameToIdMap = httpCallParser.getHostNameToIdMap();
        Assertions.assertEquals(h3.requestParams.getApiCollectionId(), hostNameToIdMap.get(domain2));
        Assertions.assertEquals(h5.requestParams.getApiCollectionId(), hostNameToIdMap.get(domain2));
        Assertions.assertEquals(h6.requestParams.getApiCollectionId(),  hostNameToIdMap.get(domain3));

        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiCollections.size(), 4);
        Map<String, ApiCollection> apiCollectionMap = new HashMap<>();
        for (ApiCollection apiCollection: apiCollections) {
            apiCollectionMap.put(apiCollection.getName(), apiCollection);
        }

        Assertions.assertEquals(apiCollectionMap.get(domain2).getId(), domain2.hashCode() + 1);
        Assertions.assertTrue(apiCollectionMap.get(domain2).isHostWise());
        Assertions.assertEquals(apiCollectionMap.get(domain3).getId(), domain3.hashCode());
        Assertions.assertEquals(apiCollectionMap.get(domain1).getId(), domain2.hashCode());
        Assertions.assertEquals(apiCollectionMap.get(domain).getId(), 0);
        Assertions.assertFalse(apiCollectionMap.get(domain).isHostWise());

        // if another instance of httpCallParser starts then it start from empty map but it shouldn't create duplicate collections
        HttpCallParser httpCallParser1 = new HttpCallParser("",0,0,0);
        HttpResponseParams h7 = new HttpResponseParams();
        h7.requestParams = new HttpRequestParams();
        h7.requestParams.setHeaders(new HashMap<>());
        h7.requestParams.getHeaders().put("host", Collections.singletonList(domain2));
        h7.statusCode = 200;
        httpCallParser1.filterHttpResponseParams(httpResponseParamsList);
        List<ApiCollection> apiCollections1 = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiCollections1.size(), 4);
    }

    @Test
    public void testCollisionHostNameCollection() {
        ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "domain", 0, new HashSet<>()));
        HttpResponseParams h1 = new HttpResponseParams();
        h1.requestParams = new HttpRequestParams();
        h1.requestParams.setHeaders(new HashMap<>());
        h1.requestParams.getHeaders().put("host", Collections.singletonList("domain"));
        h1.statusCode = 200;

        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0);
        httpCallParser.filterHttpResponseParams(Collections.singletonList(h1));

        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiCollections.size(), 1);
        Assertions.assertEquals(apiCollections.get(0).getId(), 0);
    }

}
