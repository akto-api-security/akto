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

    public String getMcpRequestMethod(ApiInfo.ApiInfoKey apiInfoKey, RawApi rawApi) {
        if (apiInfoKey == null) return "POST";
        if (instance.mcpRequestMethodMap.containsKey(apiInfoKey)) {
            return instance.mcpRequestMethodMap.get(apiInfoKey);
        }
        // get from AI here and store in the map
        String method =  McpRequestResponseUtils.analyzeMcpRequestMethod(apiInfoKey, rawApi.getRequest().getBody());
        instance.mcpRequestMethodMap.put(apiInfoKey, method);
        return method;
    }
   
    public static String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

}
