package com.akto.jsonrpc;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.JsonUtils;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonRpcUtils {

    private static final LoggerMaker logger = new LoggerMaker(JsonRpcUtils.class, LogDb.RUNTIME);
    public static final String JSONRPC_KEY = "jsonrpc";

    public static HttpResponseParams parseJsonRpcResponse(HttpResponseParams responseParams) {
        String requestPayload = responseParams.getRequestParams().getPayload();

        if (!isJsonRpcRequest(responseParams)) {
            return responseParams;
        }

        logger.info("Found a JSON RPC request. {}", requestPayload);

        Map<String, Object> jsonRpcMap = JsonUtils.getMap(requestPayload);

        if (jsonRpcMap.containsKey("method")) {
            String method = String.valueOf(jsonRpcMap.get("method"));
            if (StringUtils.isEmpty(method)) {
                return responseParams;

            }

            String url = responseParams.getRequestParams().getURL();
            url = HttpResponseParams.addPathParamToUrl(url, method);

            HttpResponseParams httpResponseParamsCopy = responseParams.copy();
            httpResponseParamsCopy.getRequestParams().setUrl(url);
            return httpResponseParamsCopy;
        }
        return responseParams;
    }

    public static boolean isJsonRpcRequest(HttpResponseParams responseParams) {
        HttpRequestParams params = responseParams.getRequestParams();
        if (params == null || params.getPayload() == null) {
            return false;
        }
        return params.getPayload().contains(JSONRPC_KEY);
    }
}
