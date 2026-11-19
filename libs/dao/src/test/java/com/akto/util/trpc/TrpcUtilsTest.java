package com.akto.util.trpc;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrpcUtilsTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int COLLECTION = 627031169;

    private static Map<String, List<String>> multiValued(Map<String, String> headers) {
        Map<String, List<String>> result = new HashMap<>();
        headers.forEach((name, value) -> result.put(name, new ArrayList<>(Arrays.asList(value))));
        return result;
    }

    private static HttpResponseParams call(String method, String path, String requestPayload, String responsePayload,
                                           int status, Map<String, String> extraRequestHeaders) throws Exception {
        Map<String, String> reqHeaders = new HashMap<>();
        reqHeaders.put("host", "app.example.com");
        reqHeaders.put("cookie", "session=abc");
        reqHeaders.put("content-length", String.valueOf(requestPayload.length()));
        if (method.equals("POST")) {
            reqHeaders.put("content-type", "application/json");
        }
        reqHeaders.putAll(extraRequestHeaders);
        Map<String, String> respHeaders = new HashMap<>();
        respHeaders.put("content-type", "application/json");
        respHeaders.put("content-encoding", "gzip");
        respHeaders.put("content-length", "123");

        ObjectNode orig = mapper.createObjectNode();
        orig.put("method", method);
        orig.put("path", path);
        orig.put("type", "HTTP/1.1");
        orig.put("requestHeaders", mapper.writeValueAsString(reqHeaders));
        orig.put("requestPayload", requestPayload);
        orig.put("responseHeaders", mapper.writeValueAsString(respHeaders));
        orig.put("responsePayload", responsePayload);
        orig.put("statusCode", String.valueOf(status));
        orig.put("status", "OK");
        orig.put("ip", "1.2.3.4");
        orig.put("time", "1700000000");
        orig.put("akto_vxlan_id", COLLECTION);
        orig.put("source", "MIRRORING");

        HttpRequestParams request = new HttpRequestParams(method, path, "HTTP/1.1", multiValued(reqHeaders), requestPayload, COLLECTION);
        return new HttpResponseParams("HTTP/1.1", status, "OK", multiValued(respHeaders), responsePayload, request, 1700000000,
                "1728622642", false, HttpResponseParams.Source.MIRRORING, orig.toString(), "1.2.3.4");
    }

    private static HttpResponseParams get(String path, String responsePayload) throws Exception {
        return call("GET", path, "", responsePayload, 200, new HashMap<>());
    }

    private static HttpResponseParams post(String path, String body, String responsePayload) throws Exception {
        return call("POST", path, body, responsePayload, 200, new HashMap<>());
    }

    private static JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    private static JsonNode sample(HttpResponseParams p) throws Exception {
        return mapper.readTree(p.getOrig());
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private static void assertJsonEquals(String expected, String actual) throws Exception {
        assertEquals(json(expected), json(actual));
    }

    @Test
    public void batchGetIsSplitIntoOneRestCallPerProcedure() throws Exception {
        String input = "{\"1\":{\"view\":\"\",\"limit\":50,\"page\":null,\"sorts\":[{\"key\":\"lastActivityAt\",\"order\":\"desc\"}]}}";
        String path = "/trpc/planet-express/matter.tableView.list,matter.list,user.getCurrentUser?batch=1&input=" + enc(input);
        String response = "[{\"result\":{\"data\":{\"views\":[1,2]}}},{\"result\":{\"data\":{\"items\":[{\"id\":\"m1\"}]}}},{\"result\":{\"data\":{\"id\":\"u1\",\"email\":\"a@b.com\"}}}]";

        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(get(path, response));

        assertEquals(3, ops.size());
        assertEquals("/trpc/STRING/matter.tableView.list", ops.get(0).getRequestParams().getURL());
        assertEquals("/trpc/STRING/matter.list", ops.get(1).getRequestParams().getURL());
        assertEquals("/trpc/STRING/user.getCurrentUser", ops.get(2).getRequestParams().getURL());
        for (HttpResponseParams op : ops) {
            assertEquals("GET", op.getRequestParams().getMethod());
            assertEquals(COLLECTION, op.getRequestParams().getApiCollectionId());
            assertEquals(200, op.getStatusCode());
        }
        // only call 1 had an input; its rest view is the decoded input, the others are empty
        assertJsonEquals("{}", ops.get(0).getRequestParams().getPayload());
        assertJsonEquals("{\"view\":\"\",\"limit\":50,\"page\":null,\"sorts\":[{\"key\":\"lastActivityAt\",\"order\":\"desc\"}]}", ops.get(1).getRequestParams().getPayload());
        assertJsonEquals("{}", ops.get(2).getRequestParams().getPayload());
        assertJsonEquals("{\"views\":[1,2]}", ops.get(0).getPayload());
        assertJsonEquals("{\"items\":[{\"id\":\"m1\"}]}", ops.get(1).getPayload());
        assertJsonEquals("{\"id\":\"u1\",\"email\":\"a@b.com\"}", ops.get(2).getPayload());
    }

    @Test
    public void storedSampleIsAReplayableSingleProcedureCall() throws Exception {
        String path = "/_/sow/trpc/planet-express/matter.tableView.list,matter.list?batch=1&input=" + enc("{\"1\":{\"limit\":50}}");
        String response = "[{\"result\":{\"data\":{\"views\":[]}}},{\"result\":{\"data\":{\"items\":[]}}}]";

        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(get(path, response));
        assertEquals(2, ops.size());

        // the real tenant is kept, the call is not batched and has no input
        JsonNode first = sample(ops.get(0));
        assertEquals("/_/sow/trpc/planet-express/matter.tableView.list", first.get("path").asText());
        assertEquals("", first.get("requestPayload").asText());
        assertEquals("{\"result\":{\"data\":{\"views\":[]}}}", first.get("responsePayload").asText());

        // the input of a GET is in the payload, readable and editable, not a percent encoded query param
        JsonNode second = sample(ops.get(1));
        assertEquals("/_/sow/trpc/planet-express/matter.list", second.get("path").asText());
        assertEquals("{\"limit\":50}", second.get("requestPayload").asText());

        // everything else of the kafka message survives, stale length / encoding headers do not
        assertEquals("1.2.3.4", second.get("ip").asText());
        JsonNode requestHeaders = json(second.get("requestHeaders").asText());
        assertEquals("session=abc", requestHeaders.get("cookie").asText());
        assertFalse(requestHeaders.has("content-length"));
        JsonNode responseHeaders = json(second.get("responseHeaders").asText());
        assertFalse(responseHeaders.has("content-length"));
        assertFalse(responseHeaders.has("content-encoding"));
        assertTrue(responseHeaders.has("content-type"));
    }

    @Test
    public void everyOpGetsItsOwnCompleteClone() throws Exception {
        HttpResponseParams original = get("/trpc/acme/a.one,b.two?batch=1&input=" + enc("{}"),
                "[{\"result\":{\"data\":1}},{\"result\":{\"data\":2}}]");
        original.setTags("{\"k8s\":\"x\"}");

        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(original);
        assertEquals(2, ops.size());
        for (HttpResponseParams op : ops) {
            assertEquals("{\"k8s\":\"x\"}", op.getTags());
            assertEquals("1728622642", op.getAccountId());
        }
        ops.get(0).getRequestParams().getHeaders().put("x-test", new ArrayList<>(Arrays.asList("1")));
        assertFalse(ops.get(1).getRequestParams().getHeaders().containsKey("x-test"));
        assertFalse(original.getRequestParams().getHeaders().containsKey("x-test"));
    }

    @Test
    public void primitiveInputAndDataAreNamed() throws Exception {
        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(
                get("/trpc/acme/matter.lopsId?batch=1&input=" + enc("{\"0\":\"abc\"}"), "[{\"result\":{\"data\":\"LOPS-1\"}}]"));
        assertEquals(1, ops.size());
        assertJsonEquals("{\"input\":\"abc\"}", ops.get(0).getRequestParams().getPayload());
        assertJsonEquals("{\"data\":\"LOPS-1\"}", ops.get(0).getPayload());

        // a raw string body of a non batched call, the sample keeps it as it was sent
        List<HttpResponseParams> raw = TrpcUtils.parseTrpcResponseParam(post("/trpc/impersonate.start", "\"019dd43a\"", "{\"result\":{}}"));
        assertJsonEquals("{\"input\":\"019dd43a\"}", raw.get(0).getRequestParams().getPayload());
        assertEquals("\"019dd43a\"", sample(raw.get(0)).get("requestPayload").asText());
    }

    @Test
    public void nonBatchedCallsKeepTheirInputVerbatim() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"provider\":\"google\"}";
        List<HttpResponseParams> posted = TrpcUtils.parseTrpcResponseParam(
                post("/trpc/login", body, "{\"result\":{\"data\":\"https://idp.example.com/authorize\"}}"));
        assertEquals(1, posted.size());
        // no tenant segment: nothing is templated
        assertEquals("/trpc/login", posted.get(0).getRequestParams().getURL());
        assertJsonEquals(body, posted.get(0).getRequestParams().getPayload());
        assertJsonEquals("{\"data\":\"https://idp.example.com/authorize\"}", posted.get(0).getPayload());
        assertEquals(body, sample(posted.get(0)).get("requestPayload").asText());

        List<HttpResponseParams> fetched = TrpcUtils.parseTrpcResponseParam(
                get("/_/sow/trpc/info?input=" + enc(body), "{\"result\":{\"data\":{\"loginProvider\":\"WORKOS\"}}}"));
        assertEquals("/_/sow/trpc/info", fetched.get(0).getRequestParams().getURL());
        assertEquals("/_/sow/trpc/info", sample(fetched.get(0)).get("path").asText());
        assertEquals(body, sample(fetched.get(0)).get("requestPayload").asText());
    }

    @Test
    public void errorItemGetsItsOwnHttpStatus() throws Exception {
        String response = "[{\"result\":{\"data\":{\"ok\":true}}},{\"error\":{\"message\":\"nope\",\"code\":-32004,\"data\":{\"code\":\"NOT_FOUND\",\"httpStatus\":404,\"path\":\"b.two\"}}}]";
        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(call("GET",
                "/trpc/acme/a.one,b.two?batch=1&input=" + enc("{}"), "", response, 207, new HashMap<>()));

        assertEquals(2, ops.size());
        assertEquals(200, ops.get(0).getStatusCode());
        assertEquals(404, ops.get(1).getStatusCode());
        assertEquals("404", sample(ops.get(1)).get("statusCode").asText());
        assertTrue(json(sample(ops.get(1)).get("responsePayload").asText()).has("error"));
    }

    @Test
    public void emptyResponseStillSplitsOnlyWithAStrongUrlSignal() throws Exception {
        // 7% of the real samples have a `{}` response on a 207/200 call, the request side is still worth recording
        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(call("GET",
                "/trpc/acme/a.one,b.two?batch=1&input=" + enc("{\"0\":{\"x\":1}}"), "", "{}", 207, new HashMap<>()));

        assertEquals(2, ops.size());
        assertEquals(200, ops.get(0).getStatusCode());
        assertJsonEquals("{\"x\":1}", ops.get(0).getRequestParams().getPayload());
        assertEquals("{}", sample(ops.get(0)).get("responsePayload").asText());

        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/trpc/acme/a.one", "{}")).isEmpty());
    }

    @Test
    public void jsonlStreamingChunkKeepsTheRequestSide() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("trpc-accept", "application/jsonl");
        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(call("GET",
                "/trpc/acme/task.get?batch=1&input=" + enc("{\"0\":{\"id\":\"t1\"}}"), "", "{\"0\":[[0],[null,0,0]]}", 200, headers));

        assertEquals(1, ops.size());
        assertJsonEquals("{\"id\":\"t1\"}", ops.get(0).getRequestParams().getPayload());
        assertJsonEquals("{}", ops.get(0).getPayload());
        // the replayable sample asks for plain json
        assertFalse(json(sample(ops.get(0)).get("requestHeaders").asText()).has("trpc-accept"));
    }

    @Test
    public void superjsonEnvelopeIsUnwrappedInTheRestViewOnly() throws Exception {
        String input = "{\"0\":{\"json\":{\"id\":\"m1\"},\"meta\":{\"values\":{\"d\":[\"Date\"]}}}}";
        String response = "[{\"result\":{\"data\":{\"json\":{\"id\":\"m1\",\"n\":1},\"meta\":{\"values\":{}}}}}]";
        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(
                post("/trpc/acme/matter.update?batch=1", input, response));

        assertEquals(1, ops.size());
        assertEquals("POST", ops.get(0).getRequestParams().getMethod());
        assertJsonEquals("{\"id\":\"m1\"}", ops.get(0).getRequestParams().getPayload());
        assertJsonEquals("{\"id\":\"m1\",\"n\":1}", ops.get(0).getPayload());
        // the sample is what went over the wire, wrapper included
        assertEquals("{\"json\":{\"id\":\"m1\"},\"meta\":{\"values\":{\"d\":[\"Date\"]}}}", sample(ops.get(0)).get("requestPayload").asText());
        assertEquals("/trpc/acme/matter.update", sample(ops.get(0)).get("path").asText());
    }

    @Test
    public void ordinaryTrafficIsLeftAlone() throws Exception {
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/api/users/me", "{\"id\":1}")).isEmpty());
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/v2/cases/123/activity?batch=1", "{\"id\":1}")).isEmpty());
        // the `trpc` path segment is what marks a mount, a batch looking url without it is not detected
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/api/rpc/a.one,b.two?batch=1&input=" + enc("{}"),
                "[{\"result\":{\"data\":1}},{\"result\":{\"data\":2}}]")).isEmpty());
        // the word in another position is not a mount: no segment, or nothing after it
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/docs/trpc-guide", "{\"result\":{\"data\":1}}")).isEmpty());
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/api/trpc", "{\"result\":{\"data\":1}}")).isEmpty());
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/trpc/docs", "<html></html>")).isEmpty());
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/trpc/acme/a.one", "{\"foo\":1}")).isEmpty());
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/trpc/acme/a.one", "{\"jsonrpc\":\"2.0\",\"result\":1,\"id\":1}")).isEmpty());
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/trpc/acme/a.one,b.two?batch=1", "[{\"result\":{\"data\":1}}]")).isEmpty());
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/trpc/acme/a.one,b.two", "{\"result\":{\"data\":1}}")).isEmpty());
        // batch input must be keyed by call index
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("/trpc/acme/a.one?batch=1&input=" + enc("{\"email\":\"x\"}"), "[{\"result\":{\"data\":1}}]")).isEmpty());
        // other verbs, form bodies
        assertTrue(TrpcUtils.parseTrpcResponseParam(call("PUT", "/trpc/acme/a.one", "{}", "{\"result\":{}}", 200, new HashMap<>())).isEmpty());
        Map<String, String> form = new HashMap<>();
        form.put("content-type", "multipart/form-data; boundary=x");
        assertTrue(TrpcUtils.parseTrpcResponseParam(call("POST", "/trpc/acme/a.one", "--x", "{\"result\":{}}", 200, form)).isEmpty());
        assertTrue(TrpcUtils.parseTrpcResponseParam(null).isEmpty());
        // a call that came without its original message cannot get a sample, it stays plain REST
        HttpResponseParams withoutOriginalMessage = get("/trpc/acme/a.one", "{\"result\":{\"data\":1}}");
        withoutOriginalMessage.setOrig(null);
        assertTrue(TrpcUtils.parseTrpcResponseParam(withoutOriginalMessage).isEmpty());
    }

    @Test
    public void absoluteUrlsKeepTheirOriginOnTheProducedUrls() throws Exception {
        // HAR / Postman imports carry the whole url
        String input = "{\"0\":{\"json\":{\"id\":\"123\"}}}";
        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(get(
                "https://api.example.com/trpc/acme/a.one,b.two?batch=1&input=" + enc(input),
                "[{\"result\":{\"data\":1}},{\"result\":{\"data\":2}}]"));

        assertEquals(2, ops.size());
        assertEquals("https://api.example.com/trpc/STRING/a.one", ops.get(0).getRequestParams().getURL());
        assertEquals("https://api.example.com/trpc/STRING/b.two", ops.get(1).getRequestParams().getURL());
        assertEquals("https://api.example.com/trpc/acme/a.one", sample(ops.get(0)).get("path").asText());
        assertEquals("{\"json\":{\"id\":\"123\"}}", sample(ops.get(0)).get("requestPayload").asText());
        // a host that merely contains the word is not a mount
        assertTrue(TrpcUtils.parseTrpcResponseParam(get("https://trpc.example.com/api/users", "{\"result\":{\"data\":1}}")).isEmpty());
    }

    private static OriginalHttpRequest request(String sampleMessage) {
        OriginalHttpRequest request = new OriginalHttpRequest();
        request.buildFromSampleMessage(sampleMessage);
        return request;
    }

    @Test
    public void storedGetInputIsEditableAsTheBodyAndMovesBackIntoTheQueryWhenSent() throws Exception {
        String input = "{\"limit\":50,\"filters\":[{\"key\":\"status\",\"value\":[\"active\"]}]}";
        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(
                get("/trpc/acme/matter.list,user.get?batch=1&input=" + enc("{\"0\":" + input + "}") + "&foo=bar",
                        "[{\"result\":{\"data\":{}}},{\"result\":{\"data\":{}}}]"));
        OriginalHttpRequest request = request(ops.get(0).getOrig());

        // what the test operations and the dashboard work on
        assertEquals(input, request.getBody());
        assertEquals("foo=bar", request.getQueryParams());
        assertEquals("/trpc/acme/matter.list", request.getUrl());

        // a body operation changes the input, sending puts it on the wire
        request.setBody("{\"limit\":999}");
        assertTrue(TrpcUtils.moveBodyToInputQuery(request));
        assertEquals("", request.getBody());
        assertEquals("input=" + enc("{\"limit\":999}") + "&foo=bar", request.getQueryParams());
        assertFalse("a second move is a no-op", TrpcUtils.moveBodyToInputQuery(request));
    }

    @Test
    public void unmodifiedRoundTripIsLossless() throws Exception {
        String input = "{\"q\":\"a&b=c+d%20é #?\",\"n\":[1,2.50,null],\"empty\":{}}";
        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(
                get("/trpc/acme/search.run?batch=1&input=" + enc("{\"0\":" + input + "}"), "[{\"result\":{\"data\":{}}}]"));
        OriginalHttpRequest request = request(ops.get(0).getOrig());

        assertEquals(input, request.getBody());
        assertTrue(TrpcUtils.moveBodyToInputQuery(request));
        assertEquals("input=" + enc(input), request.getQueryParams());
        assertEquals(input, URLDecoder.decode(request.getQueryParams().substring("input=".length()), "UTF-8"));
    }

    @Test
    public void aGetWithoutInputHasNoPayloadAndSendsNoInput() throws Exception {
        List<HttpResponseParams> ops = TrpcUtils.parseTrpcResponseParam(get("/trpc/acme/user.list", "{\"result\":{\"data\":[]}}"));
        OriginalHttpRequest request = request(ops.get(0).getOrig());

        assertEquals("", request.getBody());
        assertFalse(TrpcUtils.moveBodyToInputQuery(request));
        assertEquals("", request.getQueryParams() == null ? "" : request.getQueryParams());
    }

    @Test
    public void bodyOfEveryOtherRequestIsLeftAlone() throws Exception {
        OriginalHttpRequest rest = new OriginalHttpRequest("/api/users", "a=1", "GET", "{\"x\":1}", new HashMap<>(), "HTTP/1.1");
        assertFalse(TrpcUtils.moveBodyToInputQuery(rest));
        assertEquals("{\"x\":1}", rest.getBody());
        assertEquals("a=1", rest.getQueryParams());

        OriginalHttpRequest post = new OriginalHttpRequest("/trpc/acme/a.one", "", "POST", "{\"x\":1}", new HashMap<>(), "HTTP/1.1");
        assertFalse(TrpcUtils.moveBodyToInputQuery(post));
        assertEquals("{\"x\":1}", post.getBody());

        OriginalHttpRequest batch = new OriginalHttpRequest("/trpc/acme/a.one,b.two", "batch=1", "GET", "{\"0\":{}}", new HashMap<>(), "HTTP/1.1");
        assertFalse(TrpcUtils.moveBodyToInputQuery(batch));

        // the query already carries the input
        OriginalHttpRequest hasInput = new OriginalHttpRequest("/trpc/acme/a.one", "input=%7B%7D", "GET", "{\"x\":1}", new HashMap<>(), "HTTP/1.1");
        assertFalse(TrpcUtils.moveBodyToInputQuery(hasInput));
        assertEquals("input=%7B%7D", hasInput.getQueryParams());
        assertEquals("{\"x\":1}", hasInput.getBody());

        OriginalHttpRequest empty = new OriginalHttpRequest("/trpc/acme/a.one", "", "GET", null, new HashMap<>(), "HTTP/1.1");
        assertFalse(TrpcUtils.moveBodyToInputQuery(empty));
        assertFalse(TrpcUtils.moveBodyToInputQuery(null));
    }

    @Test
    public void movesWorkOnAnAbsoluteUrl() throws Exception {
        // a modify_url test operation or the executor may have made the url absolute by the time the request is sent
        OriginalHttpRequest request = new OriginalHttpRequest("https://app.example.com/trpc/acme/a.one", "", "GET", "{\"x\":2}", new HashMap<>(), "HTTP/1.1");

        assertTrue(TrpcUtils.moveBodyToInputQuery(request));
        assertEquals("input=" + enc("{\"x\":2}"), request.getQueryParams());
        assertEquals("https://app.example.com/trpc/acme/a.one", request.getUrl());
    }
}
