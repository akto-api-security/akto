package com.akto.parsers;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.*;
import com.akto.runtime.APICatalogSync;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.*;

import static com.akto.parsers.TestDump2.createList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestMergingNew extends MongoBasedTest {

    @Test
    public void testMultipleIntegerMerging() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);
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

        parser.syncFunction(responseParams.subList(0,10));
        parser.apiCatalogSync.syncWithDB();
        parser.syncFunction(responseParams.subList(10,15));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(0, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(15,20));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(0, getStaticURLsSize(parser));



        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDelta(123).getTemplateURLToMethods();


        assertEquals(1, urlTemplateMap.size());
        assertEquals(0, getStaticURLsSize(parser));

    }

    public int getStaticURLsSize(HttpCallParser parser) {
        Map<URLStatic, RequestTemplate> urlStaticMap = parser.apiCatalogSync.getDelta(123).getStrictURLToMethods();

        return urlStaticMap.size();
    }

    @Test
    public void testUUIDForceMerge() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);
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

        parser.syncFunction(responseParams.subList(0,1));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(1, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(1,2));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(0, getStaticURLsSize(parser));

//        parser.syncFunction(responseParams.subList(28,33));
//        parser.apiCatalogSync.syncWithDB();
//        assertEquals(0, getStaticURLsSize(parser));
    }

    @Test
    public void testParameterizedURLsTestString() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);
        String url = "link/";
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

        parser.syncFunction(responseParams.subList(0,23));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(23, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(23,28));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(0, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(28,33));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(0, getStaticURLsSize(parser));
    }

    @Test
    public void testEmptyResponsePayload() throws Exception {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();

        String a = "{\"path\": \"http://killdill.mpl.internal:8080/1.0/kb/paymentGateways/hosted/form/bbb-bbbb-bbb?paymentMethodId=qq-qqq-qqq\", \"method\": \"POST\", \"type\": \"HTTP/1.1\", \"requestHeaders\": \"{\\\"X-Killbill-ApiKey\\\": \\\"mplgaming\\\", \\\"Authorization\\\": \\\"Basic somerandom=\\\", \\\"X-Killbill-ApiSecret\\\": \\\"something\\\", \\\"Accept\\\": \\\"application/json\\\", \\\"X-MPL-COUNTRYCODE\\\": \\\"IN\\\", \\\"X-Killbill-CreatedBy\\\": \\\"test-payment\\\", \\\"Content-type\\\": \\\"application/json\\\"}\", \"requestPayload\": \"{\\\"formFields\\\":[{\\\"key\\\":\\\"amount\\\",\\\"value\\\":\\\"125.000\\\"},{\\\"key\\\":\\\"netAmount\\\",\\\"value\\\":\\\"125.000\\\"},{\\\"key\\\":\\\"currency\\\",\\\"value\\\":\\\"INR\\\"},{\\\"key\\\":\\\"orderId\\\",\\\"value\\\":\\\"ASGARD\\\"},{\\\"key\\\":\\\"paymentMethodId\\\",\\\"value\\\":\\\"zzzz-zzz-zzz-zzzz-zzzz\\\"},{\\\"key\\\":\\\"mobileNumber\\\",\\\"value\\\":\\\"+917021916328\\\"},{\\\"key\\\":\\\"countryCode\\\",\\\"value\\\":\\\"IN\\\"},{\\\"key\\\":\\\"chargeDetails\\\",\\\"value\\\":\\\"{\\\\\\\"charges\\\\\\\":[],\\\\\\\"totalCharges\\\\\\\":0,\\\\\\\"totalChargesLC\\\\\\\":0}\\\"},{\\\"key\\\":\\\"pegRate\\\",\\\"value\\\":\\\"1.0000\\\"},{\\\"key\\\":\\\"extraInfo\\\",\\\"value\\\":\\\"{\\\\\\\"paymentMode\\\\\\\":\\\\\\\"NB_ICICI\\\\\\\",\\\\\\\"additionalPluginInfo\\\\\\\":\\\\\\\"{\\\\\\\\\\\\\\\"merchantId\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"mpl_qa\\\\\\\\\\\\\\\",\\\\\\\\\\\\\\\"clientId\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"mplgaming\\\\\\\\\\\\\\\"}\\\\\\\",\\\\\\\"paymentModePluginInfo\\\\\\\":\\\\\\\"{\\\\\\\\\\\\\\\"code\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"NB_ICICI\\\\\\\\\\\\\\\"}\\\\\\\",\\\\\\\"paymentMethodType\\\\\\\":\\\\\\\"netbanking\\\\\\\",\\\\\\\"paymentFlow\\\\\\\":\\\\\\\"JP2_AT_R4\\\\\\\"}\\\"},{\\\"key\\\":\\\"appVersion\\\",\\\"value\\\":\\\"1000174\\\"},{\\\"key\\\":\\\"savedPaymentDetails\\\",\\\"value\\\":\\\"{}\\\"},{\\\"key\\\":\\\"appType\\\",\\\"value\\\":\\\"CASH\\\"},{\\\"key\\\":\\\"savedPaymentDetails\\\",\\\"value\\\":\\\"{}\\\"}]}\", \"statusCode\": \"200\", \"responseHeaders\": \"{\\\"Date\\\": \\\"Mon, 18 Apr 2022 13:05:16 GMT\\\", \\\"Content-Type\\\": \\\"application/json\\\", \\\"Transfer-Encoding\\\": \\\"chunked\\\", \\\"Connection\\\": \\\"keep-alive\\\", \\\"Server\\\": \\\"Apache-Coyote/1.1\\\", \\\"Access-Control-Allow-Origin\\\": \\\"*\\\", \\\"Access-Control-Allow-Methods\\\": \\\"GET, POST, DELETE, PUT, OPTIONS\\\", \\\"Access-Control-Allow-Headers\\\": \\\"Authorization,Content-Type,Location,X-Killbill-ApiKey,X-Killbill-ApiSecret,X-Killbill-Comment,X-Killbill-CreatedBy,X-Killbill-Pagination-CurrentOffset,X-Killbill-Pagination-MaxNbRecords,X-Killbill-Pagination-NextOffset,X-Killbill-Pagination-NextPageUri,X-Killbill-Pagination-TotalNbRecords,X-Killbill-Reason\\\", \\\"Access-Control-Expose-Headers\\\": \\\"Authorization,Content-Type,Location,X-Killbill-ApiKey,X-Killbill-ApiSecret,X-Killbill-Comment,X-Killbill-CreatedBy,X-Killbill-Pagination-CurrentOffset,X-Killbill-Pagination-MaxNbRecords,X-Killbill-Pagination-NextOffset,X-Killbill-Pagination-NextPageUri,X-Killbill-Pagination-TotalNbRecords,X-Killbill-Reason\\\", \\\"Access-Control-Allow-Credentials\\\": \\\"true\\\"}\", \"status\": \"OK\", \"responsePayload\": \"\", \"ip\": \"\", \"time\": \"1650287116\", \"akto_account_id\": \"1000000\", \"akto_vxlan_id\": 123, \"source\": \"OTHER\"}";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(a);
            String[] s = httpResponseParams.requestParams.url.split("/");
            s[s.length-1] = "param"+i;
            httpResponseParams.requestParams.url = String.join("/", s) + "?paymentMethodId=qq-qqq-qqq";
            responseParams.add(httpResponseParams);
        }

        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);

        parser.syncFunction(responseParams.subList(0,10));
        parser.apiCatalogSync.syncWithDB();
        parser.syncFunction(responseParams.subList(10,25));
        parser.apiCatalogSync.syncWithDB();
        parser.syncFunction(responseParams.subList(25,30));
        parser.apiCatalogSync.syncWithDB();


        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDelta(0).getTemplateURLToMethods();
        Map<URLStatic, RequestTemplate> urlStaticMap = parser.apiCatalogSync.getDelta(0).getStrictURLToMethods();

        assertEquals(urlTemplateMap.size(), 1);
        assertEquals(urlStaticMap.size(), 0);

    }

    @Test
    public void testStrictIntoTemplate() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);
        String url = "api/books/";
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
        URLTemplate urlTemplate = APICatalogSync.tryMergeUrls(new URLStatic(responseParams.get(0).requestParams.url, URLMethods.Method.GET), new URLStatic(responseParams.get(1).requestParams.url, URLMethods.Method.GET));
        parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods().put(urlTemplate, new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder(new HashMap<>())));

        parser.syncFunction(responseParams.subList(0,15));
        parser.apiCatalogSync.syncWithDB();
        parser.syncFunction(responseParams.subList(15,25));
        parser.apiCatalogSync.syncWithDB();
        parser.syncFunction(responseParams.subList(25,30));
        parser.apiCatalogSync.syncWithDB();


        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDelta(123).getTemplateURLToMethods();
        Map<URLStatic, RequestTemplate> urlStaticMap = parser.apiCatalogSync.getDelta(123).getStrictURLToMethods();

        assertEquals(1,urlTemplateMap.size());
        assertEquals(0, urlStaticMap.size());
    }

    @Test
    public void test20percentCondition() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);
        String url = "api/";
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

        parser.syncFunction(responseParams.subList(0,23));
        parser.apiCatalogSync.syncWithDB();
        parser.syncFunction(responseParams.subList(23,28));
        parser.apiCatalogSync.syncWithDB();
        parser.syncFunction(responseParams.subList(28,33));
        parser.apiCatalogSync.syncWithDB();


        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDelta(123).getTemplateURLToMethods();
        Map<URLStatic, RequestTemplate> urlStaticMap = parser.apiCatalogSync.getDelta(123).getStrictURLToMethods();

        assertEquals(1,urlTemplateMap.keySet().size());
        assertEquals(0, urlStaticMap.keySet().size());

        boolean merged = true;
        for (SingleTypeInfo singleTypeInfo: SingleTypeInfoDao.instance.findAll(new BasicDBObject())) {
            if (!singleTypeInfo.getUrl().equals("api/STRING")) {
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

        while (start < 10) {
            ret.append(k+"_"+start, "Avneesh");
            start += 1;
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
        HttpCallParser parser = new HttpCallParser("access-token", 10, 40, 10);

        /* tryMergingWithKnownStrictURLs - put in delta-static */
        parser.syncFunction(responseParams);
        assertTrue(parser.getSyncCount() == 0);

        /* processKnownStaticURLs */
        parser.syncFunction(responseParams);

        /* tryMergingWithKnownStrictURLs - merge with delta-static */
        responseParams.add(TestDump2.createSampleParams("user" + 2, url + 2));
        responseParams.add(TestDump2.createSampleParams("user" + 3, url + 3));

        /* tryMergingWithKnownStrictURLs - merge with delta-template */
        responseParams.add(TestDump2.createSampleParams("user" + 4, url + 4));
        parser.syncFunction(responseParams);
        assertTrue(parser.getSyncCount() == 0);

        /* tryMergingWithKnownTemplates */
        parser.syncFunction(responseParams);
        assertTrue(parser.getSyncCount() == 0);

        /* tryMergingWithKnownStrictURLs - merge with Db url */
        url = "payment/";
        responseParams = new ArrayList<>();
        responseParams.add(TestDump2.createSampleParams("user" + 2, url + 2));
        responseParams.get(0).setSource(HttpResponseParams.Source.HAR);
        parser.syncFunction(responseParams);
        responseParams = new ArrayList<>();
        responseParams.add(TestDump2.createSampleParams("user" + 3, url + 3));

        /* tryMergingWithKnownStrictURLs - merge with Db url - template already exists in delta */
        responseParams.add(TestDump2.createSampleParams("user" + 4, url + 4));
        responseParams.get(0).setSource(HttpResponseParams.Source.HAR);
        parser.syncFunction(responseParams);
    }

    @Test
    public void testUrlParamSingleTypeInfoAndValues() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);
        String url = "api/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int i=0; i< 100; i++) {
            urls.add(url + i + "/books/" + (i+1) + "/cars/" + (i+3));
        }
        for (String c: urls) {
            BasicDBObject ret = new BasicDBObject();
            ret.put("name", c);
            HttpRequestParams httpRequestParams = new HttpRequestParams("GET", c, "", new HashMap<>(), ret.toJson(), 123);
            HttpResponseParams resp = new HttpResponseParams("", 200,"", new HashMap<>(), ret.toJson(),httpRequestParams, 0,"0",false, HttpResponseParams.Source.MIRRORING,"", "");
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams.subList(0,10));
        parser.apiCatalogSync.syncWithDB();

        Map<URLTemplate, RequestTemplate> templateURLToMethods = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        for (URLTemplate urlTemplate: templateURLToMethods.keySet()) {
            RequestTemplate requestTemplate = templateURLToMethods.get(urlTemplate);
            List<SingleTypeInfo> singleTypeInfoList = requestTemplate.getAllTypeInfo();
            assertEquals(singleTypeInfoList.size(),2);
            for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
                assertEquals(singleTypeInfo.getValues().getElements().size(), 10);
            }
        }

        parser.syncFunction(responseParams.subList(10,55));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(0, getStaticURLsSize(parser));

        templateURLToMethods = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        for (URLTemplate urlTemplate: templateURLToMethods.keySet()) {
            RequestTemplate requestTemplate = templateURLToMethods.get(urlTemplate);
            List<SingleTypeInfo> singleTypeInfoList = requestTemplate.getAllTypeInfo();
            assertEquals(singleTypeInfoList.size(),5);
            for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
                if (singleTypeInfo.isUrlParam()) {
                    assertEquals(singleTypeInfo.getValues().getElements().size(), 45);
                } else {
                    assertEquals(singleTypeInfo.getValues().getElements().size(), 55);
                }
            }

        }

        parser.apiCatalogSync.syncWithDB();
        parser.apiCatalogSync.syncWithDB();

        templateURLToMethods = parser.apiCatalogSync.getDbState(123).getTemplateURLToMethods();
        for (URLTemplate urlTemplate: templateURLToMethods.keySet()) {
            RequestTemplate requestTemplate = templateURLToMethods.get(urlTemplate);
            List<SingleTypeInfo> singleTypeInfoList = requestTemplate.getAllTypeInfo();
            assertEquals(singleTypeInfoList.size(),5);
            for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
                if (singleTypeInfo.isUrlParam()) {
                    assertEquals(singleTypeInfo.getValues().getElements().size(), 45);
                } else {
                    assertEquals(singleTypeInfo.getValues().getElements().size(), 0);
                }
            }

        }

    }

    @Test
    public void testMinMaxAndLastSeenNew() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);
        String url = "api/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int i=0; i< 100; i++) {
            urls.add(url + i + "/books/" + (i+1) + "/cars/" + (i+3));
        }
        List<Float> floats = Arrays.asList(23.3F,-29F, 100F, -39F);
        int idx =0;
        for (String c: urls) {
            BasicDBObject ret = new BasicDBObject();
            ret.put("value", floats.get(idx%4));
            HttpRequestParams httpRequestParams = new HttpRequestParams("GET", c, "", new HashMap<>(), ret.toJson(), 123);
            HttpResponseParams resp = new HttpResponseParams("", 200,"", new HashMap<>(), ret.toJson(),httpRequestParams, 0,"0",false, HttpResponseParams.Source.MIRRORING,"", "");
            responseParams.add(resp);
            idx+=1;
        }

        parser.syncFunction(responseParams.subList(0,20));
        parser.apiCatalogSync.syncWithDB();

        parser.syncFunction(responseParams.subList(20,40));
        parser.apiCatalogSync.syncWithDB();
    }
}
