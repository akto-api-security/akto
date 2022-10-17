package com.akto.dto;

import com.akto.dto.type.RequestTemplate;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TestOriginalHttpRequest {

    private OriginalHttpRequest generateOriginalHttpRequest() {
        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
        String message = "{\"method\":\"POST\",\"requestPayload\":\"[\\n  {\\n    \\\"id\\\": 0,\\n    \\\"username\\\": \\\"string\\\",\\n    \\\"firstName\\\": \\\"string\\\",\\n    \\\"lastName\\\": \\\"string\\\",\\n    \\\"email\\\": \\\"string\\\",\\n    \\\"password\\\": \\\"string\\\",\\n    \\\"phone\\\": \\\"string\\\",\\n    \\\"userStatus\\\": 0\\n  }\\n]\",\"responsePayload\":\"{\\\"code\\\":200,\\\"type\\\":\\\"unknown\\\",\\\"message\\\":\\\"ok\\\"}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/user/createWithArray?user=1\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"195\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:14:27 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327267\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        originalHttpRequest.buildFromSampleMessage(message);
        return originalHttpRequest;
    }

    @Test
    public void testBuildFromSampleMessage() {
        OriginalHttpRequest originalHttpRequest = generateOriginalHttpRequest();
        assertEquals("https://petstore.swagger.io/v2/user/createWithArray", originalHttpRequest.getUrl());
        assertEquals("HTTP/2", originalHttpRequest.getType());
        assertEquals("user=1", originalHttpRequest.getQueryParams());
        assertEquals("POST", originalHttpRequest.getMethod());
        assertEquals("[\n  {\n    \"id\": 0,\n    \"username\": \"string\",\n    \"firstName\": \"string\",\n    \"lastName\": \"string\",\n    \"email\": \"string\",\n    \"password\": \"string\",\n    \"phone\": \"string\",\n    \"userStatus\": 0\n  }\n]", originalHttpRequest.getBody());
        assertEquals(14, originalHttpRequest.getHeaders().size());
        assertEquals("petstore.swagger.io", originalHttpRequest.getHeaders().get("host").get(0));
    }

    @Test
    public void testHeaderFunctions() {
        OriginalHttpRequest originalHttpRequest = generateOriginalHttpRequest();
        assertEquals("petstore.swagger.io", originalHttpRequest.findHostFromHeader());
        assertEquals("application/json", originalHttpRequest.findContentType());
    }

    @Test
    public void testGetFullUrlWithParams() {
        OriginalHttpRequest originalHttpRequest = generateOriginalHttpRequest();
        String fullUrlWithParams = originalHttpRequest.getFullUrlWithParams();
        assertEquals("https://petstore.swagger.io/v2/user/createWithArray?user=1", fullUrlWithParams);
    }

    @Test
    public void testBuildFromApiSampleMessage() {
        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
        originalHttpRequest.buildFromApiSampleMessage("{\"request\":{\"url\":\"https://petstore.swagger.io/v2/pet/1\",\"method\":\"POST\",\"type\":\"HTTP/2\",\"queryParams\":null,\"body\":\"name=asd&status=available\",\"headers\":\"{\\\"sec-fetch-mode\\\":\\\"cors\\\",\\\"referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"content-length\\\":\\\"25\\\",\\\"sec-fetch-site\\\":\\\"same-origin\\\",\\\"accept-language\\\":\\\"en-US,en;q=0.5\\\",\\\"origin\\\":\\\"https://petstore.swagger.io\\\",\\\"accept\\\":\\\"application/json\\\",\\\"te\\\":\\\"trailers\\\",\\\"host\\\":\\\"null\\\",\\\"connection\\\":\\\"keep-alive\\\",\\\"content-type\\\":\\\"application/x-www-form-urlencoded\\\",\\\"accept-encoding\\\":\\\"gzip, deflate, br\\\",\\\"user-agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"sec-fetch-dest\\\":\\\"empty\\\"}\"},\"response\":{\"statusCode\":404,\"body\":\"{\\\"code\\\":404,\\\"type\\\":\\\"unknown\\\",\\\"message\\\":\\\"not found\\\"}\",\"headers\":\"{\\\"date\\\":\\\"Wed, 31 Aug 2022 15:30:05 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\"}}");

        assertEquals("https://petstore.swagger.io/v2/pet/1", originalHttpRequest.getUrl());
        assertEquals("HTTP/2", originalHttpRequest.getType());
        assertNull(originalHttpRequest.getQueryParams());
        assertEquals("POST", originalHttpRequest.getMethod());
        assertEquals("name=asd&status=available", originalHttpRequest.getBody());


        originalHttpRequest.buildFromApiSampleMessage("{\"request\":{\"url\":\"https://bat.bing.com:443/actionp/0\",\"method\":\"POST\",\"type\":\"HTTP/2\",\"queryParams\":\"ti=4063247&tm=gtm002&Ver=2&mid=ca2d781b-4525-475c-a8dc-38497d845979&sid=0910c9d0275811eda8eb6b80b2a40e19&vid=ba436bb022bb11edb0d817c5213a1c13&vids=0&msclkid=N&evt=pageHide\",\"body\":\"{}\",\"headers\":\"{\\\"sec-fetch-mode\\\":\\\"no-cors\\\",\\\"referer\\\":\\\"https://www.atlassian.com/\\\",\\\"content-length\\\":\\\"0\\\",\\\"sec-fetch-site\\\":\\\"cross-site\\\",\\\"cookie\\\":\\\"null\\\",\\\"accept-language\\\":\\\"en-GB,en-US;q=0.9,en;q=0.8\\\",\\\"origin\\\":\\\"https://www.atlassian.com\\\",\\\"accept\\\":\\\"*/*\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"macOS\\\\\\\"\\\",\\\"host\\\":\\\"bat.bing.com\\\",\\\"accept-encoding\\\":\\\"gzip, deflate\\\",\\\"user-agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.5112.102 Safari/537.36\\\",\\\"sec-fetch-dest\\\":\\\"empty\\\"}\"},\"response\":{\"statusCode\":204,\"body\":\"\",\"headers\":\"{\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"date\\\":\\\"Wed, 31 Aug 2022 08:45:32 GMT\\\",\\\"set-cookie\\\":\\\"MUID=267C033E0C226A691693112F0D8A6BB9; domain=.bing.com; expires=Mon, 25-Sep-2023 08:45:32 GMT; path=/; SameSite=None; Secure; Priority=High;;MR=0; domain=bat.bing.com; expires=Wed, 07-Sep-2022 08:45:32 GMT; path=/; SameSite=None; Secure;\\\",\\\"expires\\\":\\\"Fri, 01 Jan 1990 00:00:00 GMT\\\",\\\"x-msedge-ref\\\":\\\"Ref A: C9B74F2BDB31442C9060CF9E117258DC Ref B: BOM01EDGE1114 Ref C: 2022-08-31T08:45:32Z\\\",\\\"x-cache\\\":\\\"CONFIG_NOCACHE\\\",\\\"cache-control\\\":\\\"no-cache, must-revalidate\\\",\\\"pragma\\\":\\\"no-cache\\\",\\\"strict-transport-security\\\":\\\"max-age=31536000; includeSubDomains; preload\\\",\\\"accept-ch\\\":\\\"Sec-CH-UA-Arch, Sec-CH-UA-Bitness, Sec-CH-UA-Full-Version, Sec-CH-UA-Mobile, Sec-CH-UA-Model, Sec-CH-UA-Platform, Sec-CH-UA-Platform-Version\\\"}\"}}");

        assertEquals("https://bat.bing.com:443/actionp/0", originalHttpRequest.getUrl());
        assertEquals("HTTP/2", originalHttpRequest.getType());
        assertEquals("ti=4063247&tm=gtm002&Ver=2&mid=ca2d781b-4525-475c-a8dc-38497d845979&sid=0910c9d0275811eda8eb6b80b2a40e19&vid=ba436bb022bb11edb0d817c5213a1c13&vids=0&msclkid=N&evt=pageHide",originalHttpRequest.getQueryParams());
        assertEquals("POST", originalHttpRequest.getMethod());
        assertEquals("{}", originalHttpRequest.getBody());



        originalHttpRequest.buildFromApiSampleMessage("{\"request\":{\"url\":\"https://veviwag1.atlassian.net:443/rest/api/latest/user/properties/help_button_ui_state\",\"method\":\"PUT\",\"type\":\"HTTP/2\",\"queryParams\":\"accountId=6304df219a460a36a1edb0e0\",\"body\":\"{\\\"helpPanelMenu\\\":{\\\"releaseNotesNotifications\\\":0}}\",\"headers\":\"{\\\"sec-fetch-mode\\\":\\\"cors\\\",\\\"referer\\\":\\\"https://veviwag1.atlassian.net/jira/core/projects/SOM/board\\\",\\\"content-length\\\":\\\"49\\\",\\\"sec-fetch-site\\\":\\\"same-origin\\\",\\\"cookie\\\":\\\"null\\\",\\\"accept-language\\\":\\\"en-GB,en-US;q=0.9,en;q=0.8\\\",\\\"origin\\\":\\\"https://veviwag1.atlassian.net\\\",\\\"accept\\\":\\\"application/json,text/javascript,*/*\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"x-atlassian-force-account-id\\\":\\\"true\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"macOS\\\\\\\"\\\",\\\"host\\\":\\\"veviwag1.atlassian.net\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"accept-encoding\\\":\\\"gzip, deflate\\\",\\\"user-agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.5112.102 Safari/537.36\\\",\\\"sec-fetch-dest\\\":\\\"empty\\\"}\"},\"response\":{\"statusCode\":401,\"body\":\"{\\\"message\\\":\\\"Client must be authenticated to access this resource.\\\",\\\"status-code\\\":401}\",\"headers\":\"{\\\"date\\\":\\\"Wed, 31 Aug 2022 08:44:37 GMT\\\",\\\"server\\\":\\\"globaledge-envoy\\\",\\\"x-envoy-upstream-service-time\\\":\\\"204\\\",\\\"vary\\\":\\\"Accept\\\",\\\"www-authenticate\\\":\\\"OAuth realm=\\\\\\\"https%3A%2F%2Fveviwag1.atlassian.net\\\\\\\"\\\",\\\"expect-ct\\\":\\\"report-uri=\\\\\\\"https://web-security-reports.services.atlassian.com/expect-ct-report/atlassian-proxy\\\\\\\", max-age=86400\\\",\\\"strict-transport-security\\\":\\\"max-age=63072000; preload\\\",\\\"atl-traceid\\\":\\\"10343bd6888cf756\\\",\\\"set-cookie\\\":\\\"atlassian.xsrf.token=825dbd3d-a44d-45db-9409-60981db9b8e0_b1e600497cbaa9996a966cc075023d2054fd4954_lout; path=/; SameSite=None; Secure\\\",\\\"x-content-type-options\\\":\\\"nosniff\\\",\\\"x-xss-protection\\\":\\\"1; mode=block\\\",\\\"nel\\\":\\\"{\\\\\\\"failure_fraction\\\\\\\": 0.001, \\\\\\\"include_subdomains\\\\\\\": true, \\\\\\\"max_age\\\\\\\": 600, \\\\\\\"report_to\\\\\\\": \\\\\\\"endpoint-1\\\\\\\"}\\\",\\\"x-arequestid\\\":\\\"7bc7c45d-5342-4379-a63c-37116bc4f8d0\\\",\\\"content-type\\\":\\\"application/json;charset=UTF-8\\\",\\\"timing-allow-origin\\\":\\\"*\\\",\\\"report-to\\\":\\\"{\\\\\\\"endpoints\\\\\\\": [{\\\\\\\"url\\\\\\\": \\\\\\\"https://dz8aopenkvv6s.cloudfront.net\\\\\\\"}], \\\\\\\"group\\\\\\\": \\\\\\\"endpoint-1\\\\\\\", \\\\\\\"include_subdomains\\\\\\\": true, \\\\\\\"max_age\\\\\\\": 600}\\\",\\\"cache-control\\\":\\\"no-transform\\\"}\"}}");

        assertEquals("https://veviwag1.atlassian.net:443/rest/api/latest/user/properties/help_button_ui_state", originalHttpRequest.getUrl());
        assertEquals("HTTP/2", originalHttpRequest.getType());
        assertEquals("accountId=6304df219a460a36a1edb0e0",originalHttpRequest.getQueryParams());
        assertEquals("PUT", originalHttpRequest.getMethod());
        assertEquals("{\"helpPanelMenu\":{\"releaseNotesNotifications\":0}}", originalHttpRequest.getBody());
    }

    @Test
    public void testCopy() {
        String url = "url";
        String queryParams = "user=avneesh";
        String method = "GET";
        String body = "{}";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("something", Collections.singletonList("value"));
        String type = "HTTP 1.1";

        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest(
                url, queryParams, method, body, headers, type
        );

        OriginalHttpRequest copyOriginalHttpRequest = originalHttpRequest.copy();

        assertEquals(url,copyOriginalHttpRequest.getUrl());
        assertEquals(queryParams,copyOriginalHttpRequest.getQueryParams());
        assertEquals(method,copyOriginalHttpRequest.getMethod());
        assertEquals(body,copyOriginalHttpRequest.getBody());
        assertEquals(headers,copyOriginalHttpRequest.getHeaders());
        assertEquals(type,copyOriginalHttpRequest.getType());

    }

    @Test
    public void testCombineQueryParams() {
        String query1 = "user=avneesh&age=99&favColour=blue&status=all_is_well";
        String query2 = "status=blah%20blah&age=101";
        String combinedQuery = OriginalHttpRequest.combineQueryParams(query1, query2);
        assertTrue(combinedQuery.contains("status=blah%20blah"));

        BasicDBObject combinedQueryObject = RequestTemplate.getQueryJSON("google.com?"+combinedQuery);

        assertEquals("avneesh", combinedQueryObject.get("user"));
        assertEquals("101", combinedQueryObject.get("age"));
        assertEquals("blue", combinedQueryObject.get("favColour"));
        assertEquals("blah blah", combinedQueryObject.get("status"));
    }

    @Test
    public void testGetRawQueryFromJson() {
        String normalReq = "{\"name\": \"avneesh\", \"cities\": [{\"name\": \"Mumbai\"}, {\"name\": \"Bangalore\"}], \"age\": 99}";
        String resultNormalReq = OriginalHttpRequest.getRawQueryFromJson(normalReq);
        BasicDBObject queryParams = RequestTemplate.getQueryJSON("?"+ resultNormalReq);
        assertEquals(3, queryParams.size());
    }

}
