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

    // --- Filter 1: service.(dev|prod|prd)-api.example.com -> service.qa-api.example.com ---

    @Test
    public void testDevApiToQaApi() {
        RawApi rawApi = buildRawApi("https://catalog.dev-api.example.com/api/test",
                makeHeaders("host", "catalog.dev-api.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey(), false);

        assertEquals("catalog.qa-api.example.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testProdApiToQaApi() {
        RawApi rawApi = buildRawApi("https://inventory.prod-api.example.com/",
                makeHeaders("host", "inventory.prod-api.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey(), false);

        assertEquals("inventory.qa-api.example.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testPrdApiToQaApi() {
        RawApi rawApi = buildRawApi("https://orders.prd-api.example.com/",
                makeHeaders("host", "orders.prd-api.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey(), false);

        assertEquals("orders.qa-api.example.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testSpiDevApiToSpiQaApi() {
        RawApi rawApi = buildRawApi("https://auth.spidev-api.example.com/",
                makeHeaders("host", "auth.spidev-api.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey(), false);

        assertEquals("auth.spiqa-api.example.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    // --- Filter 2: x-(dev|prd|prod)(.|-)  ->  x-qa(.|-)  ---

    @Test
    public void testPrdSuffixToQa() {
        RawApi rawApi = buildRawApi("https://api-prd.internal.example.com/api/endpoint",
                makeHeaders("host", "api-prd.internal.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("-(dev|prd|prod)([.-])", "-qa$2"),
                new HashMap<>(), dummyApiInfoKey(), false);

        assertEquals("api-qa.internal.example.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testDevSuffixToQa() {
        RawApi rawApi = buildRawApi("https://app.search-dev.example.com/api/endpoint",
                makeHeaders("host", "app.search-dev.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("-(dev|prd|prod)([.-])", "-qa$2"),
                new HashMap<>(), dummyApiInfoKey(), false);

        assertEquals("app.search-qa.example.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testDevMiddleToQa() {
        RawApi rawApi = buildRawApi("https://worker-dev-us-west-2.internal.example.com/",
                makeHeaders("host", "worker-dev-us-west-2.internal.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("-(dev|prd|prod)([.-])", "-qa$2"),
                new HashMap<>(), dummyApiInfoKey(), false);

        assertEquals("worker-qa-us-west-2.internal.example.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    // --- Edge cases ---

    @Test
    public void testQaHostUnchanged() {
        RawApi rawApi = buildRawApi("https://catalog.qa-api.example.com/",
                makeHeaders("host", "catalog.qa-api.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey(), false);

        assertEquals("catalog.qa-api.example.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testMissingHeaderReturnsMessage() {
        RawApi rawApi = buildRawApi("https://example.com/",
                makeHeaders("content-type", "application/json"));

        ExecutorSingleOperationResp resp = Utils.modifySampleDataUtil("modify_header", rawApi, "host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey(), false);

        assertTrue(resp.getErrMsg().contains("header key not present"));
    }

    @Test
    public void testStaticModifyHeaderStillWorks() {
        RawApi rawApi = buildRawApi("https://old.example.com/",
                makeHeaders("host", "old.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "host", "new.example.com",
                new HashMap<>(), dummyApiInfoKey(), false);

        assertEquals("new.example.com", rawApi.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testRegexReplaceOnCustomHeader() {
        RawApi rawApi = buildRawApi("https://example.com/",
                makeHeaders("x-forwarded-host", "service.dev-api.example.com"));

        Utils.modifySampleDataUtil("modify_header", rawApi, "x-forwarded-host",
                buildRegexReplace("(dev|prod|prd)-api\\.", "qa-api."),
                new HashMap<>(), dummyApiInfoKey(), false);

        assertEquals("service.qa-api.example.com", rawApi.fetchReqHeaders().get("x-forwarded-host").get(0));
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
            "        regex: \"\\\\.(spi)?(dev|prod|prd)-api\\\\.example\\\\.com$\"\n" +
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
        RawApi rawApi = buildRawApi("https://catalog.dev-api.example.com/",
                makeHeaders("host", "catalog.dev-api.example.com"));

        RawApi result = executeYamlFilterOnRawApi(FILTER1_YAML, rawApi);

        assertEquals("catalog.qa-api.example.com", result.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testEndToEnd_Filter1_ProdApiToQaApi() throws Exception {
        RawApi rawApi = buildRawApi("https://inventory.prod-api.example.com/",
                makeHeaders("host", "inventory.prod-api.example.com"));

        RawApi result = executeYamlFilterOnRawApi(FILTER1_YAML, rawApi);

        assertEquals("inventory.qa-api.example.com", result.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testEndToEnd_Filter1_SpiDevToSpiQa() throws Exception {
        RawApi rawApi = buildRawApi("https://auth.spidev-api.example.com/",
                makeHeaders("host", "auth.spidev-api.example.com"));

        RawApi result = executeYamlFilterOnRawApi(FILTER1_YAML, rawApi);

        assertEquals("auth.spiqa-api.example.com", result.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testEndToEnd_Filter2_PrdDotToQaDot() throws Exception {
        RawApi rawApi = buildRawApi("https://api-prd.internal.example.com/",
                makeHeaders("host", "api-prd.internal.example.com"));

        RawApi result = executeYamlFilterOnRawApi(FILTER2_YAML, rawApi);

        assertEquals("api-qa.internal.example.com", result.fetchReqHeaders().get("host").get(0));
    }

    @Test
    public void testEndToEnd_Filter2_DevDashToQaDash() throws Exception {
        RawApi rawApi = buildRawApi("https://worker-dev-us-west-2.internal.example.com/",
                makeHeaders("host", "worker-dev-us-west-2.internal.example.com"));

        RawApi result = executeYamlFilterOnRawApi(FILTER2_YAML, rawApi);

        assertEquals("worker-qa-us-west-2.internal.example.com", result.fetchReqHeaders().get("host").get(0));
    }
}
