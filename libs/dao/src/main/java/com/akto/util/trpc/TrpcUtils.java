package com.akto.util.trpc;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.util.HttpRequestResponseUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * tRPC support. Akto only models REST, so a tRPC call is converted to one REST-shaped call per procedure.
 * Wire format (https://trpc.io/docs/rpc): GET {mount}/{proc}[,{proc}...]?[batch=1&]input=<urlencoded JSON> and POST
 * {mount}/{proc}[,{proc}...][?batch=1] with a JSON body (batch input is keyed by call index); the response is
 * {"result":{"data":..}} or {"error":{..,"data":{"httpStatus":..}}}, an array in call order for a batch. Tenant segments may
 * sit between the mount and the procedure, and an absolute url (HAR / Postman import) keeps its origin on the produced urls.
 * Detection: a `trpc` path segment before a procedure-shaped last segment.
 * Ingestion ({@link #parseTrpcResponseParam}): one endpoint {mount}/STRING/.../{proc} per procedure, a REST view of the
 * decoded input and the response data, and a stored sample that is a standalone non batched call whose GET input stays
 * in the request payload (readable and editable). Testing ({@link #moveBodyToInputQuery}): a body on a tRPC GET is its
 * input and is moved into the `input` query param right before the request is sent.
 */
public final class TrpcUtils {

    public static final String TENANT_PLACEHOLDER = "STRING";

    private static final String TRPC_MOUNT_SEGMENT = "trpc";
    private static final String BATCH_QUERY_PARAM = "batch";
    private static final String INPUT_QUERY_PARAM = "input";
    private static final int MAX_BATCH_SIZE = 500;
    private static final String UTF_8 = "UTF-8";

    // exact decimals so that re-serializing an input does not change a number such as 2.50
    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    private static final Pattern PROCEDURE_NAME_PATTERN = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$-]*(\\.[A-Za-z_$][A-Za-z0-9_$-]*)*$");
    private static final Set<String> SUPERJSON_ENVELOPE_KEYS = new HashSet<>(Arrays.asList("json", "meta"));
    // headers that no longer describe the message once its body has been rewritten
    private static final Set<String> STALE_REQUEST_HEADERS = new HashSet<>(Collections.singletonList("content-length"));
    private static final Set<String> STALE_RESPONSE_HEADERS = new HashSet<>(Arrays.asList("content-length", "content-encoding", "transfer-encoding"));

    private TrpcUtils() {
    }

    /** Parsed shape of a tRPC url: {mount segments}/{tenant segments}/{procedure[,procedure...]}?batch=1&input=... */
    static final class TrpcUrl {
        List<String> mountSegments = new ArrayList<>();     // path segments up to and including the `trpc` mount
        List<String> tenantSegments = new ArrayList<>();    // path segments between the mount and the procedure
        List<String> procedures = new ArrayList<>();
        String origin = "";                                 // scheme://host of an absolute url, empty for a request target
        boolean isBatch;
        String inputJson;                                   // decoded `input` query value, null when absent
        List<String> otherQueryPairs = new ArrayList<>();   // raw k=v pairs other than batch and input
    }

    private static final class ProcedureResponse {
        final JsonNode envelope;    // the {"result":..} / {"error":..} envelope of this procedure, null when unknown
        final int statusCode;

        ProcedureResponse(JsonNode envelope, int statusCode) {
            this.envelope = envelope;
            this.statusCode = statusCode;
        }
    }

    static TrpcUrl parseTrpcUrl(String url) {
        // every ingested request lands here: like GraphQL's `/graphql` check, a plain contains turns almost all traffic down
        if (url == null || !url.contains(TRPC_MOUNT_SEGMENT)) {
            return null;
        }

        String origin = "";
        int schemeEnd = url.indexOf("://");
        if (schemeEnd > 0 && schemeEnd <= 5) {
            int originEnd = url.indexOf('/', schemeEnd + 3);
            origin = originEnd < 0 ? url : url.substring(0, originEnd);
            url = url.substring(origin.length());
        }

        int queryStart = url.indexOf('?');
        String path = queryStart < 0 ? url : url.substring(0, queryStart);
        String query = queryStart < 0 ? "" : url.substring(queryStart + 1);

        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }

        // the procedure is the last segment, the `trpc` mount is the closest segment before it
        int procedureSegmentIndex = segments.size() - 1;
        int mountSegmentIndex = procedureSegmentIndex - 1;
        while (mountSegmentIndex >= 0 && !TRPC_MOUNT_SEGMENT.equals(segments.get(mountSegmentIndex))) {
            mountSegmentIndex--;
        }
        if (mountSegmentIndex < 0) {
            return null;
        }

        List<String> procedures = parseProcedureNames(segments.get(procedureSegmentIndex));
        if (procedures == null) {
            return null;
        }

        TrpcUrl trpcUrl = new TrpcUrl();
        if (!readQueryParams(query, trpcUrl)) {
            return null;
        }
        if (procedures.size() > 1 && !trpcUrl.isBatch) {
            return null;
        }

        trpcUrl.origin = origin;
        trpcUrl.procedures = procedures;
        trpcUrl.mountSegments = new ArrayList<>(segments.subList(0, mountSegmentIndex + 1));
        trpcUrl.tenantSegments = new ArrayList<>(segments.subList(mountSegmentIndex + 1, procedureSegmentIndex));
        return trpcUrl;
    }

    /** The procedure names of the last path segment (several for a batch), null when it is not a procedure list. */
    private static List<String> parseProcedureNames(String lastSegment) {
        List<String> procedureNames = new ArrayList<>();
        for (String procedureName : lastSegment.split(",", -1)) {
            if (!PROCEDURE_NAME_PATTERN.matcher(procedureName).matches()) {
                return null;
            }
            procedureNames.add(procedureName);
        }
        if (procedureNames.isEmpty() || procedureNames.size() > MAX_BATCH_SIZE) {
            return null;
        }
        return procedureNames;
    }

    /** Reads batch, input and the remaining query pairs into the url. Returns false for a malformed percent-encoding. */
    private static boolean readQueryParams(String query, TrpcUrl trpcUrl) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String rawName = separator < 0 ? pair : pair.substring(0, separator);
            String rawValue = separator < 0 ? "" : pair.substring(separator + 1);
            try {
                String name = URLDecoder.decode(rawName, UTF_8);
                if (BATCH_QUERY_PARAM.equals(name)) {
                    trpcUrl.isBatch = !URLDecoder.decode(rawValue, UTF_8).isEmpty();
                } else if (INPUT_QUERY_PARAM.equals(name)) {
                    trpcUrl.inputJson = URLDecoder.decode(rawValue, UTF_8);
                } else {
                    trpcUrl.otherQueryPairs.add(pair);
                }
            } catch (UnsupportedEncodingException | IllegalArgumentException e) {
                return false;
            }
        }
        return true;
    }

    /** One REST call per procedure of the call, or an empty list when the traffic is not (provably) tRPC. */
    public static List<HttpResponseParams> parseTrpcResponseParam(HttpResponseParams responseParams) {
        try {
            return splitIntoProcedureCalls(responseParams);
        } catch (Exception e) {
            // never let a parsing problem break ingestion, the request is then handled as plain REST
            return Collections.emptyList();
        }
    }

    private static List<HttpResponseParams> splitIntoProcedureCalls(HttpResponseParams responseParams) {
        if (responseParams == null || responseParams.getRequestParams() == null) {
            return Collections.emptyList();
        }
        HttpRequestParams request = responseParams.getRequestParams();
        String method = request.getMethod();
        boolean isGet = "GET".equalsIgnoreCase(method);
        boolean isPost = "POST".equalsIgnoreCase(method);
        if (!isGet && !isPost) {
            return Collections.emptyList();
        }

        TrpcUrl trpcUrl = parseTrpcUrl(request.getURL());
        if (trpcUrl == null) {
            return Collections.emptyList();
        }
        if (isPost && !isJsonContentTypeOrAbsent(request.getHeaders())) {
            // tRPC v11 also accepts FormData / octet-stream inputs, those are not decoded
            return Collections.emptyList();
        }

        int procedureCount = trpcUrl.procedures.size();
        JsonNode parsedInput = null;    // the whole input: keyed by call index for a batch, the input itself otherwise
        String rawRequestBody = request.getPayload();
        if (isGet) {
            if (trpcUrl.inputJson != null && !trpcUrl.inputJson.trim().isEmpty()) {
                parsedInput = tryParseJson(trpcUrl.inputJson);
                if (parsedInput == null || parsedInput.isMissingNode()) {
                    return Collections.emptyList();
                }
            }
        } else if (rawRequestBody != null && !rawRequestBody.trim().isEmpty()) {
            parsedInput = tryParseJson(rawRequestBody);
            if (parsedInput == null || parsedInput.isMissingNode()) {
                return Collections.emptyList();
            }
        }
        // a batch input is keyed by call index; clients send `{}` when none of the calls has an input
        if (trpcUrl.isBatch && parsedInput != null
                && !(parsedInput.isObject() && (parsedInput.size() == 0 || isKeyedByCallIndex(parsedInput)))) {
            return Collections.emptyList();
        }

        ProcedureResponse[] procedureResponses = matchProcedureResponses(responseParams, trpcUrl, parsedInput != null);
        if (procedureResponses == null) {
            return Collections.emptyList();
        }

        List<HttpResponseParams> procedureCalls = new ArrayList<>(procedureCount);
        for (int callIndex = 0; callIndex < procedureCount; callIndex++) {
            JsonNode rawInput = trpcUrl.isBatch
                    ? (parsedInput == null ? null : parsedInput.get(String.valueOf(callIndex)))
                    : parsedInput;
            procedureCalls.add(buildProcedureCall(responseParams, trpcUrl, callIndex, rawInput, rawRequestBody,
                    procedureResponses[callIndex], isGet));
        }
        return procedureCalls;
    }

    /** The per procedure envelopes of the response, null when it does not look like tRPC; an empty response needs a strong url signal. */
    private static ProcedureResponse[] matchProcedureResponses(HttpResponseParams responseParams, TrpcUrl trpcUrl,
                                                               boolean hasInputPayload) {
        int procedureCount = trpcUrl.procedures.size();
        int originalStatusCode = responseParams.getStatusCode();
        // 207 is the status of the whole batch, a procedure whose result is unknown counts as successful
        int fallbackStatusCode = originalStatusCode == 207 ? 200 : originalStatusCode;
        ProcedureResponse[] procedureResponses = new ProcedureResponse[procedureCount];

        JsonNode responseJson = tryParseJson(responseParams.getPayload());
        if (responseJson == null) {
            return null; // not json (html, sse, ...)
        }

        boolean responseUnavailable = responseJson.isMissingNode() || (responseJson.isObject() && responseJson.size() == 0)
                || (responseJson.isObject() && isKeyedByCallIndex(responseJson)
                        && requestsJsonlStream(responseParams.getRequestParams().getHeaders()));
        if (responseUnavailable) {
            boolean hasStrongUrlSignal = trpcUrl.isBatch || hasInputPayload;
            if (!hasStrongUrlSignal) {
                return null;
            }
            for (int callIndex = 0; callIndex < procedureCount; callIndex++) {
                procedureResponses[callIndex] = new ProcedureResponse(null, fallbackStatusCode);
            }
            return procedureResponses;
        }

        if (trpcUrl.isBatch) {
            if (!responseJson.isArray() || responseJson.size() != procedureCount) {
                return null;
            }
            for (int callIndex = 0; callIndex < procedureCount; callIndex++) {
                procedureResponses[callIndex] = parseResponseEnvelope(responseJson.get(callIndex));
                if (procedureResponses[callIndex] == null) {
                    return null;
                }
            }
        } else {
            procedureResponses[0] = parseResponseEnvelope(responseJson);
            if (procedureResponses[0] == null) {
                return null;
            }
        }
        return procedureResponses;
    }

    /** An envelope has exactly one of `result` / `error` and is not a JSON-RPC message. */
    private static ProcedureResponse parseResponseEnvelope(JsonNode envelopeNode) {
        if (envelopeNode == null || !envelopeNode.isObject() || envelopeNode.has("jsonrpc")) {
            return null;
        }
        boolean hasResult = envelopeNode.has("result");
        boolean hasError = envelopeNode.has("error");
        if (hasResult == hasError) {
            return null;
        }
        if (hasResult) {
            return new ProcedureResponse(envelopeNode, 200);
        }
        JsonNode error = unwrapSuperjson(envelopeNode.get("error"));
        int httpStatus = 500;
        if (error != null && error.isObject()) {
            JsonNode errorData = error.get("data");
            if (errorData != null && errorData.isObject() && errorData.path("httpStatus").canConvertToInt()) {
                httpStatus = errorData.path("httpStatus").asInt();
            }
        }
        return new ProcedureResponse(envelopeNode, httpStatus);
    }

    private static HttpResponseParams buildProcedureCall(HttpResponseParams originalCall, TrpcUrl trpcUrl, int callIndex,
                                                         JsonNode rawInput, String rawRequestBody,
                                                         ProcedureResponse procedureResponse, boolean isGet) {
        HttpRequestParams originalRequest = originalCall.getRequestParams();
        String procedure = trpcUrl.procedures.get(callIndex);

        List<String> restPathSegments = new ArrayList<>(trpcUrl.mountSegments);
        for (int i = 0; i < trpcUrl.tenantSegments.size(); i++) {
            restPathSegments.add(TENANT_PLACEHOLDER);
        }
        restPathSegments.add(procedure);
        String restUrl = trpcUrl.origin + joinPath(restPathSegments);
        if (!trpcUrl.otherQueryPairs.isEmpty()) {
            restUrl += "?" + String.join("&", trpcUrl.otherQueryPairs);
        }

        Map<String, List<String>> requestHeaders = copyHeadersExcept(originalRequest.getHeaders(), STALE_REQUEST_HEADERS);
        Map<String, List<String>> responseHeaders = copyHeadersExcept(originalCall.getHeaders(), STALE_RESPONSE_HEADERS);
        HttpRequestParams restRequest = new HttpRequestParams(originalRequest.method, restUrl, originalRequest.type,
                requestHeaders, toRestRequestPayload(rawInput), originalRequest.getApiCollectionId());

        String sampleMessage = buildSampleMessage(originalCall, trpcUrl, callIndex, rawInput, rawRequestBody, procedureResponse, isGet);

        HttpResponseParams procedureCall = new HttpResponseParams(originalCall.type, procedureResponse.statusCode,
                originalCall.status, responseHeaders, toRestResponsePayload(procedureResponse.envelope), restRequest,
                originalCall.getTime(), originalCall.getAccountId(), originalCall.getIsPending(), originalCall.getSource(),
                sampleMessage, originalCall.getSourceIP(), originalCall.getDestIP(), originalCall.getDirection());
        procedureCall.setTags(originalCall.getTags());
        return procedureCall;
    }

    /** The REST request payload of an input: plain JSON object / array, superjson unwrapped, primitives named `input`. */
    private static String toRestRequestPayload(JsonNode rawInput) {
        JsonNode value = unwrapSuperjson(rawInput);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "{}";
        }
        if (value.isObject() || value.isArray()) {
            return value.toString();
        }
        return "{\"input\":" + value + "}";
    }

    /** The REST response payload of an envelope: the `result.data` payload (or the error), primitives named `data`. */
    private static String toRestResponsePayload(JsonNode responseEnvelope) {
        if (responseEnvelope == null) {
            return "{}";
        }
        if (responseEnvelope.has("error")) {
            return responseEnvelope.toString();
        }
        JsonNode result = responseEnvelope.get("result");
        JsonNode data = result == null ? null : unwrapSuperjson(result.get("data"));
        if (data == null || data.isMissingNode() || data.isNull()) {
            return "{}";
        }
        if (data.isObject() || data.isArray()) {
            return data.toString();
        }
        return "{\"data\":" + data + "}";
    }

    /** The stored sample: a standalone non batched call for one procedure; a GET keeps its input in the request payload. */
    private static String buildSampleMessage(HttpResponseParams originalCall, TrpcUrl trpcUrl, int callIndex, JsonNode rawInput,
                                             String rawRequestBody, ProcedureResponse procedureResponse, boolean isGet) {
        // the original kafka message is always there for ingested traffic; a call without it fails here and stays plain REST
        ObjectNode message = ((ObjectNode) tryParseJson(originalCall.getOrig())).deepCopy();

        List<String> pathSegments = new ArrayList<>(trpcUrl.mountSegments);
        pathSegments.addAll(trpcUrl.tenantSegments);
        pathSegments.add(trpcUrl.procedures.get(callIndex));
        StringBuilder path = new StringBuilder(trpcUrl.origin).append(joinPath(pathSegments));
        if (!trpcUrl.otherQueryPairs.isEmpty()) {
            path.append('?').append(String.join("&", trpcUrl.otherQueryPairs));
        }
        message.put("path", path.toString());

        // keep the input verbatim for a non batched call, a batch item is re-serialized
        String requestPayload = "";
        if (rawInput != null && !rawInput.isMissingNode()) {
            if (trpcUrl.isBatch) {
                requestPayload = rawInput.toString();
            } else {
                requestPayload = isGet ? trpcUrl.inputJson : (rawRequestBody == null ? "" : rawRequestBody.trim());
            }
        }
        message.put("requestPayload", requestPayload);
        message.put("responsePayload", procedureResponse.envelope == null ? "{}" : procedureResponse.envelope.toString());
        message.put("statusCode", String.valueOf(procedureResponse.statusCode));

        // a streamed (jsonl) response cannot be replayed as one json body, so the sample asks for plain json
        String extraRequestHeaderToRemove = requestsJsonlStream(originalCall.getRequestParams().getHeaders()) ? "trpc-accept" : null;
        MessageHeaders.removeHeaders(message, "requestHeaders", STALE_REQUEST_HEADERS, extraRequestHeaderToRemove);
        MessageHeaders.removeHeaders(message, "responseHeaders", STALE_RESPONSE_HEADERS, null);
        return message.toString();
    }

    private static final class MessageHeaders {
        static void removeHeaders(ObjectNode message, String headersField, Set<String> namesToRemove, String extraNameToRemove) {
            JsonNode headersEntry = message.get(headersField);
            JsonNode headers = headersEntry == null ? null : (headersEntry.isTextual() ? tryParseJson(headersEntry.asText()) : headersEntry);
            if (headers == null || !headers.isObject()) {
                return;
            }
            ObjectNode headersCopy = ((ObjectNode) headers).deepCopy();
            List<String> headerNamesToRemove = new ArrayList<>();
            Iterator<String> headerNames = headersCopy.fieldNames();
            while (headerNames.hasNext()) {
                String headerName = headerNames.next();
                String lowerCaseName = headerName.toLowerCase();
                if (namesToRemove.contains(lowerCaseName) || lowerCaseName.equals(extraNameToRemove)) {
                    headerNamesToRemove.add(headerName);
                }
            }
            headersCopy.remove(headerNamesToRemove);
            message.put(headersField, headersCopy.toString());
        }
    }

    /** Moves the body of a single tRPC GET (its input, edited as the body) into the `input` query param, URL encoded; no-op otherwise. */
    public static boolean moveBodyToInputQuery(OriginalHttpRequest request) {
        try {
            if (request == null) {
                return false;
            }
            String body = request.getBody() == null ? "" : request.getBody().trim();
            if (body.isEmpty()) {
                return false; // hot path: nearly every request ends here
            }
            TrpcUrl trpcUrl = parseSingleGetUrl(request);
            if (trpcUrl == null || trpcUrl.inputJson != null) {
                return false;
            }
            String inputQueryPair = INPUT_QUERY_PARAM + "=" + encodeUriComponent(body);
            String queryParams = request.getQueryParams() == null ? "" : request.getQueryParams();
            request.setQueryParams(queryParams.isEmpty() ? inputQueryPair : inputQueryPair + "&" + queryParams);
            request.setBody("");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static TrpcUrl parseSingleGetUrl(OriginalHttpRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod()) || request.getUrl() == null) {
            return null;
        }
        // a test operation (modify_url) or the executor may have made the url absolute
        TrpcUrl trpcUrl = parseTrpcUrl(OriginalHttpRequest.getFullUrlWithParams(request.getUrl(), request.getQueryParams()));
        if (trpcUrl == null || trpcUrl.isBatch || trpcUrl.procedures.size() != 1) {
            return null;
        }
        return trpcUrl;
    }

    /** Missing node for a blank string, null when the string is not valid json. */
    private static JsonNode tryParseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return MissingNode.getInstance();
        }
        try {
            return jsonMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** superjson wraps values as {"json": value, "meta": {...}}. */
    private static JsonNode unwrapSuperjson(JsonNode node) {
        if (node != null && node.isObject() && node.has("json")) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                if (!SUPERJSON_ENVELOPE_KEYS.contains(fieldNames.next())) {
                    return node;
                }
            }
            return node.get("json");
        }
        return node;
    }

    /** True for a non empty object whose keys are all numbers, the shape of a batch input: {"0": .., "1": ..}. */
    private static boolean isKeyedByCallIndex(JsonNode node) {
        if (node == null || !node.isObject() || node.size() == 0) {
            return false;
        }
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (fieldName.isEmpty()) {
                return false;
            }
            for (int i = 0; i < fieldName.length(); i++) {
                if (!Character.isDigit(fieldName.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isJsonContentTypeOrAbsent(Map<String, List<String>> headers) {
        String contentType = HttpRequestResponseUtils.getHeaderValue(headers, "content-type");
        return contentType == null || contentType.toLowerCase().contains("json");
    }

    /** True when the request asks for a streamed response (tRPC v11 `trpc-accept: application/jsonl`). */
    private static boolean requestsJsonlStream(Map<String, List<String>> requestHeaders) {
        String trpcAccept = HttpRequestResponseUtils.getHeaderValue(requestHeaders, "trpc-accept");
        return trpcAccept != null && trpcAccept.toLowerCase().contains("jsonl");
    }

    private static Map<String, List<String>> copyHeadersExcept(Map<String, List<String>> headers, Set<String> namesToRemove) {
        Map<String, List<String>> headersCopy = new HashMap<>();
        if (headers == null) {
            return headersCopy;
        }
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (header.getKey() == null || namesToRemove.contains(header.getKey().toLowerCase())) {
                continue;
            }
            headersCopy.put(header.getKey(), header.getValue() == null ? new ArrayList<>() : new ArrayList<>(header.getValue()));
        }
        return headersCopy;
    }

    private static String joinPath(List<String> segments) {
        return "/" + String.join("/", segments);
    }

    /** Like JavaScript's encodeURIComponent: a space becomes %20, not +. */
    private static String encodeUriComponent(String value) {
        try {
            return URLEncoder.encode(value, UTF_8).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

}
