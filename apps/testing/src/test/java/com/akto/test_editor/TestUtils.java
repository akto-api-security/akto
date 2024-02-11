package com.akto.test_editor;

import org.junit.Assert;
import org.junit.Test;

import com.akto.test_editor.execution.VariableResolver;
import com.mongodb.BasicDBObject;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.akto.test_editor.Utils.headerValuesUnchanged;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestUtils {

    @Test
    public void testHeaderValuesUnchanged() {
        Map<String, List<String>> originalRequestHeaders = new HashMap<>();
        originalRequestHeaders.put("Connection", Arrays.asList("Close", "Open"));
        originalRequestHeaders.put("Referer", Arrays.asList("a1", "a2"));
        originalRequestHeaders.put("Auth", Collections.singletonList("r@nd0m_t0k3n"));
        originalRequestHeaders.put("Accept", Collections.singletonList("application/json, text/plain, */*"));
        originalRequestHeaders.put("access-token", null);

        Map<String, List<String>> testRequestHeaders = new HashMap<>();
        testRequestHeaders.put("Connection", Collections.singletonList("Close"));
        testRequestHeaders.put("Referer", Arrays.asList("a1", "a2"));
        testRequestHeaders.put("Auth", Collections.singletonList("r@nd0m_t0k3n+changed"));
        testRequestHeaders.put("Accept", Collections.singletonList("application/json, text/plain, */*"));
        testRequestHeaders.put("access-token", Collections.singletonList(""));
        testRequestHeaders.put("Accept-Language", Collections.singletonList("en-GB"));

        Set<String> result = headerValuesUnchanged(originalRequestHeaders, testRequestHeaders);

        assertEquals(2, result.size());
        assertTrue(result.contains("Accept"));
        assertTrue(result.contains("Referer"));

    }

    @Test
    public void testModifyKvJwt() {
        String out;
        Map<String, Object> resolverMap = new HashMap<>();
        Map<String, Object> kvMap = new HashMap<>();
        String expected = "ankush@akto.io";
        resolverMap.put("${auth_context.modify_jwt}", kvMap);

        Map<String, List<String>> headers = new HashMap<>();
        List<String> authHeaders = new ArrayList<>();
        authHeaders.add("Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjEsInVzZXJuYW1lIjoidmljdGltIiwiZW1haWwiOiJ2aWN0aW1AZ21haWwuY29tIiwicGFzc3dvcmQiOiJhNjJlN2JlMGE1NjQwODFiNmE5Zjc1MzA4MjA4YzQzMyIsInJvbGUiOiJjdXN0b21lciIsImRlbHV4ZVRva2VuIjoiIiwibGFzdExvZ2luSXAiOiIiLCJwcm9maWxlSW1hZ2UiOiJhc3NldHMvcHVibGljL2ltYWdlcy91cGxvYWRzL2RlZmF1bHQuc3ZnIiwidG90cFNlY3JldCI6IiIsImlzQWN0aXZlIjp0cnVlLCJjcmVhdGVkQXQiOiIyMDIzLTAzLTEwIDA1OjM5OjE4LjI5OSArMDA6MDAiLCJ1cGRhdGVkQXQiOiIyMDIzLTAzLTEwIDA1OjM5OjE4LjI5OSArMDA6MDAiLCJkZWxldGVkQXQiOm51bGx9LCJpYXQiOjE2Nzg0MjY4NjUsImV4cCI6MTk5Mzc4Njg2NX0.bUvn24at2rOcuht5hto8QHl7pXdanuLKQDBxqH2MWG2-mMEI8LgWm1R9HhUD209dHL93Ks52KijKJFOlF_5Z3-v47jY-Rf73wcA_Le69-n7EudWwrc_X6EGpNiqovVYm31RZQnU2Q_H-PtzpnzNIOnfE6z_p023acrke-cZkKss");
        headers.put("authorization", authHeaders);

        kvMap.put("username", expected);        
        out = VariableResolver.resolveAuthContext(resolverMap, headers, "authorization");
        String actual = BasicDBObject.parse(new String(Base64.getDecoder().decode(out.split("\\.")[1]))).getString("username");
        Assert.assertEquals(expected, actual);

        kvMap.put("data.email", kvMap.remove("username"));
        out = VariableResolver.resolveAuthContext(resolverMap, headers, "authorization");
        actual = BasicDBObject.parse(BasicDBObject.parse(new String(Base64.getDecoder().decode(out.split("\\.")[1]))).get("data").toString()).getString("email");
        Assert.assertEquals(expected, actual);

        kvMap.remove("data.email");
        kvMap.put("data.id", 11);
        out = VariableResolver.resolveAuthContext(resolverMap, headers, "authorization");
        Object actualObj = BasicDBObject.parse(BasicDBObject.parse(new String(Base64.getDecoder().decode(out.split("\\.")[1]))).get("data").toString()).get("id");
        Assert.assertEquals(11, actualObj);


    }
    @Test
    public void testEncodeDecode() {
        String orig = "some string this is really a big string you can't imagine";
        for(int i = 0; i < 200; i ++) {
            String modified = orig + i;
            String encoded = Base64.getEncoder().encodeToString(modified.getBytes(StandardCharsets.UTF_8));
            new String(Base64.getDecoder().decode(encoded));
            if (encoded.endsWith("=")) encoded = encoded.substring(0, encoded.length()-1);
            if (encoded.endsWith("=")) encoded = encoded.substring(0, encoded.length()-1);
            new String(Base64.getDecoder().decode(encoded));
        }
    }
}
