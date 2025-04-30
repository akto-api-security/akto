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
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.testing.config.TestScript;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

public class ApiExecutorUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutorUtil.class, LogDb.TESTING);

    private static Map<Integer, Integer> lastFetchedMap = new HashMap<>();
    private static Map<Integer, TestScript> testScriptMap = new HashMap<>();

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
            TestScript testScript = testScriptMap.getOrDefault(accountId, null);
            int lastTestScriptFetched = lastFetchedMap.getOrDefault(accountId, 0);
            if (Context.now() - lastTestScriptFetched > 5 * 60) {
                testScript = TestScriptsDao.instance.fetchTestScript();
                lastTestScriptFetched = Context.now();
                testScriptMap.put(accountId, testScript);
                lastFetchedMap.put(accountId, Context.now());
            }
            if (testScript != null && testScript.getJavascript() != null) {
                script = testScript.getJavascript();
            } else {
                return;
            }
            loggerMaker.infoAndAddToDb("Starting calculateHashAndAddAuth");

            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("nashorn");

            SimpleScriptContext sctx = ((SimpleScriptContext) engine.get("context"));
            sctx.setAttribute("method", originalHttpRequest.getMethod(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("headers", originalHttpRequest.getHeaders(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("url", originalHttpRequest.getPath(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("payload", originalHttpRequest.getBody(), ScriptContext.ENGINE_SCOPE);
            sctx.setAttribute("queryParams", originalHttpRequest.getQueryParams(), ScriptContext.ENGINE_SCOPE);
            engine.eval(script);

            String method = (String) sctx.getAttribute("method");
            Map<String, Object> headers = (Map) sctx.getAttribute("headers");
            String url = (String) sctx.getAttribute("url");
            String payload = (String) sctx.getAttribute("payload");
            String queryParams = (String) sctx.getAttribute("queryParams");

            Map<String, List<String>> hs = new HashMap<>();
            for (String key: headers.keySet()) {
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

            originalHttpRequest.setBody(payload);
            originalHttpRequest.setMethod(method);
            originalHttpRequest.setUrl(url);
            originalHttpRequest.setHeaders(hs);
            originalHttpRequest.setQueryParams(queryParams);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in calculateHashAndAddAuth " + e.getMessage() + " url " + originalHttpRequest.getUrl());
            e.printStackTrace();
            return;
        }
    }

}
