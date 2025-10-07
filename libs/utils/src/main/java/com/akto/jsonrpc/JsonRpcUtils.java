package com.akto.jsonrpc;

import com.akto.dto.HttpResponseParams;
import com.akto.mcp.McpSchema;
import com.akto.util.JSONUtils;
import com.akto.util.Pair;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonRpcUtils {

    public static final String JSONRPC_KEY = "jsonrpc";

    public static HttpResponseParams parseJsonRpcResponse(HttpResponseParams responseParams) {
        Pair<Boolean, Map<String, Object>> result = validateAndParseJsonRpc(responseParams);
        if (!result.getFirst()) {
            return responseParams;
        }

        Map<String, Object> jsonRpcMap = result.getSecond();

        if (jsonRpcMap.containsKey("method")) {
            String method = String.valueOf(jsonRpcMap.get("method"));
            if (StringUtils.isEmpty(method)) {
                return responseParams;
            }
            String url = responseParams.getRequestParams().getURL();
            url = HttpResponseParams.addPathParamToUrl(url, method);
            responseParams.getRequestParams().setUrl(url);
        }
        return responseParams;
    }

    public static Pair<Boolean, Map<String, Object>> validateAndParseJsonRpc(HttpResponseParams responseParams) {
        if (responseParams == null || responseParams.getRequestParams() == null) {
            return new Pair<>(false, null);
        }
        String payload = responseParams.getRequestParams().getPayload();
        if (payload == null || !payload.contains(JSONRPC_KEY)) {
            return new Pair<>(false, null);
        }

        Map<String, Object> jsonRpcMap;
        try {
            jsonRpcMap = JSONUtils.getMap(payload);
        } catch (Exception e) {
            return new Pair<>(false, null);
        }
        if (MapUtils.isEmpty(jsonRpcMap)) {
            return new Pair<>(false, null);
        }

        Object version = jsonRpcMap.get(JSONRPC_KEY);
        if (!McpSchema.JSONRPC_VERSION.equals(version)) {
            return new Pair<>(false, jsonRpcMap);
        }
        int statusCode = responseParams.getStatusCode();
        if (statusCode < 200 || statusCode >= 300) {
            return new Pair<>(false, jsonRpcMap);
        }
        return new Pair<>(true, jsonRpcMap);
    }
}
