package com.akto.parsers;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.type.SingleTypeInfo.SubType;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.dto.type.URLMethods.Method;
import com.akto.parsers.HttpCallParser.HttpRequestParams;
import com.akto.parsers.HttpCallParser.HttpResponseParams;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestDump2 {


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

    public static void assertEquals(int actual, int expected) {
        Assertions.assertEquals(expected, actual);
    }

    public static void assertEquals(String actual, String expected) {
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testHappyPath() {
        String message = " {\"akto_account_id\":\"1000000\",\"contentType\":\"application/json;charset=utf-8\",\"ip\":\"49.32.227.133:60118\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{\\\"Accept\\\":[\\\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\\\"],\\\"Accept-Encoding\\\":[\\\"gzip, deflate\\\"],\\\"Accept-Language\\\":[\\\"en-US,en;q=0.9,mr;q=0.8\\\"],\\\"Cache-Control\\\":[\\\"no-cache\\\"],\\\"Connection\\\":[\\\"keep-alive\\\"],\\\"Dnt\\\":[\\\"1\\\"],\\\"Pragma\\\":[\\\"no-cache\\\"],\\\"Upgrade-Insecure-Requests\\\":[\\\"1\\\"],\\\"User-Agent\\\":[\\\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.55 Safari/537.36\\\"]}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Type\\\":[\\\"application/json;charset=utf-8\\\"]}\",\"responsePayload\":\"{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"}}\\n\",\"status\":\"null\",\"statusCode\":\"201\",\"time\":\"1638940067\",\"type\":\"HTTP/1.1\"}";
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(message);
        } catch (Exception e) {
            assertEquals(1,2);
            return;
        }

        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 5);

        aggr.addURL(httpResponseParams);
        sync.computeDelta(aggr, false, 0);
        
        assertEquals(sync.getDBUpdatesForParams(sync.getDelta(0), sync.getDbState(0)).size(), 15);        
        assertEquals(sync.getDBUpdatesForTraffic(0, sync.getDelta(0)).size(), 2);        
        assertEquals(sync.getDBUpdatesForSampleData(0, sync.getDelta(0)).size(), 1);        
    }


    public void simpleTestForSingleCollection(int collectionId, APICatalogSync sync) {
        {
            String url = "https://someapi.com/link1";
            HttpResponseParams resp = createSampleParams("user1", url);
        
            URLAggregator aggr = new URLAggregator();

            aggr.addURL(resp);
            sync.computeDelta(aggr, false, collectionId);

            Map<String, URLMethods> urlMethodsMap = sync.getDelta(collectionId).getStrictURLToMethods();

            assertEquals(urlMethodsMap.size(), 1);

            URLMethods urlMethods = urlMethodsMap.get(resp.getRequestParams().url);
            
            RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(Method.valueOf(resp.getRequestParams().method));
            assertEquals(reqTemplate.getUserIds().size(), 1);
            assertEquals(reqTemplate.getParameters().size(), 2);
            
            RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
            assertEquals(respTemplate.getUserIds().size(), 1);
            assertEquals(respTemplate.getParameters().size(), 3);

            assertEquals(sync.getDBUpdatesForParams(sync.getDelta(collectionId), sync.getDbState(collectionId)).size(), 24);
            assertEquals(sync.getDBUpdatesForTraffic(collectionId, sync.getDelta(collectionId)).size(), 2);        
        }        
    }

    @Test
    public void simpleTest() {
        APICatalogSync sync = new APICatalogSync("access-token", 5);
        simpleTestForSingleCollection(0, sync);
        simpleTestForSingleCollection(1, sync);
        simpleTestForSingleCollection(2, sync);
        assertEquals(sync.getDBUpdatesForParams(sync.getDelta(0), sync.getDbState(0)).size(), 24);
        assertEquals(sync.getDBUpdatesForParams(sync.getDelta(1), sync.getDbState(1)).size(), 24);
        assertEquals(sync.getDBUpdatesForParams(sync.getDelta(2), sync.getDbState(2)).size(), 24);
    }

    @Test
    public void getParamsTest() {
        String baseurl = "https://someapi.com/example";
        String url = baseurl+"?p1=v1&p2=v2&p3=%7B%22a%22%3A1%2C%22b%22%3A%5B%7B%22c%22%3A1%2C%22d%22%3A1%7D%5D%7D";
        HttpResponseParams resp = createSampleParams("user1", url);

        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 5);

        aggr.addURL(resp);
        sync.computeDelta(aggr, false, 0);

        Map<String, URLMethods> urlMethodsMap = sync.getDelta(0).getStrictURLToMethods();

        assertEquals(urlMethodsMap.size(), 1);

        URLMethods urlMethods = urlMethodsMap.get(baseurl);

        assertEquals(urlMethodsMap.keySet().iterator().next(), baseurl);
        RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(Method.valueOf(resp.getRequestParams().method));
        assertEquals(reqTemplate.getUserIds().size(), 1);
        assertEquals(reqTemplate.getParameters().size(), 5);

        System.out.println("done");
        assertEquals(sync.getDBUpdatesForTraffic(0, sync.getDelta(0)).size(), 2);        
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
        APICatalogSync sync = new APICatalogSync("access-token", 5);

        aggr.addURL(responses, resp.getRequestParams().getURL());
        sync.computeDelta(aggr, false, 0);

        Map<String, URLMethods> urlMethodsMap = sync.getDelta(0).getStrictURLToMethods();

        assertEquals(urlMethodsMap.size(), 1);
        URLMethods urlMethods = urlMethodsMap.get(resp.getRequestParams().url);
        
        RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(Method.valueOf(resp.getRequestParams().method));
        assertEquals(reqTemplate.getUserIds().size(), 5);
        assertEquals(reqTemplate.getParameters().size(), 2);
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
        assertEquals(respTemplate.getUserIds().size(), 5);
        assertEquals(respTemplate.getParameters().size(), 3);
        assertEquals(sync.getDBUpdatesForTraffic(0, sync.getDelta(0)).size(), 2);        
    }

    @Test
    public void testParameterizedURLsTest() {
        String url = "link/";
        HttpResponseParams resp = createSampleParams("user1", url+1);
        URLAggregator aggr = new URLAggregator();
        resp.requestParams.getHeaders().put("newHeader", new ArrayList<String>());
        aggr.addURL(resp);
        APICatalogSync sync = new APICatalogSync("access-token", 1);

        for (int i = 2; i <= 30; i ++ ) {
            aggr.addURL(createSampleParams("user"+i, url+i));
        }

        sync.computeDelta(aggr, true, 0);

        Map<URLTemplate, URLMethods> urlTemplateMap = sync.getDelta(0).getTemplateURLToMethods();

        assertEquals(urlTemplateMap.size(), 1);

        Map.Entry<URLTemplate, URLMethods> entry = urlTemplateMap.entrySet().iterator().next();

        assertEquals(entry.getKey().getTemplateString(), url+"INTEGER");

        RequestTemplate reqTemplate = entry.getValue().getMethodToRequestTemplate().get(Method.POST);

        assertEquals(reqTemplate.getUserIds().size(), 30);
        assertEquals(reqTemplate.getParameters().size(), 2);
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
        assertEquals(respTemplate.getUserIds().size(), 30);
        assertEquals(respTemplate.getParameters().size(), 3);
        assertEquals(sync.getDBUpdatesForTraffic(0, sync.getDelta(0)).size(), 0);        
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

        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 5);

        aggr.addURL(responseParams, url);
        sync.computeDelta(aggr, false, 0);

        Map<String, URLMethods> urlMethodsMap = sync.getDelta(0).getStrictURLToMethods();
        assertEquals(urlMethodsMap.size(), 1);

        URLMethods urlMethods = urlMethodsMap.get(resp.getRequestParams().url);
        
        RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(Method.valueOf(resp.getRequestParams().method));
        assertEquals(reqTemplate.getUserIds().size(), 10);
        assertEquals(reqTemplate.getParameters().size(), 2);
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
        assertEquals(respTemplate.getUserIds().size(), 10);
        assertEquals(respTemplate.getParameters().size(), 29);

        respTemplate.tryMergeNodesInTrie(url, "POST", resp.statusCode, resp.getRequestParams().getApiCollectionId());
        assertEquals(respTemplate.getParameters().size(), 1);

        List updates = sync.getDBUpdatesForParams(sync.getDelta(0), sync.getDbState(0));
        assertEquals(updates.size(), 22);
    }

    @Test
    public void testURLMatch() {
        String url = "https://amazonpay.amazon.in/ap/signin?openid.return_to=https%3A%2F%2Famazonpay.amazon.in%2Fv1%2Finitiate-payment%3FredirectUrl%3Dhttps%253A%252F%252Fsandbox.juspay.in%252Fv2%252Fpay%252Fresponse-amazonpay%252Fmpl_qa%26payload%3DqpZtIG4rQa0Ru6HR1RSOlDtxA61%252BVNb0WLLwzMgLnsStcLU9nD%252FbQ2XZLKvWNqdViQ5YZujSRCPagD%252FVME0JWyl3fhlh1s69%252FCaKfQiDnTg42Ofgqxj5CN86Mv45MhbmzJFVZ0JRM1yECFrLkdLnGJOr4c%252FZQoWJ3CeRGl3XcYF807JC%252F0iidvC62N3qQm97ketMo9af%252FQjTL4NTOkzPVwv1bNeI%252F8Ea5uQxWtBdZATV6ogzHgFMeM4tzcbJY5E0XxeTjhJ1SijDXLtgSOoERFCPxLzudyb9%252B2IoF9cxNWb8yi9RJuqn%252BMvU4BC%252FFrgJaLn9DJ9r4RE%253D%26iv%3DE2XGT7As7Kdo50sj%26key%3DFoXRG7XfML%252B9UAO88iH7hfSyNfNbhgdPT7d3%252F8G%252B9sqovuZOct4ZNf88yR%252FtgbRedAsVG%252BZJHjeOHlKlFZoomrm2IWweysOvMQrDyIL35hT2NUoG4ZCG94ZFC2b7TII4XEFId%252Bkpj0qMUreKQafh0NXu2jg58ogzAWgpU5uskZBUg3WDITJMQXdGqaOPO6gooIEtKmLV6gQx4%252F%252B9K18XKofG2fZQ5bNlvpuFbyn4%252Brs3J%252BtJxPsxnuSiPrJwGEk36rDjhW1LOgssrAAUv%252BSfExHQ3KfmFnBdbK2rWM0CkwgYZ95cteVxRDl7f7SdpgBCmrlVVcPvM2moUiWOTW9aHA%253D%253D&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.assoc_handle=amazon_pay_in_mobile&openid.mode=checkid_setup&marketPlaceId=A3FDG49KKM823Y&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&pageId=amzn_pay_in&openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0&openid.pape.max_auth_age=5400&siteState=clientContext%3D257-1861896-6931844%2CsourceUrl%3Dhttps%253A%252F%252Famazonpay.amazon.in%252Fv1%252Finitiate-payment%253FredirectUrl%253Dhttps%25253A%25252F%25252Fsandbox.juspay.in%25252Fv2%25252Fpay%25252Fresponse-amazonpay%25252Fmpl_qa%2526payload%253DqpZtIG4rQa0Ru6HR1RSOlDtxA61%25252BVNb0WLLwzMgLnsStcLU9nD%25252FbQ2XZLKvWNqdViQ5YZujSRCPagD%25252FVME0JWyl3fhlh1s69%25252FCaKfQiDnTg42Ofgqxj5CN86Mv45MhbmzJFVZ0JRM1yECFrLkdLnGJOr4c%25252FZQoWJ3CeRGl3XcYF807JC%25252F0iidvC62N3qQm97ketMo9af%25252FQjTL4NTOkzPVwv1bNeI%25252F8Ea5uQxWtBdZATV6ogzHgFMeM4tzcbJY5E0XxeTjhJ1SijDXLtgSOoERFCPxLzudyb9%25252B2IoF9cxNWb8yi9RJuqn%25252BMvU4BC%25252FFrgJaLn9DJ9r4RE%25253D%2526iv%253DE2XGT7As7Kdo50sj%2526key%253DFoXRG7XfML%25252B9UAO88iH7hfSyNfNbhgdPT7d3%25252F8G%25252B9sqovuZOct4ZNf88yR%25252FtgbRedAsVG%25252BZJHjeOHlKlFZoomrm2IWweysOvMQrDyIL35hT2NUoG4ZCG94ZFC2b7TII4XEFId%25252Bkpj0qMUreKQafh0NXu2jg58ogzAWgpU5uskZBUg3WDITJMQXdGqaOPO6gooIEtKmLV6gQx4%25252F%25252B9K18XKofG2fZQ5bNlvpuFbyn4%25252Brs3J%25252BtJxPsxnuSiPrJwGEk36rDjhW1LOgssrAAUv%25252BSfExHQ3KfmFnBdbK2rWM0CkwgYZ95cteVxRDl7f7SdpgBCmrlVVcPvM2moUiWOTW9aHA%25253D%25253D%2Csignature%3Dj2BY7ki63y4rphlJZ6WQZhGj2F5fMyEj3D";
        assertTrue(KeyTypes.patternToSubType.get(SubType.URL).matcher(url).matches());
    }

    @Test
    public void test2() {
        String[] urlTokens = APICatalogSync.tokenize("https://qapi.mpl.live:443/{param_STRING}/pending-invites");
        urlTokens[3] = null;
        SuperType[] types = new SuperType[urlTokens.length];
        types[3] = SuperType.STRING;
        URLTemplate urlTemplate = new URLTemplate(urlTokens, types);
        assertFalse(urlTemplate.match("https://qapi.mpl.live:443/kyc/for-payments"));

        assertTrue(urlTemplate.match("https://qapi.mpl.live:443/12312/pending-invites"));
        assertFalse(urlTemplate.match("https://qapi.mpl.live:443/12312/sdfdasfa"));
    }
}
