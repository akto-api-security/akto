package com.akto.action.observe;

import com.akto.MongoBasedTest;
import com.akto.dao.*;
import com.akto.dto.AccountSettings;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.type.CollectionReplaceDetails;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.runtime.APICatalogSync;
import com.akto.listener.InitializerListener;
import com.akto.listener.RuntimeListener;
import com.akto.parsers.HttpCallParser;
import com.akto.types.CappedSet;
import com.akto.utils.AccountHTTPCallParserAktoPolicyInfo;
import com.google.api.client.util.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import org.junit.Test;

import java.util.*;
import org.json.JSONArray;
import org.junit.Test;

import static com.akto.listener.RuntimeListener.convertStreamToString;
import static com.akto.listener.RuntimeListener.httpCallParser;
import static org.junit.Assert.*;

public class TestInventoryAction extends MongoBasedTest {

    private SingleTypeInfo buildHostSti(String url, String method, String param, int apiCollection) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, method, -1, true, param, SingleTypeInfo.GENERIC, apiCollection, false
        );
        return new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(),0,0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0,0);
    }

    @Test
    public void testFetchEndpointsBasedOnHostName() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();


        ApiCollection apiCollection = new ApiCollection(0, "petstore-lb",0, new HashSet<>(), "petstore.com", 0, false, true);
        ApiCollectionsDao.instance.insertOne(apiCollection);

        SingleTypeInfo sti1 = buildHostSti("url1", "GET", "host", 0);
        SingleTypeInfo sti2 = buildHostSti("url1", "POST", "host", 0);
        SingleTypeInfo sti3 = buildHostSti("url2", "PUT", "host", 0);
        SingleTypeInfo sti4 = buildHostSti("url3", "GET", "host", 0);
        SingleTypeInfo sti5 = buildHostSti("url4", "GET", "host", 0);
        SingleTypeInfo sti6 = buildHostSti("url5", "GET", "random", 0);
        SingleTypeInfo sti7 = buildHostSti("url6", "GET", "host", 1);

        SingleTypeInfoDao.instance.insertMany(Arrays.asList(sti1, sti2, sti3, sti4, sti5, sti6, sti7));

        InventoryAction inventoryAction = new InventoryAction();
        Context.userId.set(null);
        String result = inventoryAction.fetchEndpointsBasedOnHostName();
        assertEquals("ERROR", result);

        inventoryAction.setHostName("idodopeshit.in");
        result = inventoryAction.fetchEndpointsBasedOnHostName();
        assertEquals("ERROR", result);

        inventoryAction.setHostName(apiCollection.getHostName());
        result = inventoryAction.fetchEndpointsBasedOnHostName();
        assertEquals("SUCCESS", result);


        assertEquals(5, inventoryAction.getEndpoints().size());

        BasicDBObject basicDBObject = inventoryAction.getEndpoints().get(0);
        assertEquals(2, basicDBObject.size());
        assertNotNull(basicDBObject.get("url"));
        assertNotNull(basicDBObject.get("method"));
    }

    @Test
    public void testDeMergeApi() {
        ApiCollectionsDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();
        SensitiveSampleDataDao.instance.getMCollection().drop();
        TrafficInfoDao.instance.getMCollection().drop();

        APICatalogSync.mergeAsyncOutside = true;

        ApiCollection.useHost = false;

        // populate db before bug fix

        // common headers: {"host" : "akto.io"} and {"resHeader" : "1"}

        // {"user": "akto"}
        String p1 = "{ \"path\": \"/api/books/hi\", \"method\": \"POST\", \"type\": \"HTTP/1.1\", \"requestHeaders\": \"{\\\"host\\\" : \\\"akto.io\\\"}\", \"requestPayload\": \"{\\\"user\\\": \\\"akto\\\"}\", \"statusCode\": \"200\", \"responseHeaders\": \"{\\\"resHeader\\\" : \\\"1\\\"}\", \"status\": \"OK\", \"responsePayload\": \"{\\\"user_resp\\\": \\\"akto\\\"}\", \"ip\": \"\", \"time\": \"1650287116\", \"akto_account_id\": \"1000000\", \"akto_vxlan_id\": -1, \"source\": \"MIRRORING\" }";

        // {"name": "akto"}
        String p2 = "{ \"path\": \"/api/books/hello\", \"method\": \"POST\", \"type\": \"HTTP/1.1\", \"requestHeaders\": \"{\\\"host\\\" : \\\"akto.io\\\"}\", \"requestPayload\": \"{\\\"name\\\": \\\"akto\\\"}\", \"statusCode\": \"200\", \"responseHeaders\": \"{\\\"resHeader\\\" : \\\"1\\\"}\", \"status\": \"OK\", \"responsePayload\": \"{\\\"name_resp\\\": \\\"akto\\\"}\", \"ip\": \"\", \"time\": \"1650287116\", \"akto_account_id\": \"1000000\", \"akto_vxlan_id\": -1, \"source\": \"MIRRORING\" }";

        // {"company": "akto"}
        String p3 = "{ \"path\": \"/api/books/650d602277c31aee121e1d9b/\", \"method\": \"POST\", \"type\": \"HTTP/1.1\", \"requestHeaders\": \"{\\\"host\\\" : \\\"akto.io\\\"}\", \"requestPayload\": \"{\\\"company\\\": \\\"akto\\\"}\", \"statusCode\": \"200\", \"responseHeaders\": \"{\\\"resHeader\\\" : \\\"1\\\"}\", \"status\": \"OK\", \"responsePayload\": \"{\\\"company_resp\\\": \\\"akto\\\"}\", \"ip\": \"\", \"time\": \"1650287116\", \"akto_account_id\": \"1000000\", \"akto_vxlan_id\": -1, \"source\": \"MIRRORING\" }";
        String p4 = "{ \"path\": \"/api/books/64c8f7361d5c23180a4c855c/\", \"method\": \"POST\", \"type\": \"HTTP/1.1\", \"requestHeaders\": \"{\\\"host\\\" : \\\"akto.io\\\"}\", \"requestPayload\": \"{\\\"company\\\": \\\"akto\\\"}\", \"statusCode\": \"200\", \"responseHeaders\": \"{\\\"resHeader\\\" : \\\"1\\\"}\", \"status\": \"OK\", \"responsePayload\": \"{\\\"company_resp\\\": \\\"akto\\\"}\", \"ip\": \"\", \"time\": \"1650287116\", \"akto_account_id\": \"1000000\", \"akto_vxlan_id\": -1, \"source\": \"MIRRORING\" }";

        // target collection changed to prod.akto.io from akto.io
        AccountSettings accountSettings = new AccountSettings();
        Map<String, CollectionReplaceDetails> apiCollectionNameMapper= new HashMap<>();
        String regex = "akto.*";
        String newName = "prod.akto.io";
        apiCollectionNameMapper.put(regex.hashCode()+"", new CollectionReplaceDetails(regex, newName, "host"));
        accountSettings.setApiCollectionNameMapper(apiCollectionNameMapper);
        AccountSettingsDao.instance.insertOne(accountSettings);

        // hashcode of prod.akto.io is -184401928
        int apiCollectionId = -184401928;
        ApiCollectionsDao.instance.insertOne(new ApiCollection(apiCollectionId, "akto.io", 0, new HashSet<>(), "akto.io", apiCollectionId, false, true));
        String url = "api/books/STRING";
        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
        singleTypeInfos.add(new SingleTypeInfo(new SingleTypeInfo.ParamId(url, "POST", -1, true, "host", SingleTypeInfo.GENERIC, apiCollectionId, false), new HashSet<>(), new HashSet<>(), 0, 1650287116, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, Long.MAX_VALUE, Long.MIN_VALUE)); // req header
        singleTypeInfos.add(new SingleTypeInfo(new SingleTypeInfo.ParamId(url, "POST", 200, true, "resHeader", SingleTypeInfo.GENERIC, apiCollectionId, false), new HashSet<>(), new HashSet<>(), 0, 1650287116, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, Long.MAX_VALUE, Long.MIN_VALUE)); // resp header
        singleTypeInfos.add(new SingleTypeInfo(new SingleTypeInfo.ParamId(url, "POST", -1,false, "2", SingleTypeInfo.INTEGER_32, apiCollectionId,true), new HashSet<>(), new HashSet<>(), 0, 1650287116, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, Long.MAX_VALUE, Long.MIN_VALUE)); // url param

        singleTypeInfos.add(new SingleTypeInfo(new SingleTypeInfo.ParamId(url, "POST", -1,false, "user", SingleTypeInfo.GENERIC, apiCollectionId, false), new HashSet<>(), new HashSet<>(), 0, 1650287116, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, Long.MAX_VALUE, Long.MIN_VALUE)); // req payload
        singleTypeInfos.add(new SingleTypeInfo(new SingleTypeInfo.ParamId(url, "POST", 200,false, "user_resp", SingleTypeInfo.GENERIC, apiCollectionId, false), new HashSet<>(), new HashSet<>(), 0, 1650287116, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, Long.MAX_VALUE, Long.MIN_VALUE)); // resp payload

        singleTypeInfos.add(new SingleTypeInfo(new SingleTypeInfo.ParamId(url, "POST", -1,false, "name", SingleTypeInfo.GENERIC, apiCollectionId, false), new HashSet<>(), new HashSet<>(), 0, 1650287116, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, Long.MAX_VALUE, Long.MIN_VALUE)); // req payload
        singleTypeInfos.add(new SingleTypeInfo(new SingleTypeInfo.ParamId(url, "POST", 200,false, "name_resp", SingleTypeInfo.GENERIC, apiCollectionId, false), new HashSet<>(), new HashSet<>(), 0, 1650287116, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, Long.MAX_VALUE, Long.MIN_VALUE)); // resp payload

        singleTypeInfos.add(new SingleTypeInfo(new SingleTypeInfo.ParamId(url, "POST", -1,false, "company", SingleTypeInfo.GENERIC, apiCollectionId, false), new HashSet<>(), new HashSet<>(), 0, 1650287116, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, Long.MAX_VALUE, Long.MIN_VALUE)); // req payload
        singleTypeInfos.add(new SingleTypeInfo(new SingleTypeInfo.ParamId(url, "POST", 200,false, "company_resp", SingleTypeInfo.GENERIC, apiCollectionId, false), new HashSet<>(), new HashSet<>(), 0, 1650287116, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, Long.MAX_VALUE, Long.MIN_VALUE)); // resp payload
        SingleTypeInfoDao.instance.insertMany(singleTypeInfos);

        ApiInfoDao.instance.insertOne(new ApiInfo(apiCollectionId, url, URLMethods.Method.POST));

        List<String> samples = Arrays.asList(p1, p2, p3, p4);
        SampleDataDao.instance.insertOne(new SampleData(new Key(apiCollectionId, url, URLMethods.Method.POST, -1, 0, 0), samples));

        Map<String, Integer> mapHoursToCount = new HashMap<>();
        mapHoursToCount.put("466214", 1);
        TrafficInfoDao.instance.insertOne(new TrafficInfo(new Key(apiCollectionId, url, URLMethods.Method.POST, -1, 0, 0), mapHoursToCount));

        InventoryAction inventoryAction = new InventoryAction();
        Context.userId.set(null);
        inventoryAction.setUrl(url);
        inventoryAction.setMethod("POST");
        inventoryAction.setApiCollectionId(apiCollectionId);       
        String result = inventoryAction.deMergeApi();
        assertEquals(Action.SUCCESS.toUpperCase(), result);

        BloomFilter<CharSequence> existingAPIsInDb = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000, 0.001 );
        APICatalogSync.mergeUrlsAndSave(apiCollectionId, true, true, existingAPIsInDb, false, false);

        List<SingleTypeInfo> singleTypeInfoObjectIdList  = SingleTypeInfoDao.instance.findAll(SingleTypeInfoDao.filterForSTIUsingURL(apiCollectionId, "/api/books/OBJECT_ID", URLMethods.Method.POST));
        assertEquals(5, singleTypeInfoObjectIdList.size());

        List<SingleTypeInfo> singleTypeInfoHiList  = SingleTypeInfoDao.instance.findAll(SingleTypeInfoDao.filterForSTIUsingURL(apiCollectionId, "/api/books/hi", URLMethods.Method.POST));
        assertEquals(4, singleTypeInfoHiList.size());

        List<SingleTypeInfo> singleTypeInfoHelloList  = SingleTypeInfoDao.instance.findAll(SingleTypeInfoDao.filterForSTIUsingURL(apiCollectionId, "/api/books/hello", URLMethods.Method.POST));
        assertEquals(4, singleTypeInfoHelloList.size());

        SampleData sampleDataObjectId = SampleDataDao.instance.findOne(SampleDataDao.filterForSampleData(apiCollectionId, "/api/books/OBJECT_ID", URLMethods.Method.POST));
        assertNotNull(sampleDataObjectId);

        SampleData sampleDataHi = SampleDataDao.instance.findOne(SampleDataDao.filterForSampleData(apiCollectionId, "/api/books/hi", URLMethods.Method.POST));
        assertNotNull(sampleDataHi);

        SampleData sampleDataHello = SampleDataDao.instance.findOne(SampleDataDao.filterForSampleData(apiCollectionId, "/api/books/hello", URLMethods.Method.POST));
        assertNotNull(sampleDataHello);

        ApiInfo apiInfoObjectId = ApiInfoDao.instance.findOne(SampleDataDao.filterForSampleData(apiCollectionId, "/api/books/OBJECT_ID", URLMethods.Method.POST));
        assertNotNull(apiInfoObjectId);

        ApiInfo apiInfoHi = ApiInfoDao.instance.findOne(SampleDataDao.filterForSampleData(apiCollectionId, "/api/books/hi", URLMethods.Method.POST));
        assertNotNull(apiInfoHi);

        ApiInfo apiInfoHello = ApiInfoDao.instance.findOne(SampleDataDao.filterForSampleData(apiCollectionId, "/api/books/hello", URLMethods.Method.POST));
        assertNotNull(apiInfoHello);

    }

    @Test
    public void testFetchRecentEndpoints() throws Exception {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();

        AccountHTTPCallParserAktoPolicyInfo info = new AccountHTTPCallParserAktoPolicyInfo();
        HttpCallParser callParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
        info.setHttpCallParser(callParser);
        int accountId = Context.accountId.get();
        RuntimeListener.accountHTTPParserMap.put(accountId, info);


        String data = convertStreamToString(InitializerListener.class.getResourceAsStream("/SampleApiData.json"));
        int endpointCount = new JSONArray(data).length();

        assertTrue(endpointCount > 0);

        RuntimeListener.addSampleData();

        InventoryAction inventoryAction = new InventoryAction();
        Context.userId.set(null);
        List<BasicDBObject> basicDBObjects = inventoryAction.fetchRecentEndpoints(0, Context.now());
        assertEquals(endpointCount, basicDBObjects.size());
    }
    
}
