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
import com.akto.dto.testing.TestingRunConfig.StreamingRequestConfig;
import com.akto.dto.testing.config.TestScript;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ApiExecutorUtil {

    private static final String EXECUTE_ONCE_PER_CONVERSATION = "executeOncePerConversation = true";
    private static final String FEATURE_TEST_PRE_SCRIPT = "TEST_PRE_SCRIPT";
    private static final int SCRIPT_CACHE_TTL_SECONDS = 5 * 60;
    private static final int CONVERSATION_STATE_TTL_SECONDS = 60 * 60;
    private static final String CONVERSATION_STATE = "conversationState";

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutorUtil.class, LogDb.TESTING);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static Map<String, Integer> lastFetchedMap = new HashMap<>();
    private static Map<String, TestScript> testScriptMap = new HashMap<>();

    /** When true, getCachedScript skips the SaaS feature gate (unit tests only). */
    private static volatile boolean bypassFeatureCheckForTest = false;

    private static String scriptCacheKey(int accountId, TestScript.Type type) {
        return accountId + "_" + (type != null ? type.name() : "ANY");
    }

    /** Cache of script results per conversationId when executeOncePerConversation = true. */
    private static final Map<String, ScriptResultCache> conversationScriptCache = new ConcurrentHashMap<>();

    /**
     * Value the POST_REQUEST script published for a conversation, handed back to the PRE_REQUEST
     * script of every later request in the same conversation. The first non-empty value wins, so a
     * token minted by the opening request (e.g. a chat message id) stays stable for the rest of it.
     */
    private static final Map<String, ConversationState> conversationStateMap = new ConcurrentHashMap<>();

    private static final class ConversationState {
        final String value;
        final int createdAt;

        ConversationState(String value) {
            this.value = value;
            this.createdAt = Context.now();
        }
    }

    private static String getConversationState(String conversationId) {
        if (conversationId == null) return null;
        ConversationState state = conversationStateMap.get(conversationId);
        if (state == null) return null;
        if (Context.now() - state.createdAt > CONVERSATION_STATE_TTL_SECONDS) {
            conversationStateMap.remove(conversationId);
            return null;
        }
        return state.value;
    }

    private static void putConversationState(String conversationId, String value) {
        if (conversationId == null || value == null || value.isEmpty()) return;
        int now = Context.now();
        conversationStateMap.entrySet()
                .removeIf(e -> now - e.getValue().createdAt > CONVERSATION_STATE_TTL_SECONDS);
        conversationStateMap.putIfAbsent(conversationId, new ConversationState(value));
    }

    /**
     * Nashorn never sees commented-out code, so neither should this marker check. Stripping comments
     * keeps a commented `executeOncePerConversation = true` from silently caching the script result.
     */
    static boolean hasExecuteOncePerConversationMarker(String script) {
        if (script == null) return false;
        String withoutComments = script.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        return withoutComments.contains(EXECUTE_ONCE_PER_CONVERSATION);
    }

    /**
     * Installs PRE/POST scripts for unit tests and bypasses the SaaS feature gate.
     * Call {@link #resetForTest()} in tearDown. Pass null to leave a type unset without
     * hitting DataActor (an empty script entry is cached for the TTL window).
     */
    static void installScriptsForTest(String preRequestJs, String postRequestJs) {
        bypassFeatureCheckForTest = true;
        int accountId = Context.getActualAccountId();
        cacheScriptForTest(accountId, TestScript.Type.PRE_REQUEST, preRequestJs);
        cacheScriptForTest(accountId, TestScript.Type.POST_REQUEST, postRequestJs);
    }

    private static void cacheScriptForTest(int accountId, TestScript.Type type, String javascript) {
        String key = scriptCacheKey(accountId, type);
        if (javascript == null || javascript.isEmpty()) {
            testScriptMap.put(key, null);
        } else {
            testScriptMap.put(key, new TestScript("test-" + type.name(), javascript, type, "test", Context.now()));
        }
        lastFetchedMap.put(key, Context.now());
    }

    /** Clears conversation/script caches and disables the test feature bypass. */
    static void resetForTest() {
        bypassFeatureCheckForTest = false;
        conversationStateMap.clear();
        conversationScriptCache.clear();
        testScriptMap.clear();
        lastFetchedMap.clear();
    }

    /**
     * Returns the script JavaScript for the given type, using 5-min cache. Returns null if feature not granted, or no script.
     */
    private static String getCachedScript(int accountId, TestScript.Type type) {
        if (!bypassFeatureCheckForTest) {
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, FEATURE_TEST_PRE_SCRIPT);
            if (!featureAccess.getIsGranted()) {
                return null;
            }
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
            boolean useConversationCache = conversationId != null && hasExecuteOncePerConversationMarker(script);
            ScriptResultCache cachedForScript = useConversationCache ? conversationScriptCache.get(conversationId) : null;

            if (cachedForScript != null) {
                applyCachedToRequest(originalHttpRequest, cachedForScript, testingRunConfig);
                return originalHttpRequest.getBody();
            }

            loggerMaker.infoAndAddToDb("Starting calculateHashAndAddAuth");

            Map<String, Object> bindings = new HashMap<>();
            bindings.put("method", originalHttpRequest.getMethod());
            bindings.put("headers", originalHttpRequest.getHeaders());
            bindings.put("url", originalHttpRequest.getUrl());
            bindings.put("payload", originalHttpRequest.getBody());
            bindings.put("queryParams", originalHttpRequest.getQueryParams());
            bindings.put(CONVERSATION_STATE, getConversationState(conversationId));

            Map<String, Object> out = runScript(script, bindings,
                    "method", "headers", "url", "payload", "queryParams",
                    "parsedPayloadTemp", "cachedMethod", "cachedHeaders", "cachedUrl", "cachedPayload", "cachedPayloadPatch", "cachedQueryParams",
                    "streamingRequest", "cachedStreamingRequest");

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
            String cachedPayloadPatch = (String) out.get("cachedPayloadPatch");
            String cachedQueryParams = (String) out.get("cachedQueryParams");

            Map<String, List<String>> hs = convertHeadersFromScript(headers);
            Map<String, List<String>> cachedHs = convertHeadersFromScript(cachedHeaders);

            StreamingRequestConfig parsedStreamingRequest = parseStreamingRequest(out.get("streamingRequest"));
            StreamingRequestConfig cachedParsedStreamingRequest = parseStreamingRequest(out.get("cachedStreamingRequest"));

            if (useConversationCache && conversationId != null) {
                conversationScriptCache.put(conversationId,
                        new ScriptResultCache(cachedMethod, cachedHs, cachedUrl, cachedPayload, cachedPayloadPatch, cachedQueryParams,
                                cachedParsedStreamingRequest != null ? cachedParsedStreamingRequest : parsedStreamingRequest));
            }

            originalHttpRequest.setBody(payload);
            originalHttpRequest.setMethod(method);
            originalHttpRequest.setUrl(url);
            originalHttpRequest.setHeaders(hs);
            originalHttpRequest.setQueryParams(queryParams);

            if (testingRunConfig != null && parsedStreamingRequest != null) {
                testingRunConfig.setStreamingRequest(parsedStreamingRequest);
            }

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

    @SuppressWarnings("unchecked")
    private static StreamingRequestConfig parseStreamingRequest(Object srObj) {
        // Nashorn's JS object (ScriptObjectMirror) implements java.util.Map. Cast to Map
        // (not the jdk.nashorn.* type, which is gone on JDK 17) so this compiles on both
        // JDK 8 and 17. Behaviour is unchanged from the original.
        if (!(srObj instanceof Map)) return null;
        Map<String, Object> srMirror = (Map<String, Object>) srObj;
        String srUrl = (String) srMirror.get("url");
        String srBody = (String) srMirror.get("body");
        String lastKey = (String) srMirror.get("lastKey");
        Map<String, String> srHeaders = new HashMap<>();
        Object headersObj = srMirror.get("headers");
        if (headersObj instanceof Map) {
            Map<String, Object> hm = (Map<String, Object>) headersObj;
            for (String key : hm.keySet()) {
                Object val = hm.get(key);
                if (val != null) srHeaders.put(key, val.toString());
            }
        }
        return new StreamingRequestConfig(srUrl, srBody, srHeaders, lastKey);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> convertHeadersFromScript(Map<String, Object> headers) {
        Map<String, List<String>> hs = new HashMap<>();
        if (headers == null) return hs;
        for (String key : headers.keySet()) {
            try {
                // Nashorn's JS array (ScriptObjectMirror) implements java.util.Map with array
                // indices as string keys "0","1",... Cast to Map (not the jdk.nashorn.* type,
                // which is gone on JDK 17) so this compiles on both JDK 8 and 17.
                Map<String, Object> scm = ((Map<String, Object>) headers.get(key));
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

    private static void applyCachedToRequest(OriginalHttpRequest request, ScriptResultCache cached,
            TestingRunConfig testingRunConfig) {
        if (cached.cachedMethod != null) request.setMethod(cached.cachedMethod);
        if (cached.cachedHeaders != null && !cached.cachedHeaders.isEmpty()) request.setHeaders(cached.cachedHeaders);
        if (cached.cachedUrl != null) request.setUrl(cached.cachedUrl);
        if (cached.cachedPayloadPatch != null) {
            request.setBody(applyPayloadPatch(request.getBody(), cached.cachedPayloadPatch));
        } else if (cached.cachedPayload != null) {
            request.setBody(cached.cachedPayload);
        }
        if (cached.cachedQueryParams != null) request.setQueryParams(cached.cachedQueryParams);
        if (testingRunConfig != null && cached.streamingRequest != null) {
            testingRunConfig.setStreamingRequest(cached.streamingRequest);
        }
    }

    private static String applyPayloadPatch(String body, String patch) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode target = mapper.readTree(body);
            return mapper.writeValueAsString(mapper.readerForUpdating(target).readValue(patch));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error applying cachedPayloadPatch: " + e.getMessage());
            return body;
        }
    }

    /** Cached script outputs per conversationId; when present, script is skipped and these are applied. */
    private static final class ScriptResultCache {
        final String cachedMethod;
        final Map<String, List<String>> cachedHeaders;
        final String cachedUrl;
        final String cachedPayload;
        final String cachedPayloadPatch;
        final String cachedQueryParams;
        final StreamingRequestConfig streamingRequest;

        ScriptResultCache(String cachedMethod, Map<String, List<String>> cachedHeaders, String cachedUrl,
                String cachedPayload, String cachedPayloadPatch, String cachedQueryParams,
                StreamingRequestConfig streamingRequest) {
            this.cachedMethod = cachedMethod;
            this.cachedHeaders = cachedHeaders;
            this.cachedUrl = cachedUrl;
            this.cachedPayload = cachedPayload;
            this.cachedPayloadPatch = cachedPayloadPatch;
            this.cachedQueryParams = cachedQueryParams;
            this.streamingRequest = streamingRequest;
        }
    }

    /**
     * Runs the POST_REQUEST test script on the response (e.g. to extract tokens from response).
     * Script context: request (method, headers, url, payload, queryParams), response (statusCode, headers, body).
     * Script may set statusCode, headers, body to modify the response, and conversationState to publish a
     * value to the PRE_REQUEST script of later requests in the same conversation.
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
            bindings.put("streamingResponse", response.getStreamingChunks());
            if (request != null) {
                bindings.put("method", request.getMethod());
                bindings.put("requestHeaders", request.getHeaders());
                bindings.put("url", request.getUrl());
                bindings.put("payload", request.getBody());
                bindings.put("queryParams", request.getQueryParams());
            }

            Map<String, Object> out = runScript(script, bindings, "statusCode", "headers", "body", CONVERSATION_STATE);

            Object conversationStateObj = out.get(CONVERSATION_STATE);
            if (conversationStateObj instanceof CharSequence && testingRunConfig != null) {
                putConversationState(testingRunConfig.getConversationId(), conversationStateObj.toString());
            }

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
