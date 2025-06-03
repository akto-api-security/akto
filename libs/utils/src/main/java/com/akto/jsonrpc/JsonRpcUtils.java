package com.akto.jsonrpc;

import com.akto.dto.HttpResponseParams;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonRpcUtils {

    private static final String JSONRPC_KEY = "jsonrpc";

    public static boolean isJsonRpcRequest(HttpResponseParams responseParams) {
        return responseParams.getRequestParams().getPayload().contains(JSONRPC_KEY);
    }
}
