package com.akto.test_editor.execution;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.test_editor.ExecuteAlgoObj;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.dto.test_editor.ExecutorSingleRequest;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExecutorAlgorithmTest {

    private static class StubExecutor extends Executor {
        @Override
        public ExecutorSingleOperationResp invokeOperation(String operationType, Object key, Object value, RawApi rawApi,
                                                           Map<String, Object> varMap, AuthMechanism authMechanism, List<CustomAuthType> customAuthTypes,
                                                           ApiInfo.ApiInfoKey apiInfoKey) {
            if (!"modify_body_param".equalsIgnoreCase(operationType)) {
                return new ExecutorSingleOperationResp(false, "unsupported test operation");
            }
            return Operations.modifyBodyParam(rawApi, key.toString(), value);
        }
    }

    private RawApi buildRawApi(String body) {
        OriginalHttpRequest req = new OriginalHttpRequest();
        req.setMethod("POST");
        req.setUrl("https://example.com/login");
        req.setHeaders(new HashMap<>());
        req.setBody(body);

        OriginalHttpResponse resp = new OriginalHttpResponse();
        resp.setBody("{}");
        resp.setHeaders(new HashMap<>());
        resp.setStatusCode(200);

        return new RawApi(req, resp, "");
    }

    @Test
    public void testAllowAllCombinationsCyclesValuesPerKey() {
        Map<String, Object> varMap = new HashMap<>();
        List<Object> keys = new ArrayList<>();
        keys.add("email");
        keys.add("password");
        List<Object> values = new ArrayList<>();
        values.add("hello");
        values.add("world");
        values.add("welcome");
        varMap.put("keys", keys);
        varMap.put("vals", values);

        RawApi sampleRawApi = buildRawApi("{\"email\":\"victim@gmail.com\",\"password\":\"victim123\"}");
        ExecutorAlgorithm executorAlgorithm = new ExecutorAlgorithm(sampleRawApi, varMap, null, Collections.emptyList(), true);
        try {
            Field executorField = ExecutorAlgorithm.class.getDeclaredField("executor");
            executorField.setAccessible(true);
            executorField.set(executorAlgorithm, new StubExecutor());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ExecutorNode operationNode = new ExecutorNode(
                TestEditorEnums.ExecutorOperandTypes.NonTerminal.toString(),
                Arrays.asList(
                        new ExecutorNode(
                                TestEditorEnums.ExecutorOperandTypes.NonTerminal.toString(),
                                new ArrayList<>(),
                                "${vals}",
                                "${keys}"
                        )
                ),
                null,
                "modify_body_param"
        );

        List<RawApi> rawApis = new ArrayList<>();
        rawApis.add(sampleRawApi.copy());
        ExecutorSingleRequest response = executorAlgorithm.execute(
                Collections.singletonList(operationNode),
                0,
                new HashMap<Integer, ExecuteAlgoObj>(),
                rawApis,
                false,
                0,
                new ApiInfo.ApiInfoKey(0, "/login", URLMethods.Method.POST)
        );

        assertTrue(response.getSuccess());
        assertEquals(6, rawApis.size());

        for (int i = 0; i < 3; i++) {
            BasicDBObject payload = BasicDBObject.parse(rawApis.get(i).getRequest().getBody());
            assertEquals(values.get(i), payload.getString("email"));
            assertEquals("victim123", payload.getString("password"));
        }

        for (int i = 3; i < 6; i++) {
            BasicDBObject payload = BasicDBObject.parse(rawApis.get(i).getRequest().getBody());
            assertEquals("victim@gmail.com", payload.getString("email"));
            assertEquals(values.get(i - 3), payload.getString("password"));
        }
    }
}
