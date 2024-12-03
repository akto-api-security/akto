package com.akto.parsers;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.*;

import com.akto.MongoBasedTest;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.*;
import com.akto.dto.type.SingleTypeInfo.SubType;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.dto.type.URLMethods.Method;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import com.mongodb.client.model.Filters;
import org.junit.Test;

public class TestDump2 extends MongoBasedTest {
    private final int ACCOUNT_ID = 1_000_000;

    public void testInitializer(){
        Context.accountId.set(ACCOUNT_ID);
        Map<String, AktoDataType> aktoDataTypeMap = new HashMap<>();
        aktoDataTypeMap.put("JWT", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("PHONE_NUMBER", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("CREDIT_CARD", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("IP_ADDRESS", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("EMAIL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("SSN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("UUID", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("URL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(ACCOUNT_ID);
        if (info == null) {
            info = new AccountDataTypesInfo();
        }
        info.setAktoDataTypeMap(aktoDataTypeMap);
        SingleTypeInfo.getAccountToDataTypesInfo().put(ACCOUNT_ID, info);
    }
    public static String createSimpleResponsePayload() {
        BasicDBObject ret = new BasicDBObject();

        ret.append("a1", 1).append("b1", new BasicDBObject().append("a2", "some string").append("b2", "some number"));

        return ret.toJson();
    }

    public static String createSimpleRequestPayload() {
        BasicDBObject ret = new BasicDBObject();

        ret.append("id", 1).append("startDate", "some string");

        return ret.toJson();
    }

    public static List<String> createList(String s) {
        List<String> ret = new ArrayList<>();
        ret.add(s);
        return ret;
    }

    public static HttpResponseParams createSampleParams(String userId, String url) {
        HttpResponseParams ret = new HttpResponseParams();
        ret.type = "HTTP/1.1";
        ret.accountId = Context.accountId.get()+"";
        ret.statusCode = 200;
        ret.status = "OK";
        ret.headers = new HashMap<>();
        ret.headers.put("Access-Token", createList(" eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6ImFua3VzaEBnbWFpbC5jb20iLCJpYXQiOjE2MzQ3NTc0NzIsImV4cCI6MTYzNDc1ODM3Mn0.s_UihSzOlEY9spECqSS9SaTPxygn26YodRkYZKHmNwVnVkeppT5mQYKlwiUnjpKYIHxi2a82I50c0FbJKnTTk1Z5aYcT3t8GXUar_DaaEiv3eZZcZiqeOQSnPkP_c4nhC7Fqaq4g03p4Uj_7W5qvUAkTjHOCViwE933rmfX7tZA27o-9-b_ZKXYsTLfk-FjBV7f3piHmRo88j0WpkvuQc8LwcsoUq6yPclVuDsz9YHkvM1B33_QGdZ7nGz47M33tyLXqZyeF4qsnewkOOU6vCiDnM_eqbJghbZLSqP3Ut3lfA54BlAZ-HB5gLv-2HR0m_R2thDGXXE_G_onS-ZDB6A"));
        ret.headers.put("Content-Type", createList(" application/json;charset=utf-8"));
        ret.headers.put("Content-Length", createList(" 762"));
        ret.headers.put("Server", createList(" Jetty(9.4.42.v20210604)"));
                
        ret.setPayload(createSimpleResponsePayload());
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
        ret.requestParams.setPayload(createSimpleRequestPayload());
        ret.requestParams.setApiCollectionId(123);
        ret.setOrig(ret.toString());
        return ret;
    }

    @Test
    public void testHappyPath() {
        String message = " {\"akto_account_id\":\"1000000\",\"contentType\":\"application/json;charset=utf-8\",\"ip\":\"49.32.227.133:60118\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{\\\"Accept\\\":[\\\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\\\"],\\\"Accept-Encoding\\\":[\\\"gzip, deflate\\\"],\\\"Accept-Language\\\":[\\\"en-US,en;q=0.9,mr;q=0.8\\\"],\\\"Cache-Control\\\":[\\\"no-cache\\\"],\\\"Connection\\\":[\\\"keep-alive\\\"],\\\"Dnt\\\":[\\\"1\\\"],\\\"Pragma\\\":[\\\"no-cache\\\"],\\\"Upgrade-Insecure-Requests\\\":[\\\"1\\\"],\\\"User-Agent\\\":[\\\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.55 Safari/537.36\\\"]}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Type\\\":[\\\"application/json;charset=utf-8\\\"]}\",\"responsePayload\":\"{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"}}\\n\",\"status\":\"null\",\"statusCode\":\"201\",\"time\":\"1638940067\",\"type\":\"HTTP/1.1\"}";
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(message);
        } catch (Exception e) {
            assertEquals(2, 1);
            return;
        }

        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 5, true);

        aggr.addURL(httpResponseParams);
        sync.computeDelta(aggr, false, 0, false);
        APICatalogSync.DbUpdateReturn dbUpdateReturn = sync.getDBUpdatesForParams(sync.getDelta(0), sync.getDbState(0), false, false, Source.HAR);
        assertEquals(15, dbUpdateReturn.bulkUpdatesForSingleTypeInfo.size());
        assertEquals(2, sync.getDBUpdatesForTraffic(0, sync.getDelta(0)).size());        
        assertEquals(1, sync.getDBUpdatesForSampleData(0, sync.getDelta(0), sync.getDbState(0),true, false, false).size());
    }


    public void simpleTestForSingleCollection(int collectionId, APICatalogSync sync) {
        {
            String url = "https://someapi.com/link1";
            HttpResponseParams resp = createSampleParams("user1", url);

            URLAggregator aggr = new URLAggregator();

            aggr.addURL(resp);
            sync.computeDelta(aggr, false, collectionId, false);

            Map<URLStatic, RequestTemplate> urlMethodsMap = sync.getDelta(collectionId).getStrictURLToMethods();
            assertEquals(1, urlMethodsMap.size());

            Method method = Method.fromString(resp.getRequestParams().getMethod());
            RequestTemplate reqTemplate = urlMethodsMap.get(new URLStatic(resp.getRequestParams().getURL(), method));

            assertEquals(1, reqTemplate.getUserIds().size());
            assertEquals(2, reqTemplate.getParameters().size());

            RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
            assertEquals(1, respTemplate.getUserIds().size());
            assertEquals(3, respTemplate.getParameters().size());
            APICatalogSync.DbUpdateReturn dbUpdateReturn = sync.getDBUpdatesForParams(sync.getDelta(collectionId), sync.getDbState(collectionId), false, false, Source.HAR);
            assertEquals(24, dbUpdateReturn.bulkUpdatesForSingleTypeInfo.size());
            assertEquals(2, sync.getDBUpdatesForTraffic(collectionId, sync.getDelta(collectionId)).size());
        }
    }

    @Test
    public void simpleTest() {
        testInitializer();
        APICatalogSync sync = new APICatalogSync("access-token", 5, true);
        simpleTestForSingleCollection(0, sync);
        simpleTestForSingleCollection(1, sync);
        simpleTestForSingleCollection(2, sync);
        assertEquals(24, sync.getDBUpdatesForParams(sync.getDelta(0), sync.getDbState(0),false, false, Source.HAR).bulkUpdatesForSingleTypeInfo.size());
        assertEquals(24, sync.getDBUpdatesForParams(sync.getDelta(1), sync.getDbState(1),false, false, Source.HAR).bulkUpdatesForSingleTypeInfo.size());
        assertEquals(24, sync.getDBUpdatesForParams(sync.getDelta(2), sync.getDbState(2),false, false, Source.HAR).bulkUpdatesForSingleTypeInfo.size());
    }

    @Test
    public void testEndToEnd() throws Exception {
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();
        SensitiveSampleDataDao.instance.getMCollection().drop();
        TrafficInfoDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();

        // requestHeaders: 2
        // responseHeaders: 1
        // requestPayload: 6
        // responsePayload: 6
        // total: 15
        String host = "company.io";
        int apiCollectionId = host.hashCode();
        String url = "/api/books";
        String method = "POST";

        String message = "{\n" +
                "  \"akto_account_id\": \"" + Context.accountId.get() + "\",\n" +
                "  \"path\": \"" + url + "\",\n" +
                "  \"requestHeaders\": \"{'host': '" + host + "', 'user-agent': 'chrome' }\",\n" +
                "  \"responseHeaders\": \"{'token': 'token'}\",\n" +
                "  \"method\": \"" + method + "\",\n" +
                "  \"requestPayload\": \"{'user_req': 'user1_req', 'friends_req': [{'name_req':'friend1_req'},{'name_req':'friend2_req'}], 'role_req': {'name_req': 'admin_req', 'accesses_req': [{'billing_req': true, 'user_req' : 'true'}] }, 'gifts_req': [1,2,3] }\",\n" +
                "  \"responsePayload\": \"{'user_resp': 'user1_resp', 'friends_resp': [{'name_resp':'friend1_resp'},{'name_resp':'friend2_resp'}], 'role_resp': {'name_resp': 'admin_resp', 'accesses_resp': [{'billing_resp': true, 'user_resp' : 'true'}] }, 'gifts_resp': [1,2,3] }\",\n" +
                "  \"ip\": \"0.0.0.0\",\n" +
                "  \"time\": \"1721382413\",\n" +
                "  \"statusCode\": \"200\",\n" +
                "  \"type\": \"type\",\n" +
                "  \"status\": \"status\",\n" +
                "  \"contentType\": \"contentType\",\n" +
                "  \"source\": \"MIRRORING\",\n" +
                "  \"akto_vxlan_id\": \"0\"\n" +
                "}\n";

        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(message);
        HttpCallParser httpCallParser = new HttpCallParser("",0,0, 0, true);
        httpCallParser.syncFunction(Collections.singletonList(httpResponseParams), true, true, new AccountSettings());

        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(15, singleTypeInfos.size());

        SingleTypeInfo singleTypeInfo = generateSTI(apiCollectionId, url, method, SingleTypeInfo.GENERIC, "user_req", -1, false);
        SingleTypeInfo info = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(singleTypeInfo));
        assertNotNull(info);

        singleTypeInfo = generateSTI(apiCollectionId, url, method, SingleTypeInfo.GENERIC, "friends_req#$#name_req", -1, false);
        info = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(singleTypeInfo));
        assertNotNull(info);

        singleTypeInfo = generateSTI(apiCollectionId, url, method, SingleTypeInfo.GENERIC, "role_req#name_req", -1, false);
        info = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(singleTypeInfo));
        assertNotNull(info);

        singleTypeInfo = generateSTI(apiCollectionId, url, method, SingleTypeInfo.TRUE, "role_req#accesses_req#$#billing_req", -1, false);
        info = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(singleTypeInfo));
        assertNotNull(info);

        singleTypeInfo = generateSTI(apiCollectionId, url, method, SingleTypeInfo.GENERIC, "role_req#accesses_req#$#user_req", -1, false);
        info = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(singleTypeInfo));
        assertNotNull(info);

        singleTypeInfo = generateSTI(apiCollectionId, url, method, SingleTypeInfo.INTEGER_32, "gifts_req#$", -1, false);
        info = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(singleTypeInfo));
        assertNotNull(info);

        singleTypeInfo = generateSTI(apiCollectionId, url, method, SingleTypeInfo.TRUE, "role_resp#accesses_resp#$#billing_resp", 200, false);
        info = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(singleTypeInfo));
        assertNotNull(info);

        // sample data
        long sampleDataCount = SampleDataDao.instance.getMCollection().estimatedDocumentCount();
        assertEquals(1, sampleDataCount);
        SampleData sampleData = SampleDataDao.instance.findOne(Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.url", url),
                Filters.eq("_id.method", method)
        ));
        assertEquals(1, sampleData.getSamples().size());

        // api info
        long apiInfoCount = ApiInfoDao.instance.getMCollection().estimatedDocumentCount();
        assertEquals(1, apiInfoCount);
        ApiInfo apiInfo = ApiInfoDao.instance.findOne(Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.url", url),
                Filters.eq("_id.method", method)
        ));
        assertTrue(apiInfo.getLastSeen() > 0);

        // api collection
        long apiCollectionsCount = ApiCollectionsDao.instance.getMCollection().estimatedDocumentCount();
        assertEquals(1, apiCollectionsCount);
        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq("_id", apiCollectionId));
        assertEquals(host, apiCollection.getHostName());

    }

    static SingleTypeInfo generateSTI(int apiCollectionId, String url, String method, SubType subType, String param, int responseCode, boolean isHeader) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, method, responseCode, isHeader, param, subType, apiCollectionId, false);
        return new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, 0,0, new CappedSet<>(), null, 0,0);
    }


    @Test
    public void getParamsTest() {
        String baseurl = "https://someapi.com/example";
        String url = baseurl+"?p1=v1&p2=v2&p3=%7B%22a%22%3A1%2C%22b%22%3A%5B%7B%22c%22%3A1%2C%22d%22%3A1%7D%5D%7D";
        HttpResponseParams resp = createSampleParams("user1", url);

        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 5, true);

        aggr.addURL(resp);
        sync.computeDelta(aggr, false, 0, false);

        Map<URLStatic, RequestTemplate> urlMethodsMap = sync.getDelta(0).getStrictURLToMethods();
        assertEquals(1, urlMethodsMap.size());

        assertEquals(baseurl, urlMethodsMap.keySet().iterator().next().getUrl());
        RequestTemplate reqTemplate = urlMethodsMap.get(new URLStatic(baseurl, Method.fromString(resp.getRequestParams().method)));
        assertEquals(1, reqTemplate.getUserIds().size());
        assertEquals(5, reqTemplate.getParameters().size());

        assertEquals(2, sync.getDBUpdatesForTraffic(0, sync.getDelta(0)).size());
    }


    @Test
    public void urlsTest() {
        Set<HttpResponseParams> responses = new HashSet<>();
        String url = "https://someapi.com/link1";

        HttpResponseParams resp = createSampleParams("user1", url);
        responses.add(resp);
        responses.add(createSampleParams("user2", url));
        responses.add(createSampleParams("user3", url));
        responses.add(createSampleParams("user4", url));
        responses.add(createSampleParams("user5", url));

        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 5, true);
        Method method = Method.fromString(resp.getRequestParams().getMethod());
        aggr.addURL(responses, new URLStatic(resp.getRequestParams().getURL(), method));
        sync.computeDelta(aggr, false, 0, false);

        Map<URLStatic, RequestTemplate> urlMethodsMap = sync.getDelta(0).getStrictURLToMethods();
        assertEquals(1, urlMethodsMap.size());

        RequestTemplate reqTemplate = urlMethodsMap.get(new URLStatic(resp.getRequestParams().getURL(), method));
        assertEquals(5, reqTemplate.getUserIds().size());
        assertEquals(2, reqTemplate.getParameters().size());

        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
        assertEquals(5, respTemplate.getUserIds().size());
        assertEquals(3, respTemplate.getParameters().size());
        assertEquals(2, sync.getDBUpdatesForTraffic(0, sync.getDelta(0)).size());
    }

    private String createPayloadWithRepetitiveKeys(String i) {
        BasicDBObject ret = new BasicDBObject();
        BasicDBList list = new BasicDBList();

        ret.put("a", list);

        list.add(new BasicDBObject("i"+i, i));

        return ret.toJson();
    }

    @Test
    public void repetitiveKeyTest() {
        String url = "https://someapi.com/link1";

        Set<HttpResponseParams> responseParams = new HashSet<>();

        HttpResponseParams resp = createSampleParams("user1", url);
        resp.setPayload(createPayloadWithRepetitiveKeys("1"));
        responseParams.add(resp);

        for (int i = 2 ; i < 30; i ++) {
            resp = createSampleParams("user"+i, url);
            resp.setPayload(createPayloadWithRepetitiveKeys(""+i));
            responseParams.add(resp);
        }

        Method method = Method.fromString(resp.getRequestParams().getMethod());

        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 5, true);

        aggr.addURL(responseParams, new URLStatic(url, method));
        sync.computeDelta(aggr, false, 0, false);

        Map<URLStatic, RequestTemplate> urlMethodsMap = sync.getDelta(0).getStrictURLToMethods();
        assertEquals(1, urlMethodsMap.size());

        RequestTemplate reqTemplate = urlMethodsMap.get(new URLStatic(resp.getRequestParams().getURL(), method));
        assertEquals(10, reqTemplate.getUserIds().size());
        assertEquals(2, reqTemplate.getParameters().size());

        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
        assertEquals(10, respTemplate.getUserIds().size());
        assertEquals(29, respTemplate.getParameters().size());

        respTemplate.tryMergeNodesInTrie(url, "POST", resp.statusCode, resp.getRequestParams().getApiCollectionId());
        // TODO: investigate and fix this
        // assertEquals(1, respTemplate.getParameters().size());

        List updates = sync.getDBUpdatesForParams(sync.getDelta(0), sync.getDbState(0), false, false, Source.HAR).bulkUpdatesForSingleTypeInfo;
    }

    @Test
    public void testURLMatch() {
        String url = "https://amazonpay.amazon.in/ap/signin?openid.return_to=https%3A%2F%2Famazonpay.amazon.in%2Fv1%2Finitiate-payment%3FredirectUrl%3Dhttps%253A%252F%252Fsandbox.juspay.in%252Fv2%252Fpay%252Fresponse-amazonpay%252Fmpl_qa%26payload%3DqpZtIG4rQa0Ru6HR1RSOlDtxA61%252BVNb0WLLwzMgLnsStcLU9nD%252FbQ2XZLKvWNqdViQ5YZujSRCPagD%252FVME0JWyl3fhlh1s69%252FCaKfQiDnTg42Ofgqxj5CN86Mv45MhbmzJFVZ0JRM1yECFrLkdLnGJOr4c%252FZQoWJ3CeRGl3XcYF807JC%252F0iidvC62N3qQm97ketMo9af%252FQjTL4NTOkzPVwv1bNeI%252F8Ea5uQxWtBdZATV6ogzHgFMeM4tzcbJY5E0XxeTjhJ1SijDXLtgSOoERFCPxLzudyb9%252B2IoF9cxNWb8yi9RJuqn%252BMvU4BC%252FFrgJaLn9DJ9r4RE%253D%26iv%3DE2XGT7As7Kdo50sj%26key%3DFoXRG7XfML%252B9UAO88iH7hfSyNfNbhgdPT7d3%252F8G%252B9sqovuZOct4ZNf88yR%252FtgbRedAsVG%252BZJHjeOHlKlFZoomrm2IWweysOvMQrDyIL35hT2NUoG4ZCG94ZFC2b7TII4XEFId%252Bkpj0qMUreKQafh0NXu2jg58ogzAWgpU5uskZBUg3WDITJMQXdGqaOPO6gooIEtKmLV6gQx4%252F%252B9K18XKofG2fZQ5bNlvpuFbyn4%252Brs3J%252BtJxPsxnuSiPrJwGEk36rDjhW1LOgssrAAUv%252BSfExHQ3KfmFnBdbK2rWM0CkwgYZ95cteVxRDl7f7SdpgBCmrlVVcPvM2moUiWOTW9aHA%253D%253D&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.assoc_handle=amazon_pay_in_mobile&openid.mode=checkid_setup&marketPlaceId=A3FDG49KKM823Y&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&pageId=amzn_pay_in&openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0&openid.pape.max_auth_age=5400&siteState=clientContext%3D257-1861896-6931844%2CsourceUrl%3Dhttps%253A%252F%252Famazonpay.amazon.in%252Fv1%252Finitiate-payment%253FredirectUrl%253Dhttps%25253A%25252F%25252Fsandbox.juspay.in%25252Fv2%25252Fpay%25252Fresponse-amazonpay%25252Fmpl_qa%2526payload%253DqpZtIG4rQa0Ru6HR1RSOlDtxA61%25252BVNb0WLLwzMgLnsStcLU9nD%25252FbQ2XZLKvWNqdViQ5YZujSRCPagD%25252FVME0JWyl3fhlh1s69%25252FCaKfQiDnTg42Ofgqxj5CN86Mv45MhbmzJFVZ0JRM1yECFrLkdLnGJOr4c%25252FZQoWJ3CeRGl3XcYF807JC%25252F0iidvC62N3qQm97ketMo9af%25252FQjTL4NTOkzPVwv1bNeI%25252F8Ea5uQxWtBdZATV6ogzHgFMeM4tzcbJY5E0XxeTjhJ1SijDXLtgSOoERFCPxLzudyb9%25252B2IoF9cxNWb8yi9RJuqn%25252BMvU4BC%25252FFrgJaLn9DJ9r4RE%25253D%2526iv%253DE2XGT7As7Kdo50sj%2526key%253DFoXRG7XfML%25252B9UAO88iH7hfSyNfNbhgdPT7d3%25252F8G%25252B9sqovuZOct4ZNf88yR%25252FtgbRedAsVG%25252BZJHjeOHlKlFZoomrm2IWweysOvMQrDyIL35hT2NUoG4ZCG94ZFC2b7TII4XEFId%25252Bkpj0qMUreKQafh0NXu2jg58ogzAWgpU5uskZBUg3WDITJMQXdGqaOPO6gooIEtKmLV6gQx4%25252F%25252B9K18XKofG2fZQ5bNlvpuFbyn4%25252Brs3J%25252BtJxPsxnuSiPrJwGEk36rDjhW1LOgssrAAUv%25252BSfExHQ3KfmFnBdbK2rWM0CkwgYZ95cteVxRDl7f7SdpgBCmrlVVcPvM2moUiWOTW9aHA%25253D%25253D%2Csignature%3Dj2BY7ki63y4rphlJZ6WQZhGj2F5fMyEj3D";
        assertTrue(KeyTypes.patternToSubType.get(SingleTypeInfo.URL).matcher(url).matches());
    }

    @Test
    public void test2() {
        String[] urlTokens = APICatalogSync.tokenize("https://qapi.mpl.live:443/{param_STRING}/pending-invites");
        urlTokens[3] = null;
        SuperType[] types = new SuperType[urlTokens.length];
        types[3] = SuperType.STRING;
        URLTemplate urlTemplate = new URLTemplate(urlTokens, types, Method.POST);
        assertFalse(urlTemplate.match("https://qapi.mpl.live:443/kyc/for-payments", Method.POST));

        assertTrue(urlTemplate.match("https://qapi.mpl.live:443/12312/pending-invites", Method.POST));
        assertFalse(urlTemplate.match("https://qapi.mpl.live:443/12312/sdfdasfa", Method.POST));
        assertFalse(urlTemplate.match("https://qapi.mpl.live:443/abc/pending-invites", Method.GET));
    }
}
