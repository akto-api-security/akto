package com.akto.rag;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * RAG (Retrieval-Augmented Generation) detection.
 *
 * Identifies vector-database / RAG traffic using an ordered, corroboration-based model so that
 * generic endpoints are NOT mis-tagged as RAG (e.g. GET /graphql/query/GetTierGroupV2/tierGroup,
 * or paginated APIs whose body carries a "limit" field).
 *
 * Core principle: an ambiguous URL signal (one that reuses generic words such as "query" or
 * "collections") never flags on its own - it must be corroborated by vector evidence in the body.
 * Only signals that are essentially unique to vector search flag immediately.
 *
 * Evaluation order (returns at the first decision):
 *   1. strong vendor Host        -> RAG          (checked first: Weaviate runs vector search over GraphQL)
 *   2. GraphQL path              -> not RAG      (excludes the /graphql/... false-positive class)
 *   3. strong anchored path      -> RAG          (e.g. /points/search, /embeddings)
 *   4. strong body key           -> RAG          (e.g. query_vector, anns_field)
 *   5. ambiguous path + body key -> RAG          (e.g. Pinecone /query, Chroma /collections/{id}/query)
 *   6. >= 2 weak body keys       -> RAG
 *   7. otherwise                 -> not RAG
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RagDetector {

    private static final LoggerMaker logger = new LoggerMaker(RagDetector.class, LogDb.RUNTIME);

    /** Bound JSON traversal so a deeply nested / hostile payload cannot blow up. */
    private static final int MAX_JSON_DEPTH = 6;
    private static final int MAX_KEYS_SCANNED = 2000;

    // ==================== STEP 1: STRONG VENDOR HOST ====================
    // Managed vector-DB vendors, matched against the Host header. Vendor names sourced from
    // com.akto.dto.tracing.TracingConstants. "chroma" is intentionally restricted to chromadb /
    // trychroma because the bare word collides with chroma-key / colour / video hosts.
    private static final List<Pattern> VENDOR_HOST_PATTERNS = Arrays.asList(
        Pattern.compile("pinecone\\.io", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bweaviate\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bqdrant\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bmilvus\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(chromadb|trychroma)\\b", Pattern.CASE_INSENSITIVE)
    );

    // ==================== STEP 3: STRONG ANCHORED PATHS ====================
    // Anchored on vendor-specific segments with no generic words; safe to flag on their own.
    // Qdrant's /points/* is required to sit under /collections/<name>/ so a generic /points/search
    // (loyalty points, map points, ...) does not match.
    private static final List<Pattern> STRONG_PATH_PATTERNS = Arrays.asList(
        Pattern.compile("/vectors?/(search|upsert|delete|fetch)(?:[/?]|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/collections?/[^/]+/points/(search|recommend|scroll|query)(?:[/?]|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/vector[_-]?search(?:[/?]|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/embeddings?(?:[/?]|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/(knn|ann)[_-]?search(?:[/?]|$)", Pattern.CASE_INSENSITIVE)
    );

    // ==================== STEP 5: AMBIGUOUS PATHS (need body corroboration) ====================
    // These reuse generic words ("query"/"collections") so they only flag when the body explicitly
    // carries a vector/embedding key. Covers Pinecone's bare POST /query and Chroma's
    // /collections/{id}/query.
    private static final List<Pattern> AMBIGUOUS_PATH_PATTERNS = Arrays.asList(
        Pattern.compile("/query(?:[/?]|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/collections?/[^/]+/(query|search)(?:[/?]|$)", Pattern.CASE_INSENSITIVE)
    );

    // ==================== STEP 4: STRONG BODY KEYS ====================
    // Phrases that essentially never occur outside vector search. A single match flags RAG.
    private static final Set<String> STRONG_BODY_KEYS = new HashSet<>(Arrays.asList(
        "query_vector", "query_vectors", "query_embedding", "query_embeddings", "anns_field"
    ));

    // ==================== BODY SIGNAL CATEGORIES (steps 5-6) ====================
    // VECTOR_PRESENCE - explicit "what": the request actually carries a vector/embedding.
    private static final Set<String> VECTOR_PRESENCE_KEYS = new HashSet<>(Arrays.asList(
        "vector", "vectors", "embedding", "embeddings"
    ));
    // SEARCH_INTENT - high-precision "how": retrieval parameters used by real vector DBs. Generic
    // concept words (similarity, cosine, euclidean, metric_type, nearest, neighbors, knn, ann) are
    // deliberately excluded - they collide with analytics / geo / graph / maths APIs.
    private static final Set<String> SEARCH_INTENT_KEYS = new HashSet<>(Arrays.asList(
        "top_k", "topk", "n_results", "with_payload", "output_fields",
        "search_params", "include_metadata", "includemetadata"
    ));

    /**
     * Check if the request is a RAG operation. Returns true if RAG detected, false otherwise.
     */
    public static boolean isRagRequest(HttpResponseParams responseParams) {
        if (responseParams == null || responseParams.getRequestParams() == null) {
            return false;
        }

        try {
            String url = responseParams.getRequestParams().getURL();
            if (StringUtils.isBlank(url)) {
                return false;
            }

            // Step 1: strong vendor host (before GraphQL exclusion - Weaviate searches over GraphQL)
            String host = HttpRequestResponseUtils.getHeaderValue(
                responseParams.getRequestParams().getHeaders(), Constants.HOST_HEADER);
            if (matchesAny(VENDOR_HOST_PATTERNS, host)) {
                logger.info("Detected RAG operation from vendor host: " + host);
                return true;
            }

            // Step 2: GraphQL is never RAG - kills the /graphql/query/... false-positive class
            if (url.toLowerCase().contains("graphql")) {
                return false;
            }

            // Step 3: strong anchored path
            if (matchesAny(STRONG_PATH_PATTERNS, url)) {
                logger.info("Detected RAG operation from vector path: " + url);
                return true;
            }

            // Steps 4-6: body-based detection (with ambiguous-path corroboration)
            return checkBody(responseParams.getRequestParams().getPayload(), url);

        } catch (Exception e) {
            logger.error("Error detecting RAG operation: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Body analysis (all conditions require parsed JSON keys):
     *   step 4 - a strong, vector-exclusive key flags immediately;
     *   step 5 - an ambiguous path ({@code /query}, {@code /collections/<name>/query}) corroborated
     *            by an explicit vector/embedding key;
     *   step 6 - body alone needs BOTH an explicit vector/embedding key AND a high-precision
     *            retrieval term (AND of categories) - neither category alone is enough.
     */
    private static boolean checkBody(String requestBody, String url) {
        if (StringUtils.isBlank(requestBody)) {
            return false;
        }

        Object parsed;
        try {
            parsed = JSON.parse(requestBody);
        } catch (Exception e) {
            // Not JSON - we don't keyword-scan raw text to avoid substring false positives.
            return false;
        }
        if (parsed == null) {
            return false;
        }

        Set<String> keys = new HashSet<>();
        collectKeys(parsed, keys, 0, new int[]{0});

        // Step 4: strong body key
        for (String strong : STRONG_BODY_KEYS) {
            if (keys.contains(strong)) {
                logger.info("Detected RAG operation from strong body key: " + strong);
                return true;
            }
        }

        boolean hasVectorPresence = containsAny(keys, VECTOR_PRESENCE_KEYS);
        boolean hasSearchIntent = containsAny(keys, SEARCH_INTENT_KEYS);

        // Step 5: ambiguous path corroborated by an explicit vector/embedding key
        if (hasVectorPresence && matchesAny(AMBIGUOUS_PATH_PATTERNS, url)) {
            logger.info("Detected RAG operation from ambiguous path " + url + " corroborated by a vector key");
            return true;
        }

        // Step 6: body alone - vector/embedding key AND a retrieval term must co-occur
        if (hasVectorPresence && hasSearchIntent) {
            logger.info("Detected RAG operation from vector + retrieval body signals on URL: " + url);
            return true;
        }

        return false;
    }

    private static boolean containsAny(Set<String> keys, Set<String> candidates) {
        for (String candidate : candidates) {
            if (keys.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAny(List<Pattern> patterns, String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        for (Pattern p : patterns) {
            if (p.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursively collect all object keys (lower-cased) from a parsed JSON value, bounded by depth
     * and total keys scanned to stay safe on large / hostile payloads.
     */
    private static void collectKeys(Object node, Set<String> out, int depth, int[] scanned) {
        if (node == null || depth > MAX_JSON_DEPTH || scanned[0] >= MAX_KEYS_SCANNED) {
            return;
        }
        if (node instanceof JSONObject) {
            for (Map.Entry<String, Object> entry : ((JSONObject) node).entrySet()) {
                if (scanned[0]++ >= MAX_KEYS_SCANNED) {
                    return;
                }
                out.add(entry.getKey().toLowerCase());
                collectKeys(entry.getValue(), out, depth + 1, scanned);
            }
        } else if (node instanceof JSONArray) {
            for (Object item : (JSONArray) node) {
                if (scanned[0] >= MAX_KEYS_SCANNED) {
                    return;
                }
                collectKeys(item, out, depth + 1, scanned);
            }
        }
    }
}
