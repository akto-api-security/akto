package com.akto.parsers;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.AktoDataType;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.IgnoreData;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.*;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.*;
import com.akto.dto.type.URLMethods.Method;
import com.akto.runtime.APICatalogSync;
import com.akto.types.CappedSet;
import com.akto.util.filter.DictionaryFilter;
import com.google.api.client.util.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static com.akto.parsers.TestDump2.createList;
import static com.akto.runtime.APICatalogSync.mergeUrlsAndSave;
import static org.junit.Assert.*;

public class TestMergingNew extends MongoBasedTest {

    @Before
    public void initMain() {
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
    public void testMultipleIntegerMerging() {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "api/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int i=0; i< 50; i++) {
            urls.add(url + i + "/books/" + (i+1) + "/cars/" + (i+3));
        }
        for (String c: urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams.subList(0,10), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        parser.syncFunction(responseParams.subList(10,15), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        assertEquals(0, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(15,20), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        assertEquals(0, getStaticURLsSize(parser));



        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();


        assertEquals(1, urlTemplateMap.size());
        assertEquals(0, getStaticURLsSize(parser));

    }

    @Test
    public void testStringMerging() {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);

        String baseUrl = "/api/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = Arrays.asList(
                baseUrl + "demo",
                baseUrl + "cat",
                baseUrl + "OSHE2CNS",
                baseUrl + "2HOIWNJK",
                baseUrl + "31a1a7c5-b4e3-47f5-8579-f7fc044c6a98",
                baseUrl + "tree"
        );

        for (String c : urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams, false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123, true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(true, true);
        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        Map<URLStatic, RequestTemplate> strictUrlMap = parser.apiCatalogSync.getDbState(123).getStrictURLToMethods();


        assertEquals(1, urlTemplateMap.size());
        assertEquals(3, strictUrlMap.size());
    }

    @Test
    public void testCaseInsensitiveApisMerging(){
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);

        String baseUrl = "/api/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = Arrays.asList(
                baseUrl + "demo",
                baseUrl + "DeMo",
                baseUrl + "demO",
                baseUrl + "dEmo",
                baseUrl + "v1/demo",
                baseUrl + "v1/Demo"
        );

        for (String c : urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        for (String c : urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams, false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123, true, false, parser.apiCatalogSync.existingAPIsInDb, true, false);
        parser.apiCatalogSync.buildFromDB(true, true);
        Map<URLStatic, RequestTemplate> strictUrlMap = parser.apiCatalogSync.getDbState(123).getStrictURLToMethods();

        URLStatic urlStatic1 = new URLStatic(urls.get(0), Method.POST);
        URLStatic urlStatic2 = new URLStatic(urls.get(4), Method.POST);
        URLStatic urlStatic3 = new URLStatic(urls.get(1), Method.POST);

        assertEquals(2, strictUrlMap.size());
        assertEquals(false, strictUrlMap.containsKey(urlStatic1));
        assertEquals(false, strictUrlMap.containsKey(urlStatic2));
        assertEquals(true, strictUrlMap.containsKey(urlStatic3));
        
    }

    @Test
    public void testEnglishWordsUrlTestString() {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "/link/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (String x: Arrays.asList(
                "apple", "banana", "cat", "dog", "elephant", "flower", "guitar", "house",
                "island", "jungle", "kite", "lemon", "mountain", "night", "ocean", "piano",
                "queen", "river", "sun", "tree", "umbrella", "village", "whale", "xylophone",
                "yacht", "zebra", "bird", "clock", "desert", "engine", "forest", "garden",
                "honey", "igloo", "jacket", "kangaroo", "lamp", "mirror", "notebook", "orange",
                "pencil", "quilt", "rain", "star", "telephone", "uniform", "violin", "window",
                "yellow", "zipper"
        )) {
            urls.add(url+x);
        }
        for (String c: urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams.subList(0,23), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        assertEquals(23, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(23,28), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        assertEquals(28, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(28,33), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        assertEquals(33, getStaticURLsSize(parser));
    }


    public int getStaticURLsSize(HttpCallParser parser) {
        Map<URLStatic, RequestTemplate> urlStaticMap = parser.apiCatalogSync.getDbState(123).getStrictURLToMethods();

        return urlStaticMap.size();
    }


    @Test
    public void testmultipleUUIDForceMerge(){
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1,true);
        String url = "/api/product/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        while (urls.size() < 25) {
            UUID uuid = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            UUID uuid3 = UUID.randomUUID();
            String finalUrl = url + uuid + "/subproduct/" + uuid2 + "/subitem/" + uuid3 + "/id/" + urls.size();
            urls.add(finalUrl);
        }

        int i = 0;
        for (String c: urls) {
            HttpResponseParams resp = createDifferentHttpResponseParams(i*100, c);
            responseParams.add(resp);
            i +=1;
        }

        parser.syncFunction(responseParams.subList(0,1), false,true, null);
        parser.apiCatalogSync.syncWithDB(false,true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        assertEquals(0, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(1,2), false,true, null);
        parser.apiCatalogSync.syncWithDB(false,true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        assertEquals(0, getStaticURLsSize(parser));

        Map<URLTemplate, RequestTemplate> templateURLToMethods = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        assertEquals(1, templateURLToMethods.size());

        parser.syncFunction(responseParams.subList(3,10), false,true, null);
        parser.syncFunction(Collections.singletonList(createDifferentHttpResponseParams(10000, 
        url + "avneesh@akto.io" + "/subproduct/" + "avneesh@akto.io" + "/subitem/" + "avneesh@akto.io" + "/id/" + "112"
        )), false,true, null); // adding this just to see if multiple subTypes of urlParams are recorded or not (not for UUID merging)
        parser.apiCatalogSync.syncWithDB(false,true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        assertEquals(0, getStaticURLsSize(parser));

        templateURLToMethods = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(
        url + "STRING" + "/subproduct/" + "STRING" + "/subitem/" + "STRING" + "/id/" + "INTEGER"
        , URLMethods.Method.GET);
        RequestTemplate requestTemplate = templateURLToMethods.get(urlTemplate);
        Map<Integer, KeyTypes> keyTypesMap = requestTemplate.getUrlParams();
        KeyTypes keyTypes = keyTypesMap.get(2);

        SingleTypeInfo singleTypeInfo1 = keyTypes.getOccurrences().get(SingleTypeInfo.UUID);
        assertNotNull(singleTypeInfo1);
        SingleTypeInfo singleTypeInfo2 = keyTypes.getOccurrences().get(SingleTypeInfo.EMAIL);
        assertNotNull(singleTypeInfo2);
    }

    @Test
    public void testFirstUrlParameterMerging(){
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        urls.add("/D654447FF7"); // merges to /STRING
        urls.add("/c7e5e544-4040-4405-b2a7-22bf9c5286fb"); // merges to /STRING
        urls.add("/3"); // merges to /INTEGER
        urls.add(new ObjectId().toHexString()); // merges to /OBJECT_ID
        urls.add("test@akto.io"); //this shouldn't get merge because tokensBelowThreshold and subtype match

        int i = 0;
        for (String c: urls) {
            HttpResponseParams resp = createDifferentHttpResponseParams(i*100, c);
            responseParams.add(resp);
            i +=1;
        }

        parser.syncFunction(responseParams, false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        parser.apiCatalogSync.buildFromDB(false, true);
        assertEquals(1, parser.apiCatalogSync.getDbState(123).getStrictURLToMethods().size());
        assertEquals(3, parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods().size());

    }

    @Test
    public void testUUIDForceMerge() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "api/notifications/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        while (urls.size() < 50) {
            UUID uuid = UUID.randomUUID();
            String finalUrl = url + uuid + "/received";
            urls.add(finalUrl);
        }

        int i = 0;
        for (String c: urls) {
            HttpResponseParams resp = createDifferentHttpResponseParams(i*100, c);
            responseParams.add(resp);
            i +=1;
        }

        parser.syncFunction(responseParams.subList(0,1), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        assertEquals(0, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(1,2), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        assertEquals(0, getStaticURLsSize(parser));

        Map<URLTemplate, RequestTemplate> templateURLToMethods = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        assertEquals(1, templateURLToMethods.size());

        parser.syncFunction(responseParams.subList(3,10), false, true, null);
        parser.syncFunction(Collections.singletonList(createDifferentHttpResponseParams(10000, url+"avneesh@akto.io"+"/received")), false, true, null); // adding this just to see if multiple subTypes of urlParams are recorded or not (not for UUID merging)
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        assertEquals(0, getStaticURLsSize(parser));

        templateURLToMethods = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(url+"STRING"+"/received", URLMethods.Method.GET);
        RequestTemplate requestTemplate = templateURLToMethods.get(urlTemplate);
        Map<Integer, KeyTypes> keyTypesMap = requestTemplate.getUrlParams();
        KeyTypes keyTypes = keyTypesMap.get(2);

//        assertEquals(2, keyTypes.getOccurrences().size());
        SingleTypeInfo singleTypeInfo1 = keyTypes.getOccurrences().get(SingleTypeInfo.UUID);
        assertNotNull(singleTypeInfo1);
        SingleTypeInfo singleTypeInfo2 = keyTypes.getOccurrences().get(SingleTypeInfo.EMAIL);
        assertNotNull(singleTypeInfo2);
    }

    @Test
    public void testParameterizedURLsTestString() {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "/link/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (String x: Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H")) {
            for (int i=0; i< 50; i++) {
                urls.add(url+x+i);
            }
        }
        for (String c: urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams.subList(0,23), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        assertEquals(23, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(23,28), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        assertEquals(0, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(28,33), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        assertEquals(0, getStaticURLsSize(parser));
    }

    @Test
    public void testNonJsonResponsePayloadPayload() throws Exception {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();

        String a = "{\"path\": \"https://invoices.razorpay.com/v1/l/inv_\", \"method\": \"POST\", \"type\": \"HTTP/1.1\", \"requestHeaders\": \"{\\\"X-Killbill-ApiKey\\\": \\\"mplgaming\\\", \\\"Authorization\\\": \\\"Basic somerandom=\\\", \\\"X-Killbill-ApiSecret\\\": \\\"something\\\", \\\"Accept\\\": \\\"application/json\\\", \\\"X-MPL-COUNTRYCODE\\\": \\\"IN\\\", \\\"X-Killbill-CreatedBy\\\": \\\"test-payment\\\", \\\"Content-type\\\": \\\"application/json\\\"}\", \"requestPayload\": \"{}\", \"statusCode\": \"200\", \"responseHeaders\": \"{\\\"Date\\\": \\\"Mon, 18 Apr 2022 13:05:16 GMT\\\", \\\"Content-Type\\\": \\\"application/json\\\", \\\"Transfer-Encoding\\\": \\\"chunked\\\", \\\"Connection\\\": \\\"keep-alive\\\", \\\"Server\\\": \\\"Apache-Coyote/1.1\\\", \\\"Access-Control-Allow-Origin\\\": \\\"*\\\", \\\"Access-Control-Allow-Methods\\\": \\\"GET, POST, DELETE, PUT, OPTIONS\\\", \\\"Access-Control-Allow-Headers\\\": \\\"Authorization,Content-Type,Location,X-Killbill-ApiKey,X-Killbill-ApiSecret,X-Killbill-Comment,X-Killbill-CreatedBy,X-Killbill-Pagination-CurrentOffset,X-Killbill-Pagination-MaxNbRecords,X-Killbill-Pagination-NextOffset,X-Killbill-Pagination-NextPageUri,X-Killbill-Pagination-TotalNbRecords,X-Killbill-Reason\\\", \\\"Access-Control-Expose-Headers\\\": \\\"Authorization,Content-Type,Location,X-Killbill-ApiKey,X-Killbill-ApiSecret,X-Killbill-Comment,X-Killbill-CreatedBy,X-Killbill-Pagination-CurrentOffset,X-Killbill-Pagination-MaxNbRecords,X-Killbill-Pagination-NextOffset,X-Killbill-Pagination-NextPageUri,X-Killbill-Pagination-TotalNbRecords,X-Killbill-Reason\\\", \\\"Access-Control-Allow-Credentials\\\": \\\"true\\\"}\", \"status\": \"OK\", \"responsePayload\": \"aaaaa\", \"ip\": \"\", \"time\": \"1650287116\", \"akto_account_id\": \"1000000\", \"akto_vxlan_id\": 123, \"source\": \"OTHER\"}";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(a);
            httpResponseParams.requestParams.url += i;
            responseParams.add(httpResponseParams);
        }

        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);

        parser.syncFunction(responseParams.subList(0,10), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);

        parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);

        parser.syncFunction(responseParams.subList(10,25), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);
        parser.syncFunction(responseParams.subList(25,30), false, true, null);

        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);


        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        Map<URLStatic, RequestTemplate> urlStaticMap = parser.apiCatalogSync.getDbState(123).getStrictURLToMethods();

        assertEquals(urlTemplateMap.size(), 1);
        assertEquals(urlStaticMap.size(), 0);

    }




    @Test
    public void testEmptyResponsePayload() throws Exception {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();

        String a = "{\"path\": \"http://killdill.mpl.internal:8080/1/kb/paymentGateways/hosted/form/bbb-bbbb-bbb?paymentMethodId=qq-qqq-qqq\", \"method\": \"POST\", \"type\": \"HTTP/1.1\", \"requestHeaders\": \"{\\\"X-Killbill-ApiKey\\\": \\\"mplgaming\\\", \\\"Authorization\\\": \\\"Basic somerandom=\\\", \\\"X-Killbill-ApiSecret\\\": \\\"something\\\", \\\"Accept\\\": \\\"application/json\\\", \\\"X-MPL-COUNTRYCODE\\\": \\\"IN\\\", \\\"X-Killbill-CreatedBy\\\": \\\"test-payment\\\", \\\"Content-type\\\": \\\"application/json\\\"}\", \"requestPayload\": \"{\\\"formFields\\\":[{\\\"key\\\":\\\"amount\\\",\\\"value\\\":\\\"125.000\\\"},{\\\"key\\\":\\\"netAmount\\\",\\\"value\\\":\\\"125.000\\\"},{\\\"key\\\":\\\"currency\\\",\\\"value\\\":\\\"INR\\\"},{\\\"key\\\":\\\"orderId\\\",\\\"value\\\":\\\"ASGARD\\\"},{\\\"key\\\":\\\"paymentMethodId\\\",\\\"value\\\":\\\"zzzz-zzz-zzz-zzzz-zzzz\\\"},{\\\"key\\\":\\\"mobileNumber\\\",\\\"value\\\":\\\"+917021916328\\\"},{\\\"key\\\":\\\"countryCode\\\",\\\"value\\\":\\\"IN\\\"},{\\\"key\\\":\\\"chargeDetails\\\",\\\"value\\\":\\\"{\\\\\\\"charges\\\\\\\":[],\\\\\\\"totalCharges\\\\\\\":0,\\\\\\\"totalChargesLC\\\\\\\":0}\\\"},{\\\"key\\\":\\\"pegRate\\\",\\\"value\\\":\\\"1.0000\\\"},{\\\"key\\\":\\\"extraInfo\\\",\\\"value\\\":\\\"{\\\\\\\"paymentMode\\\\\\\":\\\\\\\"NB_ICICI\\\\\\\",\\\\\\\"additionalPluginInfo\\\\\\\":\\\\\\\"{\\\\\\\\\\\\\\\"merchantId\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"mpl_qa\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"clientId\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"mplgaming\\\\\\\\\\\\\\\"}\\\\\\\",\\\\\\\"paymentModePluginInfo\\\\\\\":\\\\\\\"{\\\\\\\\\\\\\\\"code\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"NB_ICICI\\\\\\\\\\\\\\\"}\\\\\\\",\\\\\\\"paymentMethodType\\\\\\\":\\\\\\\"netbanking\\\\\\\",\\\\\\\"paymentFlow\\\\\\\":\\\\\\\"JP2_AT_R4\\\\\\\"}\\\"},{\\\"key\\\":\\\"appVersion\\\",\\\"value\\\":\\\"1000174\\\"},{\\\"key\\\":\\\"savedPaymentDetails\\\",\\\"value\\\":\\\"{}\\\"},{\\\"key\\\":\\\"appType\\\",\\\"value\\\":\\\"CASH\\\"},{\\\"key\\\":\\\"savedPaymentDetails\\\",\\\"value\\\":\\\"{}\\\"}]}\", \"statusCode\": \"200\", \"responseHeaders\": \"{\\\"Date\\\": \\\"Mon, 18 Apr 2022 13:05:16 GMT\\\", \\\"Content-Type\\\": \\\"application/json\\\", \\\"Transfer-Encoding\\\": \\\"chunked\\\", \\\"Connection\\\": \\\"keep-alive\\\", \\\"Server\\\": \\\"Apache-Coyote/1.1\\\", \\\"Access-Control-Allow-Origin\\\": \\\"*\\\", \\\"Access-Control-Allow-Methods\\\": \\\"GET, POST, DELETE, PUT, OPTIONS\\\", \\\"Access-Control-Allow-Headers\\\": \\\"Authorization,Content-Type,Location,X-Killbill-ApiKey,X-Killbill-ApiSecret,X-Killbill-Comment,X-Killbill-CreatedBy,X-Killbill-Pagination-CurrentOffset,X-Killbill-Pagination-MaxNbRecords,X-Killbill-Pagination-NextOffset,X-Killbill-Pagination-NextPageUri,X-Killbill-Pagination-TotalNbRecords,X-Killbill-Reason\\\", \\\"Access-Control-Expose-Headers\\\": \\\"Authorization,Content-Type,Location,X-Killbill-ApiKey,X-Killbill-ApiSecret,X-Killbill-Comment,X-Killbill-CreatedBy,X-Killbill-Pagination-CurrentOffset,X-Killbill-Pagination-MaxNbRecords,X-Killbill-Pagination-NextOffset,X-Killbill-Pagination-NextPageUri,X-Killbill-Pagination-TotalNbRecords,X-Killbill-Reason\\\", \\\"Access-Control-Allow-Credentials\\\": \\\"true\\\"}\", \"status\": \"OK\", \"responsePayload\": \"\", \"ip\": \"\", \"time\": \"1650287116\", \"akto_account_id\": \"1000000\", \"akto_vxlan_id\": 123, \"source\": \"OTHER\"}";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(a);
            String[] s = httpResponseParams.requestParams.url.split("/");
            s[s.length-1] = "param"+i;
            httpResponseParams.requestParams.url = String.join("/", s) + "?paymentMethodId=qq-qqq-qqq";
            responseParams.add(httpResponseParams);
        }

        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);

        parser.syncFunction(responseParams.subList(0,10), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        parser.syncFunction(responseParams.subList(10,25), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        parser.syncFunction(responseParams.subList(25,30), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);


        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        Map<URLStatic, RequestTemplate> urlStaticMap = parser.apiCatalogSync.getDbState(123).getStrictURLToMethods();

        assertEquals(urlTemplateMap.size(), 30);
        assertEquals(urlStaticMap.size(), 0);

    }

    @Test
    public void testStrictIntoTemplate() {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "/api/books/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int i=0; i< 50; i++) {
            urls.add(url + "c"+i);
        }
        for (String c: urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        parser.apiCatalogSync.dbState = new HashMap<>();
        parser.apiCatalogSync.dbState.put(123, new APICatalog(0, new HashMap<>(), new HashMap<>()));
        parser.apiCatalogSync.getDbState(123).setTemplateURLToMethods(new HashMap<>());
        URLTemplate urlTemplate = APICatalogSync.tryMergeUrls(new URLStatic(responseParams.get(0).requestParams.url, URLMethods.Method.GET), new URLStatic(responseParams.get(1).requestParams.url, URLMethods.Method.GET), false);
        parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods().put(urlTemplate, new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder(new HashMap<>())));

        parser.syncFunction(responseParams.subList(0,15), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        parser.syncFunction(responseParams.subList(15,25), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        parser.syncFunction(responseParams.subList(25,30), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);


        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        Map<URLStatic, RequestTemplate> urlStaticMap = parser.apiCatalogSync.getDbState(123).getStrictURLToMethods();

        assertEquals(1,urlTemplateMap.size());
        assertEquals(0, urlStaticMap.size());
    }

    @Test
    public void test20percentCondition() {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "/api/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int i=0; i< 50; i++) {
            urls.add(url + "a"+i);
        }
        System.out.println("urls:" + urls);
        for (String c: urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams.subList(0,23), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        parser.syncFunction(responseParams.subList(23,28), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        parser.syncFunction(responseParams.subList(28,33), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);


        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        Map<URLStatic, RequestTemplate> urlStaticMap = parser.apiCatalogSync.getDbState(123).getStrictURLToMethods();

        assertEquals(1,urlTemplateMap.keySet().size());
        assertEquals(0, urlStaticMap.keySet().size());

        boolean merged = true;
        for (SingleTypeInfo singleTypeInfo: SingleTypeInfoDao.instance.findAll(new BasicDBObject())) {
            if (!singleTypeInfo.getUrl().equals("/api/STRING")) {
                merged = false;
                break;
            }
        }
        assertTrue(merged);
    }


    public static String createSimpleRequestPayload(String k) {
        BasicDBObject ret = new BasicDBObject();

        ret.append("id", 1).append("startDate", "some string");
        ret.append(k, "avneesh");
        ret.append("name", "ronaldo");
        ret.append("name1", "ronaldo");
        ret.append("name2", "ronaldo");
        ret.append("name3", "ronaldo");
        ret.append("name4", "ronaldo");
        ret.append("name5", "ronaldo");
        ret.append("name6", "ronaldo");
        ret.append("name7", "ronaldo");
        ret.append("name8", "ronaldo");
        ret.append("name9", "ronaldo");
        ret.append("name10", "ronaldo");

        return ret.toJson();
    }

    public static String createDifferentResponsePayload(String k, int start) {
        BasicDBObject ret = new BasicDBObject();

        int count = 0;
        while (count < 10) {
            ret.append(k+"_"+start, "Avneesh");
            start += 1;
            count += 1;
        }

        return ret.toJson();
    }

    public static HttpResponseParams createDifferentHttpResponseParams(int start, String url) {
        HttpRequestParams httpRequestParams = new HttpRequestParams(
                "GET", url, "", new HashMap<>(), createDifferentResponsePayload("req",start), 123
        );

        return new HttpResponseParams(
                "", 200, "", new HashMap<>(), createDifferentResponsePayload("resp", start), httpRequestParams,
                0,"1000000",false, HttpResponseParams.Source.MIRRORING,"", ""
        );
    }


    public static String createSimpleResponsePayload(String k) {
        BasicDBObject ret = new BasicDBObject();

        ret.append("a1", 1).append("b1", new BasicDBObject().append("a2", "some string").append("b2", "some number"));
        ret.append(k, "ankita");
        ret.append("name", "ronaldo");
        ret.append("name1", "ronaldo");
        ret.append("name2", "ronaldo");
        ret.append("name3", "ronaldo");
        ret.append("name4", "ronaldo");
        ret.append("name5", "ronaldo");
        ret.append("name6", "ronaldo");
        ret.append("name7", "ronaldo");
        ret.append("name8", "ronaldo");
        ret.append("name9", "ronaldo");
        ret.append("name10", "ronaldo");

        return ret.toJson();
    }

    public static HttpResponseParams createSampleParams(String userId, String url) {
        HttpResponseParams ret = new HttpResponseParams();
        ret.accountId = Context.accountId.get()+"";
        ret.type = "HTTP/1.1";
        ret.statusCode = 200;
        ret.status = "OK";
        ret.headers = new HashMap<>();
        ret.headers.put("Access-Token", createList(" eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6ImFua3VzaEBnbWFpbC5jb20iLCJpYXQiOjE2MzQ3NTc0NzIsImV4cCI6MTYzNDc1ODM3Mn0.s_UihSzOlEY9spECqSS9SaTPxygn26YodRkYZKHmNwVnVkeppT5mQYKlwiUnjpKYIHxi2a82I50c0FbJKnTTk1Z5aYcT3t8GXUar_DaaEiv3eZZcZiqeOQSnPkP_c4nhC7Fqaq4g03p4Uj_7W5qvUAkTjHOCViwE933rmfX7tZA27o-9-b_ZKXYsTLfk-FjBV7f3piHmRo88j0WpkvuQc8LwcsoUq6yPclVuDsz9YHkvM1B33_QGdZ7nGz47M33tyLXqZyeF4qsnewkOOU6vCiDnM_eqbJghbZLSqP3Ut3lfA54BlAZ-HB5gLv-2HR0m_R2thDGXXE_G_onS-ZDB6A"));
        ret.headers.put("Content-Type", createList(" application/json;charset=utf-8"));
        ret.headers.put("Content-Length", createList(" 762"));
        ret.headers.put("Server", createList(" Jetty(9.4.42.v20210604)"));

        ret.setPayload(createSimpleResponsePayload(url));
        ret.requestParams = new HttpRequestParams();

        ret.requestParams.method = "POST";
        ret.requestParams.url = url;
        ret.requestParams.type = "HTTP/1.1";
        Map<String, List<String>> headers = new HashMap<>();
        List<String> accessTokenHeaders = new ArrayList<>();
        accessTokenHeaders.add(userId);
        headers.put("access-token", accessTokenHeaders);
        headers.put("Host", createList("3.7.253.154:8080"));
        headers.put("Connection", createList("keep-alive"));
        headers.put("Content-Length", createList("61"));
        headers.put("Access-Control-Allow-Origin", createList("*"));
        headers.put("Accept", createList("application/json, text/plain, */*"));
        headers.put("DNT", createList("1"));
        headers.put("account", createList("222222"));
        headers.put("User-Agent", createList("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.82 Safari/537.36"));
        headers.put("Content-Type", createList("application/json"));
        headers.put("Origin", createList("http://3.7.253.154:8080"));
        headers.put("Referer", createList("http://3.7.253.154:8080/dashboard/boards/1624886875"));
        headers.put("Accept-Encoding", createList("gzip, deflate"));
        headers.put("Accept-Language", createList("en-US,en;q=0.9,mr;q=0.8"));
        headers.put("Cookie", createList("JSESSIONID=node01e7k0f9f2mkm0kyan971kl7bk7.node0; refreshToken=eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmt1c2hAZ21haWwuY29tIiwiaWF0IjoxNjM0NzU3NDcwLCJleHAiOjE2MzQ4NDM4NzB9.MHoQpVFiYPgJrY-c4XrOlhWM20Qh1IOEKdiSne92k9p1YmUekBG7_z9osa9yYpO9Tsa1CeMs39ZDiS853boNJPAo6BcSswx6ReYHOmp3-qdu5dvqWjjQb0m-NNGGtPikvNi_d3MFmTQ0vKzu1n3WTmB_Iv-SPtmN22-Rees-VSnit6CQKvm_7kVQt-oU76LfIZ_KesfMm_vRHsFrHfKdw1zVT4XCSlPE0hJhbQNkzkwI-6zByYzG_5MnX5cyvUTIGgZ3-_VGxYRt8zPXFfAqgM1F3L4LDZSTLOu0I9gVElRP-JnSQRvYpsU0eVwP3cgS6UxxaSS_2zZU3Z_TPh8Qfg"));

        ret.requestParams.setHeaders(headers);
        ret.requestParams.setPayload(createSimpleRequestPayload(url));
        ret.requestParams.setApiCollectionId(123);
        ret.setOrig(ret.toString());
        return ret;
    }

    @Test
    public void testAllPaths() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        String url = "link/";

        List<HttpResponseParams> responseParams = new ArrayList<>();

        HttpResponseParams resp = TestDump2.createSampleParams("user1", url + 1);
        ArrayList<String> newHeader = new ArrayList<>();
        newHeader.add("hnew");
        resp.getHeaders().put("new header", newHeader);
        responseParams.add(resp);
        resp.setSource(HttpResponseParams.Source.HAR);
        HttpCallParser parser = new HttpCallParser("access-token", 10, 40, 10, true);

        /* tryMergingWithKnownStrictURLs - put in delta-static */
        parser.syncFunction(responseParams, false, true, null);
        assertTrue(parser.getSyncCount() == 0);

        /* processKnownStaticURLs */
        parser.syncFunction(responseParams, false, true, null);

        /* tryMergingWithKnownStrictURLs - merge with delta-static */
        responseParams.add(TestDump2.createSampleParams("user" + 2, url + 2));
        responseParams.add(TestDump2.createSampleParams("user" + 3, url + 3));

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
        responseParams.add(TestDump2.createSampleParams("user" + 2, url + 2));
        responseParams.get(0).setSource(HttpResponseParams.Source.HAR);
        parser.syncFunction(responseParams, false, true, null);
        responseParams = new ArrayList<>();
        responseParams.add(TestDump2.createSampleParams("user" + 3, url + 3));

        /* tryMergingWithKnownStrictURLs - merge with Db url - template already exists in delta */
        responseParams.add(TestDump2.createSampleParams("user" + 4, url + 4));
        responseParams.get(0).setSource(HttpResponseParams.Source.HAR);
        parser.syncFunction(responseParams, false, true, null);
    }

//    @Test
    public void testUrlParamSingleTypeInfoAndValues() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "/api/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int i=0; i< 300; i++) {
            urls.add(url + i + "/books/" + (i+1) + "/cars/" + (i+3));
        }
        for (String c: urls) {
            BasicDBObject ret = new BasicDBObject();
            ret.put("name", c);
            HttpRequestParams httpRequestParams = new HttpRequestParams("GET", c, "", new HashMap<>(), ret.toJson(), 123);
            HttpResponseParams resp = new HttpResponseParams("", 200,"", new HashMap<>(), ret.toJson(),httpRequestParams, 0,"0",false, HttpResponseParams.Source.MIRRORING,"", "");
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams.subList(0,10), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);

        // dbState doesn't have any template URLs initially so no urlParams are considered
        testSampleSizeAndDomainOfSti(parser,10, 10, SingleTypeInfo.Domain.ENUM, SingleTypeInfo.Domain.ENUM);

        parser.syncFunction(responseParams.subList(10,55), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);

        // Now dbState has template URLs so urlParam values are now stored
        assertEquals(0,getStaticURLsSize(parser));
        testSampleSizeAndDomainOfSti(parser,55, 55, SingleTypeInfo.Domain.ENUM, SingleTypeInfo.Domain.ENUM);

        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);

        testSampleSizeAndDomainOfSti(parser, 55, 55, SingleTypeInfo.Domain.ENUM, SingleTypeInfo.Domain.ENUM);

        // changing the parser symbolizes instance restart
        // using the new or old parser shouldn't change the result
        HttpCallParser parserNew = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        parserNew.syncFunction(responseParams.subList(55,70), false, true, null);
        parserNew.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parserNew.apiCatalogSync.buildFromDB(false, true);
        parserNew.syncFunction(responseParams.subList(70,150), false, true, null);

        parserNew.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        parserNew.syncFunction(responseParams.subList(150,200), false, true, null);
        parserNew.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parserNew.apiCatalogSync.buildFromDB(false, true);

        APICatalogSync.clearValuesInDB();
        parserNew.apiCatalogSync.buildFromDB(false, true);

        // both now range
        testSampleSizeAndDomainOfSti(parserNew, 0, 0, SingleTypeInfo.Domain.RANGE, SingleTypeInfo.Domain.ANY);


    }

    private void testSampleSizeAndDomainOfSti(HttpCallParser parser, int urlParamValuesSize, int nonUrlParamValuesSize,
                                              SingleTypeInfo.Domain urlParamDomain, SingleTypeInfo.Domain nonUrlParamDomain)  {
        Map<URLTemplate, RequestTemplate> templateURLToMethods = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        for (RequestTemplate requestTemplate: templateURLToMethods.values()) {
            for (SingleTypeInfo singleTypeInfo: requestTemplate.getAllTypeInfo()) {
                if (singleTypeInfo.getIsUrlParam()) {
                    assertEquals(urlParamValuesSize, singleTypeInfo.getValues().getElements().size());
                    assertEquals(urlParamDomain,singleTypeInfo.getDomain());
                } else {
                    assertEquals(nonUrlParamValuesSize, singleTypeInfo.getValues().getElements().size());
                    assertEquals(nonUrlParamDomain, singleTypeInfo.getDomain());
                }
            }
        }
    }

    @Test
    public void testMinMaxAndLastSeenNew() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "/api/";

        // test for 1 url
        HttpResponseParams httpResponseParams1 = createHttpResponseForMinMax(url+"books1", 23.4F,-98F );
        HttpResponseParams httpResponseParams2 = createHttpResponseForMinMax(url+"books1", 2.3F,-200.5F );
        HttpResponseParams httpResponseParams3 = createHttpResponseForMinMax(url+"books1", 2500.9F,-200F );
        parser.syncFunction(Arrays.asList(httpResponseParams1, httpResponseParams2, httpResponseParams3), false, true, null);

        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        Collection<RequestTemplate> requestTemplates = parser.apiCatalogSync.getDbState(123).getStrictURLToMethods().values();
        validateMinMax(requestTemplates, 2500, 2, -98, -200);

        // merge the urls
        float reqMax = 2500.9f;
        float reqMin = 2.3f;
        float respMax = -98f;
        float respMin = -200.5f;
        for (int i=0; i< APICatalogSync.STRING_MERGING_THRESHOLD+5; i++) {
//            reqMax += 1;
//            reqMin -= 1;
//            respMax += 1;
//            respMin -= 1;
            HttpResponseParams httpResponseParams = createHttpResponseForMinMax(url+"books"+i, reqMax, respMax);
            parser.syncFunction(Collections.singletonList(httpResponseParams), false, true, null);

            httpResponseParams = createHttpResponseForMinMax(url+"books"+i, reqMin, respMin);
            parser.syncFunction(Collections.singletonList(httpResponseParams), false, true, null);
        }
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123, true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);

        HttpResponseParams httpResponseParams = createHttpResponseForMinMax(url+"books99", 190f, -190f);
        parser.syncFunction(Collections.singletonList(httpResponseParams), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);

        httpResponseParams = createHttpResponseForMinMax(url+"books100", 190f, -190f);
        parser.syncFunction(Collections.singletonList(httpResponseParams), false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);

        APICatalogSync.mergeUrlsAndSave(123, true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);

        // changing the parser symbolizes instance restart
        // using the new or old parser shouldn't change the result
        HttpCallParser parserNew = new HttpCallParser("userIdentifier", 1, 1, 1, true);

        assertEquals(0,parserNew.apiCatalogSync.getDbState(123).getStrictURLToMethods().size());
        assertEquals(1,parserNew.apiCatalogSync.getDbState(123).getTemplateURLToMethods().size());

        requestTemplates = parserNew.apiCatalogSync.getDbState(123).getTemplateURLToMethods().values();
        validateMinMax(requestTemplates, Double.valueOf(reqMax+"").longValue(), Double.valueOf(reqMin+"").longValue(), Double.valueOf(respMax+"").longValue(), Double.valueOf(respMin+"").longValue());

        httpResponseParams = createHttpResponseForMinMax(url+"books10", 19000f, -190f);
        parserNew.syncFunction(Collections.singletonList(httpResponseParams), false, true, null);
        httpResponseParams = createHttpResponseForMinMax(url+"books15", 19f, -19000f);
        parserNew.syncFunction(Collections.singletonList(httpResponseParams), false, true, null);
        parserNew.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        requestTemplates = parserNew.apiCatalogSync.getDbState(123).getTemplateURLToMethods().values();
        validateMinMax(requestTemplates, 19000, Double.valueOf(reqMin+"").longValue(), Double.valueOf(respMax+"").longValue(), -19000);

    }

    private HttpResponseParams createHttpResponseForMinMax(String url, float reqPayload, float respPayload)  {
        BasicDBObject reqRet = new BasicDBObject();
        reqRet.put("value", reqPayload);
        BasicDBObject respRet = new BasicDBObject();
        respRet.put("value", respPayload);

        HttpRequestParams httpRequestParams = new HttpRequestParams("GET", url, "", new HashMap<>(), reqRet.toJson(), 123);
        return new HttpResponseParams("", 200,"", new HashMap<>(), respRet.toJson(),httpRequestParams, 0,"0",false, HttpResponseParams.Source.MIRRORING,"", "");
    }

    private void validateMinMax(Collection<RequestTemplate> requestTemplateCollections, long reqMaxValue, long reqMinValue,
                                long respMaxValue, long respMinValue) {
        for (RequestTemplate requestTemplate: requestTemplateCollections) {
            for (SingleTypeInfo singleTypeInfo: requestTemplate.getAllTypeInfo()) {
                if (singleTypeInfo.isIsHeader() || singleTypeInfo.getIsUrlParam()) continue;
                if (singleTypeInfo.getResponseCode() == -1) {
                    assertEquals(reqMaxValue, singleTypeInfo.getMaxValue());
                    assertEquals(reqMinValue, singleTypeInfo.getMinValue());
                } else {
                    assertEquals(respMaxValue, singleTypeInfo.getMaxValue());
                    assertEquals(respMinValue, singleTypeInfo.getMinValue());
                }
            }
        }
    }

    // Test to check if param dbUpdates are made only when certain conditions are true
    // Case when update shouldn't be made:
    // delta doesn't have the strict url but db has it and no change in min, max and last seen not older than 30 mins
    @Test
    public void testDbUpdateParams() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        int collectionId = 123;
        String url = "api/";

        HttpResponseParams httpResponseParams1 = createHttpResponseForMinMax(url+"books1", 23.4F,-98F );
        parser.syncFunction(Collections.singletonList(httpResponseParams1),true, true, null);
        assertEquals(1, parser.apiCatalogSync.getDbState(collectionId).getStrictURLToMethods().size());

        APICatalogSync.DbUpdateReturn dbUpdateReturn1 = cleanSync(httpResponseParams1, collectionId);
//        assertEquals(0, dbUpdateReturn1.bulkUpdatesForSingleTypeInfo.size()); // because no change in minMax

        HttpResponseParams httpResponseParams2 = createHttpResponseForMinMax(url+"books1", 230.4F,-98F );
        APICatalogSync.DbUpdateReturn dbUpdateReturn2 = cleanSync(httpResponseParams2, collectionId);
//        assertEquals(1, dbUpdateReturn2.bulkUpdatesForSingleTypeInfo.size()); // because reqPayload Max changed

        HttpResponseParams httpResponseParams3 = createHttpResponseForMinMax(url+"books1", 100,-98F );
        APICatalogSync.DbUpdateReturn dbUpdateReturn3 = cleanSync(httpResponseParams3, collectionId);
//        assertEquals(1, dbUpdateReturn3.bulkUpdatesForSingleTypeInfo.size()); // even though minMax didn't change new values were added
    }

    // this function takes httpResponseParam and does runtime thingy in a clean environment (equivalent to server restart)
    private APICatalogSync.DbUpdateReturn cleanSync(HttpResponseParams httpResponseParams, int collectionId) {
        // new httpCallParser to make sure delta is clean
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1000, Context.now() + 1000, true);
        parser.numberOfSyncs = 1000; // to make sure it doesn't sync before

        parser.syncFunction(Collections.singletonList(httpResponseParams),false, true, null);
        APICatalogSync apiCatalogSync = parser.apiCatalogSync;
        return apiCatalogSync.getDBUpdatesForParams(
                apiCatalogSync.getDelta(collectionId), apiCatalogSync.getDbState(collectionId), false, false,
                Source.HAR);

    }

    @Test
    public void testSpecialCharHostMerging() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        String payload1 = "{\"method\":\"PUT\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"doggie\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/pet\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"<script>pet\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        String payload2 = "{\"method\":\"PUT\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"doggie\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/pet\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        HttpCallParser httpCallParser = new HttpCallParser("", 100000, 10000, 10000, true);

        long estimatedDocumentCount = SingleTypeInfoDao.instance.getMCollection().countDocuments(Filters.empty());
        assertEquals(0, estimatedDocumentCount);
        try {
            HttpResponseParams httpResponseParams1 = HttpCallParser.parseKafkaMessage(payload1);
            httpCallParser.syncFunction(Collections.singletonList(httpResponseParams1),false, true, null);
            httpCallParser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit,
                SyncLimit.noLimit, Source.HAR);

            HttpResponseParams httpResponseParams2 = HttpCallParser.parseKafkaMessage(payload2);
            httpCallParser.syncFunction(Collections.singletonList(httpResponseParams2),false, true, null);
            httpCallParser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit,
                SyncLimit.noLimit, Source.HAR);

        } catch(Exception e) {
            System.out.println("dfg");
        }

        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.regex("hostName", "script"));

        assertEquals(null, apiCollection);

        estimatedDocumentCount = SingleTypeInfoDao.instance.getMCollection().countDocuments(Filters.empty());
        assertEquals(74, estimatedDocumentCount);
    }

    // testing if new endpoints are getting their sample data synced immediately or not
    @Test
    public void testSampleDataUpdate() throws Exception {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();

        HttpCallParser httpCallParser = new HttpCallParser("", 100000, 10000, 10000, true);

        // url = https://petstore.swagger.io/v2/pet; method = put
        String payload1 = "{\"method\":\"PUT\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"doggie\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/pet\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        HttpResponseParams httpResponseParams1 = HttpCallParser.parseKafkaMessage(payload1);
        Bson filter1 = Filters.and(
                Filters.eq("_id.url", "https://petstore.swagger.io/v2/pet"),
                Filters.eq("_id.method", "PUT")
        );

        httpCallParser.syncFunction(Collections.singletonList(httpResponseParams1),false, true, null);
        httpCallParser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit,
            Source.HAR);

        SampleData sampleData1 = SampleDataDao.instance.findOne(filter1);
        assertEquals(1, sampleData1.getSamples().size());

        // payload1 but with different request body. This won't get updated because not new URL
        String payload1UpdatedRequestPayload = "{\"method\":\"PUT\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"tommie\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/pet\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        HttpResponseParams httpResponseParams2 = HttpCallParser.parseKafkaMessage(payload1UpdatedRequestPayload);
        httpCallParser.syncFunction(Collections.singletonList(httpResponseParams2), false, true, null);
        httpCallParser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit,
            Source.HAR);

        sampleData1 = SampleDataDao.instance.findOne(filter1);
        assertEquals(1, sampleData1.getSamples().size());


        // url = https://petstore.swagger.io/v2/books/1 ; method = post
        String payload2 = "{\"method\":\"POST\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"tommie\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/books/1\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        HttpResponseParams httpResponseParams3 = HttpCallParser.parseKafkaMessage(payload2);
        httpCallParser.syncFunction(Collections.singletonList(httpResponseParams3), false, true, null);
        httpCallParser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit,
            Source.HAR);

        Bson filter2 = Filters.and(
                Filters.eq("_id.url", "https://petstore.swagger.io/v2/books/INTEGER"),
                Filters.eq("_id.method", "POST")
        );

        SampleData sampleData2 = SampleDataDao.instance.findOne(filter2);
        assertEquals(1, sampleData2.getSamples().size());

        // url = https://petstore.swagger.io/v2/books/2 ; method = post
        String payload3 = "{\"method\":\"POST\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"charlie\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/books/2\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        HttpResponseParams httpResponseParams4 = HttpCallParser.parseKafkaMessage(payload3);
        httpCallParser.syncFunction(Collections.singletonList(httpResponseParams4), false, true, null);
        httpCallParser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit,
            Source.HAR);

        APICatalogSync.mergeUrlsAndSave(httpResponseParams4.requestParams.getApiCollectionId(), true, false, httpCallParser.apiCatalogSync.existingAPIsInDb, false, false);

        Bson filter3 = Filters.and(
                Filters.eq("_id.url", "https://petstore.swagger.io/v2/books/INTEGER"),
                Filters.eq("_id.method", "POST")
        );

        SampleData sampleData3 = SampleDataDao.instance.findOne(filter3);
        assertEquals(1, sampleData3.getSamples().size());

        // url = https://petstore.swagger.io/v2/books/3 ; method = post
        // sample data count won't increase because not new url
        String payload4 = "{\"method\":\"POST\",\"requestPayload\":\"{\\\"photoUrls\\\":[\\\"string\\\"],\\\"name\\\":\\\"brandon\\\",\\\"id\\\":0,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854775807,\\\"category\\\":{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"string\\\"],\\\"tags\\\":[{\\\"id\\\":0,\\\"name\\\":\\\"string\\\"}],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/books/3\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"215\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:58 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327118\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        HttpResponseParams httpResponseParams5 = HttpCallParser.parseKafkaMessage(payload4);
        httpCallParser.syncFunction(Collections.singletonList(httpResponseParams5), false, true, null);
        httpCallParser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit,
            Source.HAR);

        SampleData sampleData4 = SampleDataDao.instance.findOne(filter3);
        assertEquals(1, sampleData4.getSamples().size());

    }

    @Test
    public void testDuplicateSTI() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();

        SingleTypeInfo.ParamId paramId1 = new SingleTypeInfo.ParamId("/api/books/1", "GET", -1, false, "someveryrandomUUID1", SingleTypeInfo.UUID, 1, false);
        SingleTypeInfo singleTypeInfo1 = new SingleTypeInfo(paramId1, new HashSet<>(), new HashSet<>(),0,0,0,new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0 ,10);

        SingleTypeInfo.ParamId paramId2 = new SingleTypeInfo.ParamId("/api/books/2", "GET", -1, false, "someveryrandomUUID1", SingleTypeInfo.UUID, 1, false);
        SingleTypeInfo singleTypeInfo2 = new SingleTypeInfo(paramId2, new HashSet<>(), new HashSet<>(),0,0,0,new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0 ,10);

        SingleTypeInfoDao.instance.insertOne(singleTypeInfo1);
        SingleTypeInfoDao.instance.insertOne(singleTypeInfo2);

        BloomFilter<CharSequence> existingAPIsInDb = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000, 0.001 );
        mergeUrlsAndSave(1, true, false, existingAPIsInDb, false, false);

        SingleTypeInfoDao.instance.insertOne(singleTypeInfo1.copy());
        SingleTypeInfoDao.instance.insertOne(singleTypeInfo2.copy());

        mergeUrlsAndSave(1, true, false, existingAPIsInDb, false, false);

        long estimatedDocumentCount = SingleTypeInfoDao.instance.getMCollection().countDocuments(Filters.eq(SingleTypeInfo._URL,"/api/books/INTEGER"));
        assertEquals(2, estimatedDocumentCount);

        estimatedDocumentCount = SingleTypeInfoDao.instance.getEstimatedCount();
        assertEquals(2, estimatedDocumentCount);

        SingleTypeInfo sti = SingleTypeInfoDao.instance.findOne(Filters.eq(SingleTypeInfo._IS_URL_PARAM, true));
        assertNotNull(sti);

    }

    // this test checks if there are 2 urls api/books and /api/books they don't get merged
    @Test
    public void testMergeUrlsAndSaveSlashBug() {
        ApiCollectionsDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();

        ApiCollection apiCollection = new ApiCollection(1, "akto", 0, new HashSet<>(), "akto", 1, false, true);
        ApiCollectionsDao.instance.insertOne(apiCollection);

        SingleTypeInfo.ParamId paramIdHost1 = new SingleTypeInfo.ParamId(
                "api/books", "GET", -1, true, "host", SingleTypeInfo.GENERIC, apiCollection.getId(), false
        );
        SingleTypeInfo singleTypeInfoHost1 = new SingleTypeInfo(
                paramIdHost1, new HashSet<>(), new HashSet<>(), 0, Context.now(), 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0,10
        );
        SingleTypeInfoDao.instance.insertOne(singleTypeInfoHost1);

        SingleTypeInfo singleTypeInfoHost2 = singleTypeInfoHost1.copy();
        singleTypeInfoHost2.setUrl("/api/books");
        singleTypeInfoHost2.setId(new ObjectId());
        SingleTypeInfoDao.instance.insertOne(singleTypeInfoHost2);

        long count = SingleTypeInfoDao.instance.getEstimatedCount();
        assertEquals(2, count);
        BloomFilter<CharSequence> existingAPIsInDb = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000, 0.001 );
        mergeUrlsAndSave(apiCollection.getId(),true, false, existingAPIsInDb, false, false);

        count = SingleTypeInfoDao.instance.getEstimatedCount();
        assertEquals(2, count);

        SingleTypeInfo sti1 = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(singleTypeInfoHost1));
        assertNotNull(sti1);

        SingleTypeInfo sti2 = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(singleTypeInfoHost2));
        assertNotNull(sti2);
    }

    @Test
    public void testMultipleParamMerge() {
        ApiCollectionsDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();

        ApiCollection apiCollection = new ApiCollection(1, "akto", 0, new HashSet<>(), "akto", 1, false, true);
        ApiCollectionsDao.instance.insertOne(apiCollection);


        SingleTypeInfo.ParamId paramIdHost1 = new SingleTypeInfo.ParamId(
                "/v1/payments/pay_M4aF0T32RMlJGY/callback/885cfaf9b074af2a38888a0a054a6d5aa323fb10", "GET", -1, true, "host", SingleTypeInfo.GENERIC, apiCollection.getId(), false
        );
        SingleTypeInfo singleTypeInfoHost1 = new SingleTypeInfo(
                paramIdHost1, new HashSet<>(), new HashSet<>(), 0, Context.now(), 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0,10
        );
        SingleTypeInfoDao.instance.insertOne(singleTypeInfoHost1);

        SingleTypeInfo singleTypeInfoHost2 = singleTypeInfoHost1.copy();
        singleTypeInfoHost2.setUrl("/v1/payments/pay_M4aAEM4AD4cLB0/callback/c04a604d75ad8693e66eab9a4ef7b00388a4e9e7");
        singleTypeInfoHost2.setId(new ObjectId());
        SingleTypeInfoDao.instance.insertOne(singleTypeInfoHost2);

        long count = SingleTypeInfoDao.instance.getEstimatedCount();
        assertEquals(2, count);
        BloomFilter<CharSequence> existingAPIsInDb = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000, 0.001 );
        mergeUrlsAndSave(apiCollection.getId(),true,true, existingAPIsInDb, false, false);

        count = SingleTypeInfoDao.instance.getEstimatedCount();
        assertEquals(3, count); // 1 host + 2 url params

        count = SingleTypeInfoDao.instance.getMCollection().countDocuments(Filters.eq(SingleTypeInfo._IS_URL_PARAM, true));
        assertEquals(count,2);

        SingleTypeInfo  singleTypeInfo = SingleTypeInfoDao.instance.findOne(Filters.eq(SingleTypeInfo._PARAM, "host"));
        assertNotNull(singleTypeInfo);
        assertEquals(singleTypeInfo.getUrl(), "/v1/payments/STRING/callback/STRING");
    }



    @Test
    public void testHarIPMerging() {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "https://stage.akto.bigtech";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int i=0; i< 11; i++) { // 11 because the threshold is 10 for string merging
            urls.add(url + "-"+i + ".io/" + "books");
        }
        for (String c: urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams, false, true, null);
        parser.apiCatalogSync.syncWithDB(false, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
        APICatalogSync.mergeUrlsAndSave(123,true, false, parser.apiCatalogSync.existingAPIsInDb, false, false);
        parser.apiCatalogSync.buildFromDB(false, true);

        APICatalog dbState = parser.apiCatalogSync.getDbState(123);
        assertEquals(urls.size(), dbState.getStrictURLToMethods().size());
        assertEquals(0, dbState.getTemplateURLToMethods().size());

    }
    @Test
    public void testMultipleLongMerging() {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "/api";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        long maxInt = Integer.MAX_VALUE;

        HttpResponseParams resp = createSampleParams("user1", url + "/books/" + (maxInt + 1) + "/cars/" + (maxInt + 3));
        responseParams.add(resp);

        parser.syncFunction(responseParams, true, true, null);
        parser.apiCatalogSync.syncWithDB(true, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);

        Map<URLTemplate, RequestTemplate> templateURLToMethods = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        assertEquals(1, templateURLToMethods.size());

        URLTemplate urlTemplate = (URLTemplate)  templateURLToMethods.keySet().toArray()[0];
        assertEquals("/api/books/INTEGER/cars/INTEGER", urlTemplate.getTemplateString());

        parser.syncFunction(responseParams, true, true, null);
        parser.apiCatalogSync.syncWithDB(true, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
    }

    @Test
    public void testMultipleFloatMerging() {
        testInitializer();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, true);
        String url = "/api";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        float val = new Float("1.3");

        HttpResponseParams resp = createSampleParams("user1", url + "/books/" + (val+ 1) + "/cars/" + (val+ 3));
        responseParams.add(resp);

        parser.syncFunction(responseParams, true, true, null);
        parser.apiCatalogSync.syncWithDB(true, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);

        Map<URLTemplate, RequestTemplate> templateURLToMethods = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        assertEquals(1, templateURLToMethods.size());

        URLTemplate urlTemplate = (URLTemplate)  templateURLToMethods.keySet().toArray()[0];
        assertEquals("/api/books/FLOAT/cars/FLOAT", urlTemplate.getTemplateString());

        parser.syncFunction(responseParams, true, true, null);
        parser.apiCatalogSync.syncWithDB(true, true, SyncLimit.noLimit, SyncLimit.noLimit, SyncLimit.noLimit, Source.HAR);
    }

}
