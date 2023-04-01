package com.akto.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.google.api.services.drive.Drive.Changes.List;

public class TestFilterTest {
    
    @Test
    public void testContainsPrivateResource() {
        Map<String, SingleTypeInfo> singleTypeInfoMap = new HashMap<>();
        BOLATest bolaTest = new BOLATest();

        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/v4/api/users", URLMethods.Method.PUT);

        String payload1 = "{\"location\": {\"email\": \"dsfv\", \"param2\": \"ankush\"}, \"param2\": \"ankush\"}";
        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest("/v4/api/users", "limit=https://", apiInfoKey.getMethod().name(), payload1, new HashMap<>(), "");

        Map<String, java.util.List<String>> headers = new HashMap<>();
        headers.put("Access-Token", createList(" eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6ImFua3VzaEBnbWFpbC5jb20iLCJpYXQiOjE2MzQ3NTc0NzIsImV4cCI6MTYzNDc1ODM3Mn0.s_UihSzOlEY9spECqSS9SaTPxygn26YodRkYZKHmNwVnVkeppT5mQYKlwiUnjpKYIHxi2a82I50c0FbJKnTTk1Z5aYcT3t8GXUar_DaaEiv3eZZcZiqeOQSnPkP_c4nhC7Fqaq4g03p4Uj_7W5qvUAkTjHOCViwE933rmfX7tZA27o-9-b_ZKXYsTLfk-FjBV7f3piHmRo88j0WpkvuQc8LwcsoUq6yPclVuDsz9YHkvM1B33_QGdZ7nGz47M33tyLXqZyeF4qsnewkOOU6vCiDnM_eqbJghbZLSqP3Ut3lfA54BlAZ-HB5gLv-2HR0m_R2thDGXXE_G_onS-ZDB6A"));
        headers.put("Content-Type", createList(" application/json;charset=utf-8"));
        headers.put("Content-Length", createList(" 762"));

        originalHttpRequest.setHeaders(headers);

        TestConfigYamlParser parser = new TestConfigYamlParser();
        TestConfig testConfig = parser.parseTemplate("Fuzzing");

        OriginalHttpResponse originalHttpResponse = new OriginalHttpResponse();
        String message = "{\"method\":\"POST\",\"requestPayload\":\"[\\n  {\\n    \\\"id\\\": 0,\\n    \\\"username\\\": \\\"string\\\",\\n    \\\"firstName\\\": \\\"string\\\",\\n    \\\"lastName\\\": \\\"string\\\",\\n    \\\"email\\\": \\\"string\\\",\\n    \\\"password\\\": \\\"string\\\",\\n    \\\"phone\\\": \\\"string\\\",\\n    \\\"userStatus\\\": 0\\n  }\\n]\",\"responsePayload\":\"{\\\"code\\\":200,\\\"type\\\":\\\"unknown\\\",\\\"message\\\":{\\\"role\\\": \\\"admin\\\", \\\"param2\\\": \\\"ankush\\\"}}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/user/createWithArray?user=1\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"195\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:14:27 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327267\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        originalHttpResponse.buildFromSampleMessage(message);

        RawApi rawApi = new RawApi(originalHttpRequest, originalHttpResponse, null);

        Boolean isValid = parser.validateAgainstTemplate(testConfig, rawApi, apiInfoKey);

        assertEquals(true, isValid);

    }

    public static java.util.List<String> createList(String s) {
        java.util.List<String> ret = new ArrayList<>();
        ret.add(s);
        return ret;
    }
}
