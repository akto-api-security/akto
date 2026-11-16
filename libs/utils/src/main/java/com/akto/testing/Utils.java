package com.akto.testing;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.bson.types.ObjectId;
import org.springframework.util.StringUtils;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.CollectionConditions.ConditionsType;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.Util;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.UrlModifierPayload;
import com.akto.dto.testing.WorkflowUpdatedSampleData;
import com.akto.dto.type.RequestTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.filter.Filter;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.akto.testing.login_flow.LoginFlowCookieJar;
import com.akto.testing.login_flow.PlaceholderException;
import com.akto.testing.login_flow.PlaceholderResolver;
import com.akto.testing_db_layer_client.ClientLayer;
import com.akto.util.JSONUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import okhttp3.MediaType;

import static com.akto.runtime.RuntimeUtil.extractAllValuesFromPayload;
import static com.akto.test_editor.Utils.deleteKeyFromPayload;
import static com.akto.test_editor.execution.Operations.deleteCookie;
import static com.akto.test_editor.execution.Operations.modifyCookie;

public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class, LogDb.TESTING);
    private static final ClientLayer clientLayer = new ClientLayer();
    private static final boolean shouldCallClientLayerForSampleData = System.getenv("TESTING_DB_LAYER_SERVICE_URL") != null && !System.getenv("TESTING_DB_LAYER_SERVICE_URL").isEmpty();

    // Applies a step's regex to the raw response body, for values that can't be reached through JSON paths
    // (e.g. a CSRF token inside an HTML page). The first capture group (or the whole match, if the regex has
    // no groups) is stored as <nodeId>.response.regex and every capture group N as <nodeId>.response.regex.N
    public static void populateRegexValues(Map<String, Object> valuesMap, String payloadStr, String nodeId, String regex) {
        if (regex == null || regex.trim().isEmpty() || payloadStr == null) return;

        Matcher matcher;
        try {
            matcher = Pattern.compile(regex).matcher(payloadStr);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("invalid regex for node " + nodeId + ": " + e.getMessage());
            return;
        }
        if (!matcher.find()) return;

        String key = nodeId + ".response.regex";
        String value = matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
        if (value != null) {
            valuesMap.put(key, value);
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null) {
                valuesMap.put(key + "." + i, matcher.group(i));
            }
        }
    }

    public static void populateValuesMap(Map<String, Object> valuesMap, String payloadStr, String nodeId, Map<String,
            List<String>> headers, boolean isRequest, String queryParams) {
        boolean isList = false;
        String reqOrResp = isRequest ? "request"  : "response";

        if (payloadStr == null) payloadStr = "{}";
        if (payloadStr.startsWith("[")) {
            payloadStr = "{\"json\": "+payloadStr+"}";
            isList = true;
        }

        String fullBodyKey = nodeId + "." + reqOrResp + "." + "body";

        valuesMap.put(fullBodyKey, payloadStr);

        BasicDBObject payloadObj;
        try {
            payloadObj = BasicDBObject.parse(payloadStr);
        } catch (Exception e) {
            boolean isPostFormData = payloadStr.contains("&") && payloadStr.contains("=");
            if (isPostFormData) {
                String mockUrl = "url?"+ payloadStr; // because getQueryJSON function needs complete url
                payloadObj = RequestTemplate.getQueryJSON(mockUrl);
            } else {
                payloadObj = BasicDBObject.parse("{}");
            }
        }

        BasicDBObject queryParamsObject = null;
        if (queryParams != null) {
            try {
                String mockUrl = "url?"+ queryParams; // because getQueryJSON function needs complete url
                queryParamsObject = RequestTemplate.getQueryJSON(mockUrl);
            } catch (Exception e) {
                ;
            }
        }

        Object obj;
        if (isList) {
            obj = payloadObj.get("json");
        } else {
            obj = payloadObj;
        }

        BasicDBObject flattened = JSONUtils.flattenWithDots(obj);


        for (String param: flattened.keySet()) {
            String key = nodeId + "." + reqOrResp + "." + "body" + "." + param;
            valuesMap.put(key, flattened.get(param));
        }

        if (queryParamsObject != null) {
            BasicDBObject queryFlattened = JSONUtils.flattenWithDots(queryParamsObject);
            for (String param: queryFlattened.keySet()) {
                String key = nodeId + "." + reqOrResp + "." + "query" + "." + param;
                valuesMap.put(key, queryFlattened.get(param));
            }
        }

        if (headers == null) return;
        List<String> rawSetCookies = new ArrayList<>();
        for (String headerName: headers.keySet()) {
            List<String> headerValues = headers.get(headerName);
            if (headerValues == null || headerValues.isEmpty()) continue;

            // HTTP/2 servers send lower-case names; also expose every header under its lower-case name
            List<String> names = new ArrayList<>();
            names.add(headerName);
            String lower = headerName.toLowerCase();
            if (!lower.equals(headerName) && !headers.containsKey(lower)) names.add(lower);

            String headerValue = headerValues.size() == 1 ? headerValues.get(0) : String.join(";", headerValues);
            for (String name : names) {
                valuesMap.put(nodeId + "." + reqOrResp + ".header." + name, headerValue);
            }

            boolean isSetCookie = lower.equals("set-cookie");
            if (!isSetCookie && !lower.equals("cookie")) continue;
            if (isSetCookie && !isRequest) rawSetCookies.addAll(headerValues);

            // individual cookie values: <node>.<req|resp>.header.<header name>.<cookie name>
            for (String value : headerValues) {
                if (value == null || value.isEmpty()) continue;
                List<String[]> cookies = new ArrayList<>();
                if (isSetCookie) {
                    LoginFlowCookieJar.ParsedSetCookie parsed = LoginFlowCookieJar.parseSetCookie(value);
                    if (parsed != null) cookies.add(new String[]{parsed.getName(), parsed.getValue()});
                } else {
                    cookies.addAll(LoginFlowCookieJar.parseCookieHeader(value));
                }
                for (String[] cookie : cookies) {
                    for (String name : names) {
                        valuesMap.put(nodeId + "." + reqOrResp + ".header." + name + "." + cookie[0], cookie[1]);
                    }
                }
            }
        }
        if (!rawSetCookies.isEmpty()) {
            valuesMap.put(nodeId + LoginFlowCookieJar.RAW_SET_COOKIE_SUFFIX, LoginFlowCookieJar.joinRaw(rawSetCookies));
        }
    }

    public static OriginalHttpRequest buildHttpRequest(WorkflowUpdatedSampleData updatedSampleData, Map<String, Object> valuesMap) throws Exception {
        return buildHttpRequest(updatedSampleData, valuesMap, null);
    }

    /**
     * @param loginFlowNodeId node id of a login-flow step (enables stepK aliases and ${cookies...}, scoped to
     *                        nodes before this one); null for API workflow tests (original placeholder behaviour)
     */
    public static OriginalHttpRequest buildHttpRequest(WorkflowUpdatedSampleData updatedSampleData, Map<String, Object> valuesMap, String loginFlowNodeId) throws Exception {

        String sampleData = updatedSampleData.getOrig();
        OriginalHttpRequest request = new OriginalHttpRequest();
        request.buildFromSampleMessage(sampleData);

        String queryParams = updatedSampleData.getQueryParams();
        String requestHeaders = updatedSampleData.getRequestHeaders();
        String requestPayload = updatedSampleData.getRequestPayload();
        String requestUrl = updatedSampleData.getRequestUrl();

        String queryFromReplacedUrl = null;
        boolean loginFlow = loginFlowNodeId != null;

        boolean userSuppliedQueryParamsNullOrEmpty = queryParams == null || queryParams.trim().length() == 0;
        if (requestUrl != null) {
            loggerMaker.infoAndAddToDb("requestUrl: " + requestUrl);
            // the URL itself has no request URL to scope ${cookies} to
            String rawUrl = loginFlow
                    ? resolveTemplate(requestUrl, valuesMap, PlaceholderResolver.Options.loginFlow(loginFlowNodeId, null))
                    : executeCode(requestUrl, valuesMap);
            // this url might contain urlQueryParams. We need to move it queryParams
            String[] rawUrlArr = rawUrl.split("\\?");
            request.setUrl(rawUrlArr[0]);
            if (rawUrlArr.length > 1) {
                queryFromReplacedUrl = rawUrlArr[1];
            }
            loggerMaker.infoAndAddToDb("final url: " + request.getUrl());
            loggerMaker.infoAndAddToDb("queryFromReplacedUrl length: " + (queryFromReplacedUrl == null ? 0 : queryFromReplacedUrl.length()));
        }

        PlaceholderResolver.Options options = loginFlow
                ? PlaceholderResolver.Options.loginFlow(loginFlowNodeId, request.getUrl())
                : PlaceholderResolver.Options.standard(true, true);

        if (userSuppliedQueryParamsNullOrEmpty) {
            loggerMaker.infoAndAddToDb("setting null");
            request.setQueryParams(null);
        }

        if (requestPayload != null) {
            String finalPayload = resolveTemplate(requestPayload, valuesMap, options);
            request.setBody(finalPayload);
        }

        if (requestHeaders != null) {
            String finalPayload = resolveTemplate(requestHeaders, valuesMap, options);
            Map<String, List<String>> res = OriginalHttpRequest.buildHeadersMap(finalPayload);
            request.setHeaders(res);
        }

        boolean queryFromReplacedUrlNullOrEmpty = queryFromReplacedUrl == null || queryFromReplacedUrl.trim().isEmpty();

        if (!userSuppliedQueryParamsNullOrEmpty) {
            loggerMaker.infoAndAddToDb("user has supplied query params");
            String finalQueryParams = resolveTemplate(queryParams, valuesMap, options);
            loggerMaker.infoAndAddToDb("finalQueryParams length: " + finalQueryParams.length());
            if (queryFromReplacedUrlNullOrEmpty) {
                request.setQueryParams(finalQueryParams);
            } else {
                // combine original query params and user defined query params and latter overriding former
                String combinedQueryParams = OriginalHttpRequest.combineQueryParams(queryFromReplacedUrl, finalQueryParams);
                loggerMaker.infoAndAddToDb("combinedQueryParams length: " + (combinedQueryParams == null ? 0 : combinedQueryParams.length()));
                request.setQueryParams(combinedQueryParams);
            }
        } else if (!queryFromReplacedUrlNullOrEmpty) {
            request.setQueryParams(queryFromReplacedUrl);
        }

        return request;
    }

    public static final String JS_ENGINE_UNAVAILABLE = "JS expression (#[ ... ]#) can't be evaluated: no JavaScript "
            + "engine (Nashorn) is available in this runtime; use filters instead, e.g. "
            + "${x1.response.body.token|regex:<pattern>} or |urlencode";
    public static final String TEST_VALIDATOR_NOT_EVALUATED = "testValidatorCode was not evaluated (no JavaScript "
            + "engine in this runtime, or the script failed or did not return a boolean); see logs";
    // same pattern the original executeCode used (single line, non-greedy)
    private static final Pattern JS_BLOCK = Pattern.compile("#\\[(.*?)]#");
    private static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();
    // Nashorn: built in on Java 8; on JDK 11+ builds the standalone nashorn-core comes from the nashorn-standalone
    // profile in libs/utils/pom.xml. A fresh engine per template, as before. Replaceable in tests only.
    static volatile Supplier<ScriptEngine> jsEngineSupplier = () -> SCRIPT_ENGINE_MANAGER.getEngineByName("nashorn");
    // mini-only: ${x0.unique_<suffix>} generates "<suffix>_<nanoTime>" when the key is absent (kept from the old resolver)
    private static final Pattern UNIQUE_PLACEHOLDER = Pattern.compile("\\$\\{\\s*(x0\\.[^|}\\s]+)");
    private static final String UNIQUE_PREFIX = "x0.unique_";

    // Resolves placeholders, then evaluates #[ ... ]# blocks with the JS engine (see resolveTemplate).
    public static String executeCode(String ogPayload, Map<String, Object> valuesMap) throws Exception {
        return resolveTemplate(ogPayload, valuesMap, PlaceholderResolver.Options.standard(true, true));
    }

    // mini stays strict: a missing key always throws (akto's 4-arg variant can skip it)
    public static String replaceVariables(String payload, Map<String, Object> valuesMap, boolean escapeString) throws Exception {
        return resolvePlaceholders(payload, valuesMap, PlaceholderResolver.Options.standard(escapeString, true));
    }

    public static String resolvePlaceholders(String payload, Map<String, Object> valuesMap, PlaceholderResolver.Options options) throws PlaceholderException {
        try {
            return PlaceholderResolver.resolve(payload, withUniqueValues(payload, valuesMap), options);
        } catch (PlaceholderException e) {
            // resolver messages name keys and patterns only, never values
            loggerMaker.errorAndAddToDb("placeholder resolution failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Resolves a request template and evaluates the #[ ... ]# blocks written in it, as executeCode always did.
     * Differences from the original (which substituted the whole payload, then evaluated every block in the result):
     * - only blocks present in the template are evaluated; a resolved value containing "#[ ]#" is never run as JS
     * - placeholders inside a block get JS-string escaping (' -> \'), outside they get JSON escaping
     * - no JS engine in this runtime -> explicit error instead of a NullPointerException
     * A block whose script fails (or returns null) is left in place, as before, and the failure is logged.
     */
    public static String resolveTemplate(String template, Map<String, Object> valuesMap, PlaceholderResolver.Options options) throws PlaceholderException {
        if (template == null) return null;
        Matcher block = JS_BLOCK.matcher(template);
        if (!block.find()) {
            return resolvePlaceholders(template, valuesMap, options);
        }
        ScriptEngine engine = jsEngineSupplier.get();
        if (engine == null) {
            loggerMaker.errorAndAddToDb(JS_ENGINE_UNAVAILABLE);
            throw new PlaceholderException(JS_ENGINE_UNAVAILABLE);
        }
        StringBuilder out = new StringBuilder(template.length());
        int last = 0;
        do {
            out.append(resolvePlaceholders(template.substring(last, block.start()), valuesMap, options));
            String code = resolvePlaceholders(block.group(1), valuesMap, options.forJsExpression());
            out.append(evaluateJsBlock(engine, code, block.group()));
            last = block.end();
        } while (block.find());
        out.append(resolvePlaceholders(template.substring(last), valuesMap, options));
        return out.toString();
    }

    private static String evaluateJsBlock(ScriptEngine engine, String resolvedCode, String originalBlock) {
        String code = resolvedCode.trim();
        if (!code.endsWith(";")) code = code + ";";
        try {
            Object val = engine.eval(code);
            if (val != null) return val.toString();
            loggerMaker.errorAndAddToDb("JS expression returned null; block left unevaluated");
        } catch (ScriptException se) {
            // the message can quote substituted values, so log only where it failed
            loggerMaker.errorAndAddToDb("JS expression failed at line " + se.getLineNumber() + ", column "
                    + se.getColumnNumber() + "; block left unevaluated");
        }
        return originalBlock;
    }

    public static boolean isJsEngineAvailable() {
        return jsEngineSupplier.get() != null;
    }

    // Overlay (the caller's map is never modified) holding a generated value for each absent ${x0.unique_<suffix>}.
    static Map<String, Object> withUniqueValues(String template, Map<String, Object> valuesMap) {
        if (template == null || !template.contains("${")) return valuesMap;
        Map<String, Object> overlay = null;
        Matcher m = UNIQUE_PLACEHOLDER.matcher(template);
        while (m.find()) {
            String key = m.group(1);
            if (!key.toLowerCase().startsWith(UNIQUE_PREFIX)) continue;
            if (valuesMap != null && valuesMap.get(key) != null) continue;
            if (overlay == null) overlay = valuesMap == null ? new HashMap<String, Object>() : new HashMap<>(valuesMap);
            if (!overlay.containsKey(key)) {
                overlay.put(key, key.substring(key.indexOf('_') + 1) + "_" + System.nanoTime());
            }
        }
        return overlay == null ? valuesMap : overlay;
    }

    public static boolean hasTestValidator(String testValidatorCode) {
        return testValidatorCode != null && !testValidatorCode.trim().isEmpty();
    }

    // Original behaviour: a validator returning true means "not vulnerable"; anything else that runs is false.
    public static boolean validateTest(String testValidatorCode, Map<String, Object> valuesMap) {
        return Boolean.TRUE.equals(evaluateTestValidator(testValidatorCode, valuesMap));
    }

    /**
     * Runs the JS validator like validateTest always did, but tells "not evaluated" apart from "not vulnerable":
     * returns null when there is no validator, no JS engine, or the script fails / doesn't return a boolean.
     * Callers with a validator set should report null as TEST_VALIDATOR_NOT_EVALUATED.
     */
    public static Boolean evaluateTestValidator(String testValidatorCode, Map<String, Object> valuesMap) {
        if (!hasTestValidator(testValidatorCode)) return null;
        ScriptEngine engine = jsEngineSupplier.get();
        if (engine == null) {
            loggerMaker.errorAndAddToDb("testValidatorCode not evaluated: no JavaScript engine in this runtime");
            return null;
        }
        try {
            String code = resolvePlaceholders(testValidatorCode.trim(), valuesMap,
                    PlaceholderResolver.Options.standard(true, true).forJsExpression());
            Object o = engine.eval(code);
            if (!(o instanceof Boolean)) {
                loggerMaker.errorAndAddToDb("testValidatorCode not evaluated: result is not a boolean");
                return null;
            }
            // resolved code and values can carry secrets: log only the outcome
            loggerMaker.infoAndAddToDb("test validator evaluated: " + o);
            return !((Boolean) o);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("testValidatorCode not evaluated: " + e.getClass().getSimpleName());
            return null;
        }
    }

    public static MediaType getMediaType(String fileUrl) {
        String fileExtension = "";

        int dotIndex = fileUrl.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileUrl.length() - 1) {
            fileExtension = fileUrl.substring(dotIndex + 1).toLowerCase();
        }
        switch (fileExtension) {
            case "jpg":
            case "jpeg":
                return MediaType.parse("image/jpeg");
            case "png":
                return MediaType.parse("image/png");
            case "gif":
                return MediaType.parse("image/gif");
            case "txt":
                return MediaType.parse("text/plain");
            case "html":
                return MediaType.parse("text/html");
            case "pdf":
                return MediaType.parse("application/pdf");
            case "doc":
            case "docx":
                return MediaType.parse("application/msword");
            case "xls":
            case "xlsx":
                return MediaType.parse("application/vnd.ms-excel");
            case "json":
                return MediaType.parse("application/json");
            default:
                return MediaType.parse("application/octet-stream"); // Fallback for unknown types
        }
    }


    public static double compareWithOriginalResponse(String originalPayload, String currentPayload, Map<String, Boolean> comparisonExcludedKeys) {
        if (originalPayload == null && currentPayload == null) return 100;
        if (originalPayload == null || currentPayload == null) return 0;

        String trimmedOriginalPayload = originalPayload.trim();
        String trimmedCurrentPayload = currentPayload.trim();
        if (trimmedCurrentPayload.equals(trimmedOriginalPayload)) return 100;

        Map<String, Set<String>> originalResponseParamMap = new HashMap<>();
        Map<String, Set<String>> currentResponseParamMap = new HashMap<>();
        try {
            extractAllValuesFromPayload(originalPayload, originalResponseParamMap);
            extractAllValuesFromPayload(currentPayload, currentResponseParamMap);
        } catch (Exception e) {
            return 0.0;
        }

        if (originalResponseParamMap.keySet().size() == 0 && currentResponseParamMap.keySet().size() == 0) {
            return 100.0;
        }

        Set<String> visited = new HashSet<>();
        int matched = 0;
        for (String k1: originalResponseParamMap.keySet()) {
            if (visited.contains(k1) || comparisonExcludedKeys.containsKey(k1)) continue;
            visited.add(k1);
            Set<String> v1 = originalResponseParamMap.get(k1);
            Set<String> v2 = currentResponseParamMap.get(k1);
            if (Objects.equals(v1, v2)) matched +=1;
        }

        for (String k1: currentResponseParamMap.keySet()) {
            if (visited.contains(k1) || comparisonExcludedKeys.containsKey(k1)) continue;
            visited.add(k1);
            Set<String> v1 = originalResponseParamMap.get(k1);
            Set<String> v2 = currentResponseParamMap.get(k1);
            if (Objects.equals(v1, v2)) matched +=1;
        }

        int visitedSize = visited.size();
        if (visitedSize == 0) return 0.0;

        double result = (100.0*matched)/visitedSize;

        if (Double.isFinite(result)) {
            return result;
        } else {
            return 0.0;
        }

    }

    public static ValidationResult validateFilter(FilterNode filterNode, RawApi rawApi, ApiInfoKey apiInfoKey, Map<String, Object> varMap, String logId) {
        if (filterNode == null) return new ValidationResult(true, "");
        if (rawApi == null) return  new ValidationResult(true, "raw api is null");
        return validate(filterNode, rawApi, null, apiInfoKey,"filter", varMap, logId);
    }

    private static ValidationResult validate(FilterNode node, RawApi rawApi, RawApi testRawApi, ApiInfoKey apiInfoKey, String context, Map<String, Object> varMap, String logId) {
        Filter filter = new Filter();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(node, rawApi, testRawApi, apiInfoKey, null, null , false,context, varMap, logId, false);
        return new ValidationResult(dataOperandsFilterResponse.getResult(), dataOperandsFilterResponse.getValidationReason());
    }

    public static void modifyBodyOperations(OriginalHttpRequest httpRequest, List<ConditionsType> modifyOperations, List<ConditionsType> addOperations, List<ConditionsType> deleteOperations){
        String oldReqBody = httpRequest.getBody();
        if(oldReqBody == null || oldReqBody.isEmpty()){
            return ;
        }
        BasicDBObject payload;

        if (oldReqBody != null && oldReqBody.startsWith("[")) {
            oldReqBody = "{\"json\": "+oldReqBody+"}";
        }
        try {
            payload = BasicDBObject.parse(oldReqBody);
        } catch (Exception e) {
            payload = new BasicDBObject();
        }

        if(!modifyOperations.isEmpty()){
            for(ConditionsType condition : modifyOperations){
                Object value = condition.getValue();
                try {
                    value = replaceVariables(value.toString(), new HashMap<>(), false);
                } catch (Exception e) {
                    ;
                }
                Util.modifyValueInPayload(payload, null, condition.getKey(), value);
            }
        }
        if(!addOperations.isEmpty()){
            for(ConditionsType condition : addOperations){
                payload.put(condition.getKey(), condition.getValue());
            }
        }

        if(!deleteOperations.isEmpty()){
            for(ConditionsType condition : deleteOperations){
                deleteKeyFromPayload(payload, null, condition.getKey());
            }
        }

        String payloadStr = payload.toJson();

        if (payload.size() == 1 && payload.containsKey("json")) {
            Object jsonValue = payload.get("json");
            if (jsonValue instanceof BasicDBList) {
                payloadStr = payload.get("json").toString();
            }
        }


        httpRequest.setBody(payloadStr);
    }

    public static void modifyHeaderOperations(OriginalHttpRequest httpRequest, List<ConditionsType> modifyOperations, List<ConditionsType> addOperations, List<ConditionsType> deleteOperations){
        Map<String, List<String>> reqHeaders = httpRequest.getHeaders();

        if(!addOperations.isEmpty()){
            for(ConditionsType condition : addOperations){
                List<String> valList = Collections.singletonList(condition.getValue());
                reqHeaders.put(condition.getKey(), valList);
            }
        }

        if(!deleteOperations.isEmpty()){
            for(ConditionsType condition : deleteOperations){
                String key = condition.getKey();
                deleteCookie(reqHeaders, key, null);
                if (reqHeaders.containsKey(key)) {
                    reqHeaders.remove(key);
                }
            }
        }

        if(!modifyOperations.isEmpty()){
            for(ConditionsType condition : modifyOperations){
                String key = condition.getKey();
                modifyCookie(reqHeaders, key, condition.getValue());
                List<String> valList = Collections.singletonList(condition.getValue());
                reqHeaders.put(condition.getKey(), valList);
            }
        }
        
        
    }

    public static void modifyQueryOperations(OriginalHttpRequest httpRequest, List<ConditionsType> modifyOperations, List<ConditionsType> addOperations, List<ConditionsType> deleteOperations){

        // since this is being used with payload conditions, we are not supporting any add operations, operations are done only on existing query keys

        String query = httpRequest.getQueryParams();
        if(query == null || query.isEmpty()){
            return ;
        }

        BasicDBObject queryParamObj = RequestTemplate.getQueryJSON(httpRequest.getUrl() + "?" + query);

        if(!modifyOperations.isEmpty()){
            for(ConditionsType condition : modifyOperations){
                if(queryParamObj.containsKey(condition.getKey())){
                    queryParamObj.put(condition.getKey(), condition.getValue());
                }
            }
        }


        if(!deleteOperations.isEmpty()){
            for(ConditionsType condition : deleteOperations){
                if(queryParamObj.containsKey(condition.getKey())){
                    queryParamObj.remove(condition.getKey());
                }
            }
        } 
        
        String queryParams = "";
        for (String key: queryParamObj.keySet()) {
            queryParams +=  (key + "=" + queryParamObj.get(key) + "&");
        }
        if (queryParams.length() > 0) {
            queryParams = queryParams.substring(0, queryParams.length() - 1);
        }

        httpRequest.setQueryParams(queryParams);
    }


    public static boolean writeJsonContentInFile(String folderName, String fileName, Object content){
        try {
            File folder = new File(folderName);
            if (!folder.exists() && !folder.mkdirs()) {
                loggerMaker.errorAndAddToDb("Unable to create folder for writing json content: " + folderName);
                return false;
            }
            File file = new File(folder, fileName);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, content);
            return true;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error writing json content in file " + fileName + ": " + e.getMessage());
            return false;
        }
    }
    public static void modifyUrlParamOperations(OriginalHttpRequest originalHttpRequest, List<ConditionsType> modifyUrlParams, String operationType) {
        if (modifyUrlParams == null || modifyUrlParams.isEmpty()) {
            return;
        }

        for (ConditionsType condition : modifyUrlParams) {
            try {
                // get the urlsList for the filter condition
                Set<String> urlsList = condition.getUrlsList();
                if (urlsList == null || urlsList.isEmpty()) {
                    continue;
                }

                // iterate over the urlsList and modify the url
                String currentUrl = originalHttpRequest.getUrl();
                String currentMethod = originalHttpRequest.getMethod();
                if(!urlsList.contains(currentMethod + " " + currentUrl)){
                    continue;
                }

                // now we have to call modifyURL
                UrlModifierPayload urlModifierPayload = new UrlModifierPayload("", condition.getPosition(), condition.getValue(), operationType);
                String newUrl = com.akto.test_editor.Utils.buildNewUrl(urlModifierPayload, currentUrl);
                originalHttpRequest.setUrl(newUrl);
            } catch (Exception e) {
                // Log error but continue with other operations
                System.err.println("Error modifying URL parameter: " + e.getMessage());
            }
        }
    }

    
    public static <T> T readJsonContentFromFile(String folderName, String fileName, Class<T> valueType) {
        File file = new File(folderName, fileName);
        if (!file.exists()) {
            // nothing written yet, not an error
            return null;
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(file, valueType);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error reading json content from file " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    public static TestingRunResult generateFailedRunResultForMessage(ObjectId testingRunId,ApiInfoKey apiInfoKey, String testSuperType, 
        String testSubType, ObjectId testRunResultSummaryId, List<String> messages, String errorMessage) {

        TestingRunResult testingRunResult = null;       
        // Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();
        List<GenericTestResult> testResults = new ArrayList<>();
        String failMessage = errorMessage;
        String msg = null;
        if(shouldCallClientLayerForSampleData){
            try {
                msg = clientLayer.fetchLatestSample(apiInfoKey);
                if(msg == null){
                    loggerMaker.infoAndAddToDb("No fetchLatestSample response found for " + apiInfoKey.toString() + " from testing db layer in generateFailedRunResultForMessage");
                }
            } catch (Exception e) {
            }   
        }
        
        if(!StringUtils.hasLength(failMessage) && (messages == null || messages.isEmpty()) && msg == null){
            failMessage = TestError.NO_PATH.getMessage();
        }
            
        if(failMessage != null){
            testResults.add(new TestResult(null, null, Collections.singletonList(failMessage),0, false, Confidence.HIGH, null));
            testingRunResult = new TestingRunResult(
                testingRunId, apiInfoKey, testSuperType, testSubType, testResults,
                false, new ArrayList<>(), 100, Context.now(),
                Context.now(), testRunResultSummaryId, null, Collections
                        .singletonList(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, failMessage)));
        }       
        return testingRunResult;
    }

    public static boolean createFolder(String folderName){
        File statusDir = new File(folderName);
        
        if (!statusDir.exists()) {
            boolean created = statusDir.mkdirs();
            if (!created) {
                System.err.println("Failed to create directory: " + folderName);
                return false;
            }
            return true;
        }
        return false;
    }
}
