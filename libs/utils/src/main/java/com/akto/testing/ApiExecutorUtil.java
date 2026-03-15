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

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutorUtil.class, LogDb.TESTING);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static Map<String, Integer> lastFetchedMap = new HashMap<>();
    private static Map<String, TestScript> testScriptMap = new HashMap<>();

    private static String scriptCacheKey(int accountId, TestScript.Type type) {
        return accountId + "_" + (type != null ? type.name() : "ANY");
    }

    /** Cache of script results per conversationId when executeOncePerConversation = true. */
    private static final Map<String, ScriptResultCache> conversationScriptCache = new ConcurrentHashMap<>();

    public static String calculateHashAndAddAuth(OriginalHttpRequest originalHttpRequest, boolean executeScript,
            TestingRunConfig testingRunConfig) {
        if (!executeScript) {
            return originalHttpRequest.getBody();
        }
        try {
            int accountId = Context.getActualAccountId();
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, "TEST_PRE_SCRIPT");
            if (!featureAccess.getIsGranted()) {
                return originalHttpRequest.getBody();
            }

            String script;
            String cacheKey = scriptCacheKey(accountId, TestScript.Type.PRE_REQUEST);
            TestScript testScript = testScriptMap.getOrDefault(cacheKey, null);
            int lastTestScriptFetched = lastFetchedMap.getOrDefault(cacheKey, 0);
            if (Context.now() - lastTestScriptFetched > 5 * 60) {
                testScript = dataActor.fetchTestScript(TestScript.Type.PRE_REQUEST);
                lastTestScriptFetched = Context.now();
                testScriptMap.put(cacheKey, testScript);
                lastFetchedMap.put(cacheKey, Context.now());
            }
            if (testScript != null && testScript.getJavascript() != null) {
                script = testScript.getJavascript();
            } else {
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

            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("nashorn");

            SimpleScriptContext sctx = ((SimpleScriptContext) engine.get("context"));
            sctx.setAttribute("method", originalHttpRequest.getMethod(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("headers", originalHttpRequest.getHeaders(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("url", originalHttpRequest.getUrl(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("payload", originalHttpRequest.getBody(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("queryParams", originalHttpRequest.getQueryParams(), ScriptContext.ENGINE_SCOPE);
            engine.eval(script);

            String method = (String) sctx.getAttribute("method");
            Map<String, Object> headers = (Map) sctx.getAttribute("headers");
            String url = (String) sctx.getAttribute("url");
            String payload = (String) sctx.getAttribute("payload");
            String queryParams = (String) sctx.getAttribute("queryParams");
            String parsedPayloadTemp = (String) sctx.getAttribute("parsedPayloadTemp");
            String cachedMethod = (String) sctx.getAttribute("cachedMethod");
            Map<String, Object> cachedHeaders = (Map) sctx.getAttribute("cachedHeaders");
            String cachedUrl = (String) sctx.getAttribute("cachedUrl");
            String cachedPayload = (String) sctx.getAttribute("cachedPayload");
            String cachedQueryParams = (String) sctx.getAttribute("cachedQueryParams");


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
     * Script context: statusCode, headers, body. Script may set statusCode, headers, body to modify the response.
     */
    public static OriginalHttpResponse runPostRequestScript(OriginalHttpResponse response, boolean executeScript,
            TestingRunConfig testingRunConfig) {
        if (!executeScript || response == null) {
            return response;
        }
        try {
            int accountId = Context.getActualAccountId();
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, "TEST_POST_SCRIPT");
            if (!featureAccess.getIsGranted()) {
                return response;
            }

            String cacheKey = scriptCacheKey(accountId, TestScript.Type.POST_REQUEST);
            TestScript testScript = testScriptMap.getOrDefault(cacheKey, null);
            int lastTestScriptFetched = lastFetchedMap.getOrDefault(cacheKey, 0);
            if (Context.now() - lastTestScriptFetched > 5 * 60) {
                testScript = dataActor.fetchTestScript(TestScript.Type.POST_REQUEST);
                lastTestScriptFetched = Context.now();
                testScriptMap.put(cacheKey, testScript);
                lastFetchedMap.put(cacheKey, Context.now());
            }
            if (testScript == null || testScript.getJavascript() == null || testScript.getJavascript().isEmpty()) {
                return response;
            }

            String script = testScript.getJavascript();
            loggerMaker.infoAndAddToDb("Starting runPostRequestScript");

            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("nashorn");

            SimpleScriptContext sctx = ((SimpleScriptContext) engine.get("context"));
            sctx.setAttribute("statusCode", response.getStatusCode(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("headers", response.getHeaders(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("body", response.getBody(), ScriptContext.ENGINE_SCOPE);
            engine.eval(script);

            Object statusCodeObj = sctx.getAttribute("statusCode");
            Map<String, Object> headers = (Map) sctx.getAttribute("headers");
            String body = (String) sctx.getAttribute("body");

            int statusCode = response.getStatusCode();
            if (statusCodeObj != null) {
                if (statusCodeObj instanceof Number) {
                    statusCode = ((Number) statusCodeObj).intValue();
                }
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
