package com.akto.test_editor;

import com.akto.dao.monitoring.FilterConfigYamlParser;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.ExecutorConfigParserResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.dto.type.URLMethods;
import com.akto.test_editor.execution.ParseAndExecute;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ModifyHeaderRegexReplaceTest {

    private RawApi buildRawApi(String url, Map<String, List<String>> requestHeaders) {
        OriginalHttpRequest req = new OriginalHttpRequest();
        req.setMethod("GET");
        req.setUrl(url);
        req.setHeaders(requestHeaders);
        req.setBody("{}");

        OriginalHttpResponse resp = new OriginalHttpResponse();
        resp.setBody("{}");
        resp.setHeaders(new HashMap<>());
        resp.setStatusCode(200);

        return new RawApi(req, resp, "");
    }

    private Map<String, List<String>> makeHeaders(String key, String value) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(key, Collections.singletonList(value));
        return headers;
    }

    private ApiInfo.ApiInfoKey dummyApiInfoKey() {
        return new ApiInfo.ApiInfoKey(0, "/test", URLMethods.Method.GET);
    }

    private Map<String, Map<String, String>> buildRegexReplace(String regex, String replaceWith) {
        Map<String, String> regexInfo = new LinkedHashMap<>();
        regexInfo.put("regex", regex);
        regexInfo.put("replace_with", replaceWith);
        Map<String, Map<String, String>> regexReplace = new LinkedHashMap<>();
        regexReplace.put("regex_replace", regexInfo);
        return regexReplace;
    }

    // --- Filter 1: service.(dev|prod|prd)-api.tapestry.com -> service.qa-api.tapestry.com ---

    @Test
    public void testDevApiToQaApi() {
        RawApi rawApi = buildRawApi("https://empdiscount.dev-api.tapestry.com/api/test",
                makeHeaders("host", "empdiscount.dev-api.tapestry.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey());

        assertEquals("empdiscount.qa-api.tapestry.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testProdApiToQaApi() {
        RawApi rawApi = buildRawApi("https://fiscalcalendar.prod-api.tapestry.com/",
                makeHeaders("host", "fiscalcalendar.prod-api.tapestry.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey());

        assertEquals("fiscalcalendar.qa-api.tapestry.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testPrdApiToQaApi() {
        RawApi rawApi = buildRawApi("https://storedetails.prd-api.tapestry.com/",
                makeHeaders("host", "storedetails.prd-api.tapestry.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey());

        assertEquals("storedetails.qa-api.tapestry.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testSpiDevApiToSpiQaApi() {
        RawApi rawApi = buildRawApi("https://membership-admin-auth.spidev-api.tapestry.com/",
                makeHeaders("host", "membership-admin-auth.spidev-api.tapestry.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey());

        assertEquals("membership-admin-auth.spiqa-api.tapestry.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    // --- Filter 2: x-(dev|prd|prod)(.|-)  ->  x-qa(.|-)  ---

    @Test
    public void testPrdSuffixToQa() {
        RawApi rawApi = buildRawApi("https://api-prd.ods.tapestry.com/aptosCOH",
                makeHeaders("host", "api-prd.ods.tapestry.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("-(dev|prd|prod)([.-])", "-qa$2"),
                new HashMap<>(), dummyApiInfoKey());

        assertEquals("api-qa.ods.tapestry.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testDevSuffixToQa() {
        RawApi rawApi = buildRawApi("https://app.scan-dev.coach.com/proxy/sfcc",
                makeHeaders("host", "app.scan-dev.coach.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("-(dev|prd|prod)([.-])", "-qa$2"),
                new HashMap<>(), dummyApiInfoKey());

        assertEquals("app.scan-qa.coach.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testDevMiddleToQa() {
        RawApi rawApi = buildRawApi("https://subspace-dev-us-west-2.ods.tapestry.com/",
                makeHeaders("host", "subspace-dev-us-west-2.ods.tapestry.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("-(dev|prd|prod)([.-])", "-qa$2"),
                new HashMap<>(), dummyApiInfoKey());

        assertEquals("subspace-qa-us-west-2.ods.tapestry.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    // --- Edge cases ---

    @Test
    public void testQaHostUnchanged() {
        RawApi rawApi = buildRawApi("https://empdiscount.qa-api.tapestry.com/",
                makeHeaders("host", "empdiscount.qa-api.tapestry.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey());

        assertEquals("empdiscount.qa-api.tapestry.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testMissingHeaderReturnsMessage() {
        RawApi rawApi = buildRawApi("https://example.com/",
                makeHeaders("content-type", "application/json"));

        ExecutorSingleOperationResp resp = Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey());

        assertTrue(resp.getErrMsg().contains("header key not present"));
    }

    @Test
    public void testStaticModifyHeaderStillWorks() {
        RawApi rawApi = buildRawApi("https://old.example.com/",
                makeHeaders("host", "old.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host", "new.example.com",
                new HashMap<>(), dummyApiInfoKey());

        assertEquals("new.example.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testRegexReplaceOnCustomHeader() {
        RawApi rawApi = buildRawApi("https://example.com/",
                makeHeaders("x-forwarded-host", "service.dev-api.tapestry.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "x-forwarded-host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey());

        assertEquals("service.qa-api.tapestry.com", rawApi.fetchReqHeaders().get("x-forwarded-host").get(0));
    }

    // --- End-to-end: YAML -> parse -> executor nodes -> execute on RawApi ---

    private RawApi executeYamlFilterOnRawApi(String yaml, RawApi rawApi) throws Exception {
        FilterConfig config = FilterConfigYamlParser.parseTemplate(yaml, true);
        assertNotNull("FilterConfig should parse", config);

        ExecutorConfigParserResult executorResult = config.getExecutor();
        assertNotNull("Executor should not be null", executorResult);
        assertTrue("Executor should be valid: " + executorResult.getErrMsg(), executorResult.getIsValid());

        List<ExecutorNode> nodes = ParseAndExecute.getExecutorNodes(executorResult.getNode());
        assertFalse("Executor nodes should not be empty", nodes.isEmpty());

        Map<String, Object> varMap = config.resolveVarMap();
        return new ParseAndExecute().execute(nodes, rawApi, dummyApiInfoKey(), varMap, "test-log-id");
    }

    private static final String FILTER1_YAML =
            "id: MERGE_ENV_API_HOSTS\n" +
            "filter:\n" +
            "  request_headers:\n" +
            "    for_one:\n" +
            "      key:\n" +
            "        eq: host\n" +
            "      value:\n" +
            "        regex: \"\\\\.(spi)?(dev|prod|prd)-api\\\\.tapestry\\\\.com$\"\n" +
            "execute:\n" +
            "  type: single\n" +
            "  requests:\n" +
            "    - req:\n" +
            "        - modify_header:\n" +
            "            host:\n" +
            "              regex_replace:\n" +
            "                regex: \"(dev|prod|prd)-api\\\\.\"\n" +
            "                replace_with: \"qa-api.\"\n";

    private static final String FILTER2_YAML =
            "id: MERGE_ENV_SUFFIX_HOSTS\n" +
            "filter:\n" +
            "  request_headers:\n" +
            "    for_one:\n" +
            "      key:\n" +
            "        eq: host\n" +
            "      value:\n" +
            "        regex: \"-(dev|prd|prod)([.-])\"\n" +
            "execute:\n" +
            "  type: single\n" +
            "  requests:\n" +
            "    - req:\n" +
            "        - modify_header:\n" +
            "            host:\n" +
            "              regex_replace:\n" +
            "                regex: \"-(dev|prd|prod)([.-])\"\n" +
            "                replace_with: \"-qa$2\"\n";

    @Test
    public void testEndToEnd_Filter1_DevApiToQaApi() throws Exception {
        RawApi rawApi = buildRawApi("https://empdiscount.dev-api.tapestry.com/",
                makeHeaders("host", "empdiscount.dev-api.tapestry.com"));

        RawApi result = executeYamlFilterOnRawApi(FILTER1_YAML, rawApi);

        assertEquals("empdiscount.qa-api.tapestry.com", result.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testEndToEnd_Filter1_ProdApiToQaApi() throws Exception {
        RawApi rawApi = buildRawApi("https://fiscalcalendar.prod-api.tapestry.com/",
                makeHeaders("host", "fiscalcalendar.prod-api.tapestry.com"));

        RawApi result = executeYamlFilterOnRawApi(FILTER1_YAML, rawApi);

        assertEquals("fiscalcalendar.qa-api.tapestry.com", result.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testEndToEnd_Filter1_SpiDevToSpiQa() throws Exception {
        RawApi rawApi = buildRawApi("https://membership-admin-auth.spidev-api.tapestry.com/",
                makeHeaders("host", "membership-admin-auth.spidev-api.tapestry.com"));

        RawApi result = executeYamlFilterOnRawApi(FILTER1_YAML, rawApi);

        assertEquals("membership-admin-auth.spiqa-api.tapestry.com", result.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testEndToEnd_Filter2_PrdDotToQaDot() throws Exception {
        RawApi rawApi = buildRawApi("https://api-prd.ods.tapestry.com/",
                makeHeaders("host", "api-prd.ods.tapestry.com"));

        RawApi result = executeYamlFilterOnRawApi(FILTER2_YAML, rawApi);

        assertEquals("api-qa.ods.tapestry.com", result.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testEndToEnd_Filter2_DevDashToQaDash() throws Exception {
        RawApi rawApi = buildRawApi("https://subspace-dev-us-west-2.ods.tapestry.com/",
                makeHeaders("host", "subspace-dev-us-west-2.ods.tapestry.com"));

        RawApi result = executeYamlFilterOnRawApi(FILTER2_YAML, rawApi);

        assertEquals("subspace-qa-us-west-2.ods.tapestry.com", result.fetchReqHeaders().get("host").get(0));
    }
}
