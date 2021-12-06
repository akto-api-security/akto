package com.akto.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.type.URLMethods.Method;
import com.akto.parsers.HttpCallParser.HttpRequestParams;
import com.akto.parsers.HttpCallParser.HttpResponseParams;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestDump2 {

    private static final Logger logger = LoggerFactory.getLogger(TestDump2.class);

    private String createSimpleResponsePayload() {
        BasicDBObject ret = new BasicDBObject();

        ret.append("a1", 1).append("b1", new BasicDBObject().append("a2", "some string").append("b2", "some number"));

        return ret.toJson();
    }

    private String createSimpleRequestPayload() {
        BasicDBObject ret = new BasicDBObject();

        ret.append("id", 1).append("startDate", "some string");

        return ret.toJson();
    }

    private List<String> createList(String s) {
        List<String> ret = new ArrayList<>();
        ret.add(s);
        return ret;
    }

    private HttpResponseParams createSampleParams(String userId, String url) {
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

        return ret;
    }

    @Test
    public void simpleTest() {
        String url = "https://someapi.com/link1";
        HttpResponseParams resp = createSampleParams("user1", url);
    
        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 5);

        aggr.addURL(resp);
        sync.computeDelta(aggr, false);

        Map<String, URLMethods> urlMethodsMap = sync.getDelta().getStrictURLToMethods();

        assertEquals(urlMethodsMap.size(), 1);

        URLMethods urlMethods = urlMethodsMap.get(resp.getRequestParams().url);
        
        RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(Method.valueOf(resp.getRequestParams().method));
        assertEquals(reqTemplate.getUserIds().size(), 1);
        assertEquals(reqTemplate.getParameters().size(), 2);
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
        assertEquals(respTemplate.getUserIds().size(), 1);
        assertEquals(respTemplate.getParameters().size(), 3);

        assertEquals(sync.getDBUpdates().size(), 24);
    }

    @Test
    public void getParamsTest() {
        String baseurl = "https://someapi.com/example";
        String url = baseurl+"?p1=v1&p2=v2&p3=%7B%22a%22%3A1%2C%22b%22%3A%5B%7B%22c%22%3A1%2C%22d%22%3A1%7D%5D%7D";
        HttpResponseParams resp = createSampleParams("user1", url);

        URLAggregator aggr = new URLAggregator();
        APICatalogSync sync = new APICatalogSync("access-token", 5);

        aggr.addURL(resp);
        sync.computeDelta(aggr, false);

        Map<String, URLMethods> urlMethodsMap = sync.getDelta().getStrictURLToMethods();

        assertEquals(urlMethodsMap.size(), 1);

        URLMethods urlMethods = urlMethodsMap.get(baseurl);

        assertEquals(urlMethodsMap.keySet().iterator().next(), baseurl);
        RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(Method.valueOf(resp.getRequestParams().method));
        assertEquals(reqTemplate.getUserIds().size(), 1);
        assertEquals(reqTemplate.getParameters().size(), 5);



        System.out.println("done");
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
        sync.computeDelta(aggr, false);

        Map<String, URLMethods> urlMethodsMap = sync.getDelta().getStrictURLToMethods();

        assertEquals(urlMethodsMap.size(), 1);
        URLMethods urlMethods = urlMethodsMap.get(resp.getRequestParams().url);
        
        RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(Method.valueOf(resp.getRequestParams().method));
        assertEquals(reqTemplate.getUserIds().size(), 5);
        assertEquals(reqTemplate.getParameters().size(), 2);
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
        assertEquals(respTemplate.getUserIds().size(), 5);
        assertEquals(respTemplate.getParameters().size(), 3);
    }

    @Test
    public void testParameterizedURLsTest() {
        String url = "/link/";
        HttpResponseParams resp = createSampleParams("user1", url+1);
        URLAggregator aggr = new URLAggregator();
        resp.requestParams.getHeaders().put("newHeader", new ArrayList<String>());
        aggr.addURL(resp);
        APICatalogSync sync = new APICatalogSync("access-token", 1);

        for (int i = 2; i <= 30; i ++ ) {
            aggr.addURL(createSampleParams("user"+i, url+i));
        }

        sync.computeDelta(aggr, true);

        Map<String, URLMethods> urlMethodsMap = sync.getDelta().getStrictURLToMethods();
        assertEquals(urlMethodsMap.size(), 1);

        URLMethods urlMethods = urlMethodsMap.get(resp.getRequestParams().url);
        
        RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(Method.valueOf(resp.getRequestParams().method));
        assertNotNull(reqTemplate);

        Map<URLTemplate, URLMethods> urlTemplateMap = sync.getDelta().getTemplateURLToMethods();

        assertEquals(urlTemplateMap.size(), 1);

        Map.Entry<URLTemplate, URLMethods> entry = urlTemplateMap.entrySet().iterator().next();

        assertEquals(entry.getKey().getTemplateString(), url+"INTEGER");

        reqTemplate = entry.getValue().getMethodToRequestTemplate().get(Method.POST);

        assertEquals(reqTemplate.getUserIds().size(), 29);
        assertEquals(reqTemplate.getParameters().size(), 2);
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
        assertEquals(respTemplate.getUserIds().size(), 29);
        assertEquals(respTemplate.getParameters().size(), 3);
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
        sync.computeDelta(aggr, false);

        Map<String, URLMethods> urlMethodsMap = sync.getDelta().getStrictURLToMethods();
        assertEquals(urlMethodsMap.size(), 1);

        URLMethods urlMethods = urlMethodsMap.get(resp.getRequestParams().url);
        
        RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(Method.valueOf(resp.getRequestParams().method));
        assertEquals(reqTemplate.getUserIds().size(), 10);
        assertEquals(reqTemplate.getParameters().size(), 2);
        
        RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(resp.statusCode);
        assertEquals(respTemplate.getUserIds().size(), 10);
        assertEquals(respTemplate.getParameters().size(), 29);

        List<SingleTypeInfo> deleted = respTemplate.tryMergeNodesInTrie(url, "POST", resp.statusCode);
        assertEquals(respTemplate.getParameters().size(), 1);

        List updates = sync.getDBUpdates();
        assertEquals(updates.size(), 22);

    }
}
