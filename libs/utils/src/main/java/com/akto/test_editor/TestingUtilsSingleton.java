package com.akto.test_editor;

import java.util.HashMap;
import java.util.Map;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.mcp.McpRequestResponseUtils;

public class TestingUtilsSingleton {
    
    private static final TestingUtilsSingleton instance = new TestingUtilsSingleton();
    private  Map<ApiInfo.ApiInfoKey, Boolean> mcpRequestMap = new HashMap<>();
    private  Map<ApiInfo.ApiInfoKey, String> mcpRequestMethodMap = new HashMap<>();

    public static void init() {
        instance.mcpRequestMap = new HashMap<>();
    }

    public static TestingUtilsSingleton getInstance() {
        return instance;
    }

    public boolean isMcpRequest(ApiInfo.ApiInfoKey apiInfoKey, RawApi rawApi) {
        if (apiInfoKey == null) return false;
        if (instance.mcpRequestMap.containsKey(apiInfoKey)) {
            return instance.mcpRequestMap.get(apiInfoKey);
        }
        boolean isMcpRequest = McpRequestResponseUtils.isMcpRequest(rawApi);
        instance.mcpRequestMap.put(apiInfoKey, isMcpRequest);
        return isMcpRequest;
    }

    public String getMcpRequestMethod(ApiInfo.ApiInfoKey apiInfoKey) {
        if (apiInfoKey == null) return "POST";
        if (instance.mcpRequestMethodMap.containsKey(apiInfoKey)) {
            return instance.mcpRequestMethodMap.get(apiInfoKey);
        }
        // get from AI here and store in the map
        return "POST";
    }
}
