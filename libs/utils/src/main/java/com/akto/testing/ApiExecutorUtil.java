package com.akto.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.context.Context;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.testing.config.TestScript;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

public class ApiExecutorUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutorUtil.class, LogDb.TESTING);

    private static Map<Integer, Integer> lastFetchedMap = new HashMap<>();
    private static Map<Integer, TestScript> testScriptMap = new HashMap<>();
    private static Map<Integer, Integer> lastFetchedMapPostRequest = new HashMap<>();
    private static Map<Integer, TestScript> testScriptMapPostRequest = new HashMap<>();

    private static TestScript getTestScriptWithCache(int accountId, TestScript.Type type,
            Map<Integer, Integer> lastFetched, Map<Integer, TestScript> scriptMap) {
        TestScript testScript = scriptMap.getOrDefault(accountId, null);
        int lastFetchedTime = lastFetched.getOrDefault(accountId, 0);
        if (Context.now() - lastFetchedTime > 5 * 60) {
            testScript = TestScriptsDao.instance.fetchTestScript(type);
            scriptMap.put(accountId, testScript);
            lastFetched.put(accountId, Context.now());
        }
        return testScript;
    }

    private static ScriptEngine createEngineAndContext() {
        ScriptEngineManager manager = new ScriptEngineManager();
        return manager.getEngineByName("nashorn");
    }

    private static SimpleScriptContext getScriptContext(ScriptEngine engine) {
        return (SimpleScriptContext) engine.get("context");
    }

    private static void setRequestAttributes(ScriptContext sctx, OriginalHttpRequest request) {
        sctx.setAttribute("method", request.getMethod(), ScriptContext.ENGINE_SCOPE);
        sctx.setAttribute("headers", request.getHeaders(), ScriptContext.ENGINE_SCOPE);
        sctx.setAttribute("url", request.getUrl(), ScriptContext.ENGINE_SCOPE);
        sctx.setAttribute("payload", request.getBody(), ScriptContext.ENGINE_SCOPE);
        sctx.setAttribute("queryParams", request.getQueryParams(), ScriptContext.ENGINE_SCOPE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> convertHeadersFromScript(Map<String, Object> headers) {
        if (headers == null) {
            return new HashMap<>();
        }
        Map<String, List<String>> hs = new HashMap<>();
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

    public static void calculateHashAndAddAuth(OriginalHttpRequest originalHttpRequest, boolean executeScript) {
        if (!executeScript) {
            return;
        }
        try {
            int accountId = Context.accountId.get();
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, "TEST_PRE_SCRIPT");
            if (!featureAccess.getIsGranted()) {
                return;
            }

            String script;
            TestScript testScript = getTestScriptWithCache(accountId, TestScript.Type.PRE_REQUEST, lastFetchedMap, testScriptMap);
            if (testScript != null && testScript.getJavascript() != null) {
                script = testScript.getJavascript();
            } else {
                return;
            }
            loggerMaker.infoAndAddToDb("Starting calculateHashAndAddAuth");

            ScriptEngine engine = createEngineAndContext();
            SimpleScriptContext sctx = getScriptContext(engine);
            setRequestAttributes(sctx, originalHttpRequest);
            engine.eval(script);

            String method = (String) sctx.getAttribute("method");
            Map<String, Object> headers = (Map) sctx.getAttribute("headers");
            String url = (String) sctx.getAttribute("url");
            String payload = (String) sctx.getAttribute("payload");
            String queryParams = (String) sctx.getAttribute("queryParams");

            originalHttpRequest.setBody(payload);
            originalHttpRequest.setMethod(method);
            originalHttpRequest.setUrl(url);
            originalHttpRequest.setHeaders(convertHeadersFromScript(headers));
            originalHttpRequest.setQueryParams(queryParams);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in calculateHashAndAddAuth " + e.getMessage() + " url " + originalHttpRequest.getUrl());
            e.printStackTrace();
            return;
        }
    }

    public static void applyPostRequestScript(OriginalHttpRequest originalHttpRequest, OriginalHttpResponse originalHttpResponse, boolean executeScript) {
        if (!executeScript || originalHttpResponse == null) {
            return;
        }
        try {
            int accountId = Context.accountId.get();
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, "TEST_PRE_SCRIPT");
            if (!featureAccess.getIsGranted()) {
                return;
            }

            TestScript testScript = getTestScriptWithCache(accountId, TestScript.Type.POST_REQUEST, lastFetchedMapPostRequest, testScriptMapPostRequest);
            String script;
            if (testScript != null && testScript.getJavascript() != null) {
                script = testScript.getJavascript();
            } else {
                return;
            }
            loggerMaker.infoAndAddToDb("Starting applyPostRequestScript");

            ScriptEngine engine = createEngineAndContext();
            SimpleScriptContext sctx = getScriptContext(engine);
            if (originalHttpRequest != null) {
                setRequestAttributes(sctx, originalHttpRequest);
            }
            sctx.setAttribute("body", originalHttpResponse.getBody(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("responseHeaders", originalHttpResponse.getHeaders(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("statusCode", originalHttpResponse.getStatusCode(), ScriptContext.ENGINE_SCOPE);
            engine.eval(script);

            String body = (String) sctx.getAttribute("body");
            Map<String, Object> headers = (Map) sctx.getAttribute("responseHeaders");
            Object statusCodeObj = sctx.getAttribute("statusCode");

            if (body != null) {
                originalHttpResponse.setBody(body);
            }
            if (statusCodeObj != null) {
                int statusCode = statusCodeObj instanceof Number ? ((Number) statusCodeObj).intValue() : Integer.parseInt(String.valueOf(statusCodeObj));
                originalHttpResponse.setStatusCode(statusCode);
            }
            if (headers != null) {
                originalHttpResponse.setHeaders(convertHeadersFromScript(headers));
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in applyPostRequestScript " + e.getMessage());
            e.printStackTrace();
        }
    }

}
