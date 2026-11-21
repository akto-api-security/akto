package com.akto.threat.detection.utils;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.RequestTemplate;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Whether a call's request reuses values the previous call's response handed out (e.g. an id from a list
// response sent to a delete). Only hashes of response values are kept, never the values themselves.
public final class DataReuse {

    private static final int MAX_VALUES = 32;
    private static final int MAX_BODY_CHARS = 64 * 1024;
    private static final int MAX_REUSED_PARAMS = 3;

    private DataReuse() {
    }

    // Hashes of up to MAX_VALUES trackable response values that the call's own request did not already carry; null if none
    public static long[] fingerprint(HttpResponseParams call) {
        String body = call.getPayload();
        if (body == null || body.isEmpty() || body.length() > MAX_BODY_CHARS) return null;
        Set<Long> alreadySent = new HashSet<>();
        for (Set<Object> values : requestValues(call.getRequestParams()).values()) {
            for (Object value : values) alreadySent.add(hash(value));
        }
        Set<Long> hashes = new LinkedHashSet<>();
        for (Set<Object> values : HttpRequestResponseUtils.extractValuesFromPayload(body).values()) {
            for (Object value : values) {
                if (!HttpRequestResponseUtils.isTrackableValue(value)) continue;
                long h = hash(value);
                if (!alreadySent.contains(h)) hashes.add(h);
                if (hashes.size() == MAX_VALUES) break;
            }
            if (hashes.size() == MAX_VALUES) break;
        }
        if (hashes.isEmpty()) return null;
        long[] fingerprint = new long[hashes.size()];
        int i = 0;
        for (Long h : hashes) fingerprint[i++] = h;
        Arrays.sort(fingerprint);
        return fingerprint;
    }

    // Names of request parameters (query, body or "path") whose values come from the earlier response; empty if none
    public static List<String> findReuse(long[] earlier, HttpResponseParams call) {
        List<String> reused = new ArrayList<>();
        if (earlier == null) return reused;
        for (Map.Entry<String, Set<Object>> param : requestValues(call.getRequestParams()).entrySet()) {
            for (Object value : param.getValue()) {
                if (HttpRequestResponseUtils.isTrackableValue(value) && Arrays.binarySearch(earlier, hash(value)) >= 0) {
                    reused.add(param.getKey());
                    break;
                }
            }
            if (reused.size() == MAX_REUSED_PARAMS) break;
        }
        return reused;
    }

    // Request values by parameter: query string and body (via the shared catalog parser), plus URL path segments as "path"
    private static Map<String, Set<Object>> requestValues(HttpRequestParams request) {
        String url = request.getURL() == null ? "" : request.getURL();
        String payload = request.getPayload();
        Map<String, Set<Object>> values = payload != null && payload.length() > MAX_BODY_CHARS
                ? new HashMap<>()
                : JSONUtils.flatten(RequestTemplate.parseRequestPayload(request, url));
        int query = url.indexOf('?');
        Set<Object> segments = new HashSet<>();
        for (String segment : (query >= 0 ? url.substring(0, query) : url).split("/")) {
            if (!segment.isEmpty()) segments.add(segment);
        }
        if (!segments.isEmpty()) values.put("path", segments);
        return values;
    }

    // 64-bit FNV-1a of the value's text, so 123456 and "123456" match
    private static long hash(Object value) {
        long h = 0xcbf29ce484222325L;
        for (char c : String.valueOf(value).toCharArray()) {
            h ^= c;
            h *= 0x100000001b3L;
        }
        return h;
    }
}
