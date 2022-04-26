package com.akto.parsers;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.*;
import com.akto.runtime.APICatalogSync;
import org.junit.Test;

import java.util.*;

import static com.akto.parsers.TestDump2.createSampleParams;
import static org.junit.Assert.assertEquals;

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
}
