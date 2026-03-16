package com.akto.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.config.TestScript;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

public class ApiExecutorUtil {

    private static final String EXECUTE_ONCE_PER_CONVERSATION = "executeOncePerConversation = true";
    private static final String FEATURE_TEST_PRE_SCRIPT = "TEST_PRE_SCRIPT";
    private static final int SCRIPT_CACHE_TTL_SECONDS = 5 * 60;

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutorUtil.class, LogDb.TESTING);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static Map<String, Integer> lastFetchedMap = new HashMap<>();
    private static Map<String, TestScript> testScriptMap = new HashMap<>();

    private static String scriptCacheKey(int accountId, TestScript.Type type) {
        return accountId + "_" + (type != null ? type.name() : "ANY");
    }

    /** Cache of script results per conversationId when executeOncePerConversation = true. */
    private static final Map<String, ScriptResultCache> conversationScriptCache = new ConcurrentHashMap<>();

    /**
     * Returns the script JavaScript for the given type, using 5-min cache. Returns null if feature not granted, or no script.
     */
    private static String getCachedScript(int accountId, TestScript.Type type) {
        FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, FEATURE_TEST_PRE_SCRIPT);
        if (!featureAccess.getIsGranted()) {
            return null;
        }
        String cacheKey = scriptCacheKey(accountId, type);
        TestScript testScript = testScriptMap.getOrDefault(cacheKey, null);
        int lastTestScriptFetched = lastFetchedMap.getOrDefault(cacheKey, 0);
        if (Context.now() - lastTestScriptFetched > SCRIPT_CACHE_TTL_SECONDS) {
            testScript = dataActor.fetchTestScript(type);
            lastFetchedMap.put(cacheKey, Context.now());
            testScriptMap.put(cacheKey, testScript);
        }
        if (testScript == null || testScript.getJavascript() == null || testScript.getJavascript().isEmpty()) {
            return null;
        }
        return testScript.getJavascript();
    }

    /**
     * Sets bindings on the script context, runs the script, returns a map of the given output keys from the context.
     */
    private static Map<String, Object> runScript(String script, Map<String, Object> inputBindings, String... outputKeys) throws Exception {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("nashorn");
        SimpleScriptContext sctx = ((SimpleScriptContext) engine.get("context"));
        for (Map.Entry<String, Object> e : inputBindings.entrySet()) {
            sctx.setAttribute(e.getKey(), e.getValue(), ScriptContext.ENGINE_SCOPE);
        }
        engine.eval(script);
        Map<String, Object> out = new HashMap<>();
        for (String key : outputKeys) {
            out.put(key, sctx.getAttribute(key));
        }
        return out;
    }

    public static String calculateHashAndAddAuth(OriginalHttpRequest originalHttpRequest, boolean executeScript,
            TestingRunConfig testingRunConfig) {
        if (!executeScript) {
            return originalHttpRequest.getBody();
        }
        try {
            int accountId = Context.getActualAccountId();
            String script = getCachedScript(accountId, TestScript.Type.PRE_REQUEST);
            if (script == null) {
                return originalHttpRequest.getBody();
            }
            
            String conversationId = (testingRunConfig != null) ? testingRunConfig.getConversationId() : null;
            boolean useConversationCache = conversationId != null && script.contains(EXECUTE_ONCE_PER_CONVERSATION);
            ScriptResultCache cachedForScript = useConversationCache ? conversationScriptCache.get(conversationId) : null;

            if (cachedForScript != null) {
                applyCachedToRequest(originalHttpRequest, cachedForScript);
                return cachedForScript.cachedPayload;
            }

            loggerMaker.infoAndAddToDb("Starting calculateHashAndAddAuth");

            Map<String, Object> bindings = new HashMap<>();
            bindings.put("method", originalHttpRequest.getMethod());
            bindings.put("headers", originalHttpRequest.getHeaders());
            bindings.put("url", originalHttpRequest.getUrl());
            bindings.put("payload", originalHttpRequest.getBody());
            bindings.put("queryParams", originalHttpRequest.getQueryParams());

            Map<String, Object> out = runScript(script, bindings,
                    "method", "headers", "url", "payload", "queryParams",
                    "parsedPayloadTemp", "cachedMethod", "cachedHeaders", "cachedUrl", "cachedPayload", "cachedQueryParams");

            String method = (String) out.get("method");
            Map<String, Object> headers = (Map) out.get("headers");
            String url = (String) out.get("url");
            String payload = (String) out.get("payload");
            String queryParams = (String) out.get("queryParams");
            String parsedPayloadTemp = (String) out.get("parsedPayloadTemp");
            String cachedMethod = (String) out.get("cachedMethod");
            Map<String, Object> cachedHeaders = (Map) out.get("cachedHeaders");
            String cachedUrl = (String) out.get("cachedUrl");
            String cachedPayload = (String) out.get("cachedPayload");
            String cachedQueryParams = (String) out.get("cachedQueryParams");

            Map<String, List<String>> hs = convertHeadersFromScript(headers);
            Map<String, List<String>> cachedHs = convertHeadersFromScript(cachedHeaders);

            if (useConversationCache && conversationId != null) {
                conversationScriptCache.put(conversationId,
                        new ScriptResultCache(cachedMethod, cachedHs, cachedUrl, cachedPayload, cachedQueryParams));
            }

            originalHttpRequest.setBody(payload);
            originalHttpRequest.setMethod(method);
            originalHttpRequest.setUrl(url);
            originalHttpRequest.setHeaders(hs);
            originalHttpRequest.setQueryParams(queryParams);

            if (parsedPayloadTemp != null) {
                return parsedPayloadTemp;
            }
            return payload;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in calculateHashAndAddAuth " + e.getMessage() + " url " + originalHttpRequest.getUrl());
            e.printStackTrace();
            return originalHttpRequest.getBody();
        }
    }

    private static Map<String, List<String>> convertHeadersFromScript(Map<String, Object> headers) {
        Map<String, List<String>> hs = new HashMap<>();
        if (headers == null) return hs;
        for (String key : headers.keySet()) {
            try {
                ScriptObjectMirror scm = ((ScriptObjectMirror) headers.get(key));
                List<String> val = new ArrayList<>();
                for (int i = 0; i < scm.size(); i++) {
                    val.add((String) scm.get(Integer.toString(i)));
                }
                hs.put(key, val);
            } catch (Exception e) {
                hs.put(key, (List) headers.get(key));
            }
        }
        return hs;
    }

    private static void applyCachedToRequest(OriginalHttpRequest request, ScriptResultCache cached) {
        if (cached.cachedMethod != null) request.setMethod(cached.cachedMethod);
        if (cached.cachedHeaders != null) request.setHeaders(cached.cachedHeaders);
        if (cached.cachedUrl != null) request.setUrl(cached.cachedUrl);
        if (cached.cachedPayload != null) request.setBody(cached.cachedPayload);
        if (cached.cachedQueryParams != null) request.setQueryParams(cached.cachedQueryParams);
    }

    /** Cached script outputs per conversationId; when present, script is skipped and these are applied. */
    private static final class ScriptResultCache {
        final String cachedMethod;
        final Map<String, List<String>> cachedHeaders;
        final String cachedUrl;
        final String cachedPayload;
        final String cachedQueryParams;

        ScriptResultCache(String cachedMethod, Map<String, List<String>> cachedHeaders, String cachedUrl,
                String cachedPayload, String cachedQueryParams) {
            this.cachedMethod = cachedMethod;
            this.cachedHeaders = cachedHeaders;
            this.cachedUrl = cachedUrl;
            this.cachedPayload = cachedPayload;
            this.cachedQueryParams = cachedQueryParams;
        }
    }

    /**
     * Runs the POST_REQUEST test script on the response (e.g. to extract tokens from response).
     * Script context: request (method, headers, url, payload, queryParams), response (statusCode, headers, body).
     * Script may set statusCode, headers, body to modify the response.
     */
    public static OriginalHttpResponse runPostRequestScript(OriginalHttpRequest request, OriginalHttpResponse response, boolean executeScript,
            TestingRunConfig testingRunConfig) {
        if (!executeScript || response == null) {
            return response;
        }
        try {
            int accountId = Context.getActualAccountId();
            String script = getCachedScript(accountId, TestScript.Type.POST_REQUEST);
            if (script == null) {
                return response;
            }

            loggerMaker.infoAndAddToDb("Starting runPostRequestScript");

            Map<String, Object> bindings = new HashMap<>();
            bindings.put("statusCode", response.getStatusCode());
            bindings.put("headers", response.getHeaders());
            bindings.put("body", response.getBody());
            if (request != null) {
                bindings.put("method", request.getMethod());
                bindings.put("requestHeaders", request.getHeaders());
                bindings.put("url", request.getUrl());
                bindings.put("payload", request.getBody());
                bindings.put("queryParams", request.getQueryParams());
            }

            Map<String, Object> out = runScript(script, bindings, "statusCode", "headers", "body");

            Object statusCodeObj = out.get("statusCode");
            Map<String, Object> headers = (Map) out.get("headers");
            String body = (String) out.get("body");

            int statusCode = response.getStatusCode();
            if (statusCodeObj != null && statusCodeObj instanceof Number) {
                statusCode = ((Number) statusCodeObj).intValue();
            }
            Map<String, List<String>> responseHeaders = response.getHeaders();
            if (headers != null) {
                responseHeaders = convertHeadersFromScript(headers);
            }
            if (body == null) {
                body = response.getBody();
            }

            return new OriginalHttpResponse(body, responseHeaders, statusCode);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in runPostRequestScript " + e.getMessage());
            e.printStackTrace();
            return response;
        }
    }

}
