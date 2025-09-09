package com.akto.parsers;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.*;
import java.util.regex.Pattern;

import com.akto.MongoBasedTest;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.AktoDataType;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.User;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.*;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.AccountDataTypesInfo;
import com.akto.dto.type.CollectionReplaceDetails;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.Main;
import com.akto.runtime.URLAggregator;
import com.akto.util.filter.DictionaryFilter;
import com.akto.runtime.utils.Utils;
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
        DictionaryFilter.readDictionaryBinary();

    }

    public void testInitializer(){
        Map<String, AktoDataType> aktoDataTypeMap = new HashMap<>();
        aktoDataTypeMap.put("JWT", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("PHONE_NUMBER", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("CREDIT_CARD", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("IP_ADDRESS", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("EMAIL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("SSN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("UUID", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("URL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(ACCOUNT_ID);
        if (info == null) {
            info = new AccountDataTypesInfo();
        }
        info.setAktoDataTypeMap(aktoDataTypeMap);
        SingleTypeInfo.getAccountToDataTypesInfo().put(ACCOUNT_ID, info);
    }

    @Test
    public void testMongo() {
        testInitializer();
        long u = UsersDao.instance.getMCollection().countDocuments();
        UsersDao.instance.insertOne(new User("Abc", "abc@def.gmail", new HashMap<>(), new HashMap<>()));

        // then
        long v = UsersDao.instance.getMCollection().countDocuments();

        System.out.println("some new print" + u + " " +v);

        assertEquals(u+1, v);

    }    

    @Test
    public void testParameterizedURL() {
        testInitializer();
        String url = "link/";
        HttpResponseParams resp = TestDump2.createSampleParams("user1", url+1);
        URLAggregator aggr = new URLAggregator();
        ArrayList<String> newHeader = new ArrayList<>();
        newHeader.add("hnew");
        resp.getHeaders().put("new header", newHeader);
        aggr.addURL(resp);
        APICatalogSync sync = new APICatalogSync("access-token", 5, true);

        for (int i = 2; i <= 30; i ++ ) {
            aggr.addURL(TestDump2.createSampleParams("user"+i, url+i));
        }
        sync.computeDelta(aggr, true, 0, false);
        sync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123, true, false, sync.existingAPIsInDb, false, false);
        sync.buildFromDB(false, true);

        assertEquals(0, sync.getDbState(123).getStrictURLToMethods().size());
        assertEquals(1, sync.getDbState(123).getTemplateURLToMethods().size());

        Map.Entry<URLTemplate, RequestTemplate> entry = sync.getDbState(123).getTemplateURLToMethods().entrySet().iterator().next();

        assertEquals("/link/INTEGER", entry.getKey().getTemplateString());
        RequestTemplate reqTemplate = entry.getValue();

        assertEquals(2, reqTemplate.getParameters().size());
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.getStatusCode());
        assertEquals(3, respTemplate.getParameters().size());
    }    

//    @Test
    public void testImmediateSync() {
        testInitializer();
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

        parser.syncFunction(responseParams, false, true, null);
        assertTrue(parser.getSyncCount() == 0);
        
        parser.syncFunction(responseParams, false, true, null);
        assertFalse(parser.getSyncCount() == 0);

        responseParams.get(0).setSource(Source.HAR);
        parser.syncFunction(responseParams, false, true, null);
        assertTrue(parser.getSyncCount() == 0);

        APICatalogSync.mergeUrlsAndSave(123, true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);

        SampleData sd = SampleDataDao.instance.findOne(Filters.eq("_id.url", "immediate/INTEGER"));
        assertEquals(1, sd.getSamples().size());

        parser.syncFunction(responseParams,true, true, null);
        sd = SampleDataDao.instance.findOne(Filters.eq("_id.url", "immediate/INTEGER"));
        assertEquals(10, sd.getSamples().size());
    }

    @Test
    public void testAllPaths() {
        testInitializer();
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
        parser.syncFunction(responseParams, false, true, null);
        assertTrue(parser.getSyncCount() == 0);

        /* processKnownStaticURLs */
        parser.syncFunction(responseParams, false, true, null);

        /* tryMergingWithKnownStrictURLs - merge with delta-static */        
        responseParams.add(TestDump2.createSampleParams("user"+2, url+2));
        responseParams.add(TestDump2.createSampleParams("user"+3, url+3));

        /* tryMergingWithKnownStrictURLs - merge with delta-template */  
        responseParams.add(TestDump2.createSampleParams("user"+4, url+4));
        parser.syncFunction(responseParams, false, true, null);
        assertTrue(parser.getSyncCount() == 0);
        
        /* tryMergingWithKnownTemplates */
        parser.syncFunction(responseParams, false, true, null);
        assertTrue(parser.getSyncCount() == 0);

        /* tryMergingWithKnownStrictURLs - merge with Db url */
        url = "payment/";
        responseParams = new ArrayList<>();
        responseParams.add(TestDump2.createSampleParams("user"+2, url+2));
        responseParams.get(0).setSource(Source.HAR);
        parser.syncFunction(responseParams, false, true, null);
        responseParams = new ArrayList<>();
        responseParams.add(TestDump2.createSampleParams("user"+3, url+3));

        /* tryMergingWithKnownStrictURLs - merge with Db url - template already exists in delta */
        responseParams.add(TestDump2.createSampleParams("user"+4, url+4));
        responseParams.get(0).setSource(Source.HAR);
        parser.syncFunction(responseParams, false, true, null);

    }  

    @Test
    public void testInvalidMergeParameterizedURL() {
        testInitializer();
        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 1, true);

        for (int i = 1; i <= 30; i ++ ) {
            aggr.addURL(TestDump2.createSampleParams("user"+i, "/payment/id"+i));
        }
        sync.computeDelta(aggr, true, 123, false);
        sync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);


        assertEquals(30, sync.getDbState(123).getStrictURLToMethods().size());
        assertEquals(0, sync.getDbState(123).getTemplateURLToMethods().size());

        HttpResponseParams resp2 = TestDump2.createSampleParams("user1", "/payment/history");
        ArrayList<String> newHeader = new ArrayList<>();
        newHeader.add("hnew");
        resp2.getHeaders().put("new header", newHeader);
        URLAggregator aggr2 = new URLAggregator();
        aggr2.addURL(resp2);
        
        sync.computeDelta(aggr2, true, 123, false);
        sync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123, true, false, sync.existingAPIsInDb, false, false);
        sync.buildFromDB(false, true);

        assertEquals(1, sync.getDbState(123).getStrictURLToMethods().size());
        assertEquals(1, sync.getDbState(123).getTemplateURLToMethods().size());


    }

    @Test
    public void testInitialiseFilters() throws InterruptedException {
        testInitializer();
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
        List<HttpResponseParams> ss = httpCallParser.filterHttpResponseParams(new ArrayList<>(), null, null, true);
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

        List<HttpResponseParams> ss = httpCallParser.filterHttpResponseParams(Arrays.asList(h1, h2), null, null, true);
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

        ApiCollectionsDao.instance.insertOne(new ApiCollection(vxlanId1, groupName1, 0, new HashSet<>(), null, 0, false, true));

        HttpResponseParams h1 = new HttpResponseParams();
        h1.requestParams = new HttpRequestParams();
        h1.requestParams.setHeaders(new HashMap<>());
        h1.requestParams.getHeaders().put("host", Collections.singletonList(domain1));
        h1.statusCode = 200;
        h1.requestParams.setApiCollectionId(vxlanId1);

        List<HttpResponseParams> filterHttpResponseParamsList = httpCallParser.filterHttpResponseParams(Collections.singletonList(h1),null, null, true);

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

        filterHttpResponseParamsList = httpCallParser.filterHttpResponseParams(Collections.singletonList(h2),null, null, true);

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

        filterHttpResponseParamsList = httpCallParser.filterHttpResponseParams(Collections.singletonList(h3),null, null, true);

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

        ApiCollectionsDao.instance.insertOne(new ApiCollection(vxlanId1, groupName1, 0, new HashSet<>(), null, 0, false, true));

        HttpResponseParams h1 = new HttpResponseParams();
        h1.requestParams = new HttpRequestParams();
        h1.requestParams.setHeaders(new HashMap<>());
        h1.requestParams.getHeaders().put("host", Collections.singletonList(domain1));
        h1.requestParams.setApiCollectionId(vxlanId1);
        h1.statusCode = 200;
        h1.setSource(Source.MIRRORING);

        httpCallParser.filterHttpResponseParams(Collections.singletonList(h1),null, null, true);

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

        httpCallParser.filterHttpResponseParams(Collections.singletonList(h2),null, null, true);

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

        httpCallParser.filterHttpResponseParams(Collections.singletonList(h3),null, null, true);

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
                new ApiCollection(dupId,"something", 0, new HashSet<>(), "hostRandom", 1234, false, true)
        );
        httpCallParser.getHostNameToIdMap().put("hostRandom 1234", dupId);

        httpCallParser.filterHttpResponseParams(Collections.singletonList(h4),null, null, true);

        apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        Assertions.assertEquals(apiCollections.size(),6);

        id = domain4.hashCode();
        id += 1; // since duplicate so increased by 1 will work
        ApiCollection apiCollection4 = ApiCollectionsDao.instance.findOne(Filters.eq("_id", id));
        Assertions.assertNotNull(apiCollection4);
    }

    @Test
    public void testCollisionHostNameCollection() {
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "domain", 0, new HashSet<>(), null, 0, false, true));
        HttpResponseParams h1 = new HttpResponseParams();
        h1.setSource(Source.HAR);
        h1.requestParams = new HttpRequestParams();
        h1.requestParams.setApiCollectionId(0);
        h1.requestParams.setHeaders(new HashMap<>());
        h1.requestParams.getHeaders().put("host", Collections.singletonList("domain"));
        h1.statusCode = 200;

        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0, true);
        httpCallParser.filterHttpResponseParams(Collections.singletonList(h1),null, null, true);

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


    @Test
    public void testApiCollectionRegexMapper() throws Exception {
        String message1 = "{\"method\":\"POST\",\"requestPayload\":\"{}\",\"responsePayload\":\"{\\\"active\\\":false,\\\"createNew\\\":false,\\\"keyConditionFromUsers\\\":null,\\\"keyOperator\\\":null,\\\"name\\\":null,\\\"tagConfig\\\":null,\\\"tagConfigs\\\":{\\\"tagConfigs\\\":[],\\\"usersMap\\\":{\\\"1661879740\\\":\\\"shivansh@akto.io\\\"}}}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1000\",\"path\":\"/api/books\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"dev-1.akto.io\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"https://dev-1.akto.io/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"dev-1.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000000\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        String message2 = "{\"method\":\"POST\",\"requestPayload\":\"{}\",\"responsePayload\":\"{\\\"active\\\":false,\\\"createNew\\\":false,\\\"keyConditionFromUsers\\\":null,\\\"keyOperator\\\":null,\\\"name\\\":null,\\\"tagConfig\\\":null,\\\"tagConfigs\\\":{\\\"tagConfigs\\\":[],\\\"usersMap\\\":{\\\"1661879740\\\":\\\"shivansh@akto.io\\\"}}}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1001\",\"path\":\"/api/cars\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"dev-2.akto.io\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"https://dev-2.akto.io/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"dev-2.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000001\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        String message3 = "{\"method\":\"POST\",\"requestPayload\":\"{}\",\"responsePayload\":\"{\\\"active\\\":false,\\\"createNew\\\":false,\\\"keyConditionFromUsers\\\":null,\\\"keyOperator\\\":null,\\\"name\\\":null,\\\"tagConfig\\\":null,\\\"tagConfigs\\\":{\\\"tagConfigs\\\":[],\\\"usersMap\\\":{\\\"1661879740\\\":\\\"shivansh@akto.io\\\"}}}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1001\",\"path\":\"/api/cars\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"api.akto.io\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"https://api.akto.io/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"api.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000002\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        String message4 = "{\"method\":\"POST\",\"requestPayload\":\"{}\",\"responsePayload\":\"{\\\"active\\\":false,\\\"createNew\\\":false,\\\"keyConditionFromUsers\\\":null,\\\"keyOperator\\\":null,\\\"name\\\":null,\\\"tagConfig\\\":null,\\\"tagConfigs\\\":{\\\"tagConfigs\\\":[],\\\"usersMap\\\":{\\\"1661879740\\\":\\\"shivansh@akto.io\\\"}}}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1001\",\"path\":\"/api/cars\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"dev-2.akto.io\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"https://dev-2.akto.io/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"dev-2.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000001\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        String message5 = "{\"method\":\"POST\",\"requestPayload\":\"{}\",\"responsePayload\":\"{\\\"active\\\":false,\\\"createNew\\\":false,\\\"keyConditionFromUsers\\\":null,\\\"keyOperator\\\":null,\\\"name\\\":null,\\\"tagConfig\\\":null,\\\"tagConfigs\\\":{\\\"tagConfigs\\\":[],\\\"usersMap\\\":{\\\"1661879740\\\":\\\"shivansh@akto.io\\\"}}}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1001\",\"path\":\"/api/cars\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"dev-2.pets.io\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"https://dev-2.pets.io/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"dev-2.pets.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000004\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";


        HttpResponseParams httpResponseParams1 = HttpCallParser.parseKafkaMessage(message1);
        HttpResponseParams httpResponseParams2 = HttpCallParser.parseKafkaMessage(message2);
        HttpResponseParams httpResponseParams3 = HttpCallParser.parseKafkaMessage(message3);
        HttpResponseParams httpResponseParams4 = HttpCallParser.parseKafkaMessage(message4);
        HttpResponseParams httpResponseParams5 = HttpCallParser.parseKafkaMessage(message5);

        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        responseParamsList.add(httpResponseParams1);
        responseParamsList.add(httpResponseParams2);
        responseParamsList.add(httpResponseParams3);
        responseParamsList.add(httpResponseParams4);
        responseParamsList.add(httpResponseParams5);

        AccountSettings accountSettings = new AccountSettings();
        Map<String, CollectionReplaceDetails> apiCollectionNameMapper= new HashMap<>();
        String regex = "^dev-\\d+\\.akto\\.io$";
        String newName = "dev.akto.io";
        apiCollectionNameMapper.put(regex.hashCode()+"", new CollectionReplaceDetails(regex, newName, "host"));
        accountSettings.setApiCollectionNameMapper(apiCollectionNameMapper);

        Main.filterBasedOnHeaders(responseParamsList, accountSettings);

        assertEquals(5, responseParamsList.size());

        assertHostChange(responseParamsList.get(0), "dev.akto.io");
        assertHostChange(responseParamsList.get(1), "dev.akto.io");
        assertHostChange(responseParamsList.get(2), "api.akto.io");
        assertHostChange(responseParamsList.get(3), "dev.akto.io");
        assertHostChange(responseParamsList.get(4), "dev-2.pets.io");

        String accIdRegex = "1000001";
        String newCollName = "acc-1000001";

        apiCollectionNameMapper.clear();
        apiCollectionNameMapper.put(accIdRegex.hashCode()+"", new CollectionReplaceDetails(accIdRegex, newCollName, "account"));
        Main.filterBasedOnHeaders(responseParamsList, accountSettings);

        assertHostChange(responseParamsList.get(0), "dev.akto.io");
        assertHostChange(responseParamsList.get(1), "acc-1000001");
        assertHostChange(responseParamsList.get(2), "api.akto.io");
        assertHostChange(responseParamsList.get(3), "acc-1000001");
        assertHostChange(responseParamsList.get(4), "dev-2.pets.io");


    }

    public static void assertHostChange(HttpResponseParams httpResponseParams, String allowedHost) {
        HttpRequestParams requestParams = httpResponseParams.requestParams;
        String host = requestParams.getHeaders().get("host").get(0);
        assertEquals(allowedHost, host);
    }

    @Test
    public void testHostNameForSourceOther() throws Exception {
        ApiCollection.useHost = true;
        String message1 = "{\"method\":\"POST\",\"requestPayload\":\"{}\",\"responsePayload\":\"{\\\"active\\\":false,\\\"createNew\\\":false,\\\"keyConditionFromUsers\\\":null,\\\"keyOperator\\\":null,\\\"name\\\":null,\\\"tagConfig\\\":null,\\\"tagConfigs\\\":{\\\"tagConfigs\\\":[],\\\"usersMap\\\":{\\\"1661879740\\\":\\\"shivansh@akto.io\\\"}}}\",\"ip\":\"null\",\"source\":\"OTHER\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1000\",\"path\":\"/api/books\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"dev-1.akto.io\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"https://dev-1.akto.io/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"dev-1.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000000\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        HttpResponseParams httpResponseParams1 = HttpCallParser.parseKafkaMessage(message1);

        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        responseParamsList.add(httpResponseParams1);

        HttpCallParser httpCallParser = new HttpCallParser("", 100000, 10000, 10000, true);
        httpCallParser.syncFunction(responseParamsList,true, true, null);
        httpCallParser.apiCatalogSync.syncWithDB(true, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit,
            Source.HAR);

        ApiCollection one = ApiCollectionsDao.instance.findOne(new BasicDBObject());
        String host = "dev-1.akto.io";
        assertEquals(host.hashCode(), one.getId());
        assertEquals(host, one.getHostName());
    }

    @Test
    public void testRedundantUrlCheck() {

        HttpCallParser httpCallParser = new HttpCallParser("",0,0,0, true);
        String url1 = "test1.js";
        String url2= "test2.jsx";
        String url3 = "test3.js.js1";
        String url4 = "test4.js?temp=test";
        String url5 = "test5.js.js";

        List<String> allowedUrlType = Arrays.asList("js");
        Pattern pattern = Utils.createRegexPatternFromList(allowedUrlType);
        assertEquals(httpCallParser.isRedundantEndpoint(url1, pattern), true);
        assertEquals(httpCallParser.isRedundantEndpoint(url2, pattern), false);
        assertEquals(httpCallParser.isRedundantEndpoint(url3, pattern), false);
        assertEquals(httpCallParser.isRedundantEndpoint(url4, pattern), true);
        assertEquals(httpCallParser.isRedundantEndpoint(url5, pattern), true);
    }
}
