package com.akto.rag;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

import static com.akto.util.Constants.AKTO_RAG_DATABASE_TAG;

/**
 * RAG (Retrieval-Augmented Generation) detection system.
 * Optimized detector that identifies vector database and RAG-related traffic.
 * Parses JSON and checks if RAG keywords exist as keys.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RagDetector {

    private static final LoggerMaker logger = new LoggerMaker(RagDetector.class, LogDb.RUNTIME);

    // ==================== URL PATTERNS ====================
    private static final List<Pattern> RAG_URL_PATTERNS = Arrays.asList(
        // Vector database patterns
        Pattern.compile(".*/collections?.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/vectors?.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/query.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/embeddings?.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*pinecone\\.io.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/chroma.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*weaviate.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*qdrant.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*milvus.*", Pattern.CASE_INSENSITIVE)
    );

    // ==================== DETECTION KEYWORDS ====================
    private static final Set<String> RAG_KEYWORDS = new HashSet<>(Arrays.asList(
        "embedding", "embeddings", "vector", "vectors", "query_embedding",
        "query_vector", "similarity", "distance", "cosine", "euclidean",
        "nearest", "neighbors", "knn", "ann", "top_k", "n_results",
        "collection", "collections", "collection_name", "index", "indices",
        "anns_field", "search_params", "metric_type", "limit",
        "namespace", "output_fields", "with_payload", "include_metadata"
    ));

    /**
     * Check if the request is a RAG operation.
     * Returns true if RAG detected, false otherwise.
     */
    public static boolean isRagRequest(HttpResponseParams responseParams) {
        if (responseParams == null) {
            return false;
        }

        try {
            String url = responseParams.getRequestParams().getURL();
            String requestBody = responseParams.getRequestParams().getPayload();

            if (url == null) {
                return false;
            }

            // Check URL patterns
            if (checkUrlPatterns(url)) {
                logger.info("Detected RAG operation from URL: " + url);
                return true;
            }

            // Check body patterns
            if (checkBodyPatterns(requestBody)) {
                logger.info("Detected RAG operation from body content");
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error detecting RAG operation: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if URL matches RAG patterns
     */
    private static boolean checkUrlPatterns(String url) {
        for (Pattern pattern : RAG_URL_PATTERNS) {
            if (pattern.matcher(url).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check request/response body for RAG keywords
     */
    private static boolean checkBodyPatterns(String requestBody) {
        try {
            // Check request body
            if (StringUtils.isNotBlank(requestBody)) {
                if (containsRagKeys(requestBody)) {
                    logger.info("Detected RAG operation from request body");
                    return true;
                }
            }

        } catch (Exception e) {
            logger.debug("Error checking body patterns: " + e.getMessage());
        }

        return false;
    }

    /**
     * Parse JSON and check if any RAG keyword exists as a key.
     * Iterates through RAG_KEYWORDS and checks if they exist in parsed JSON.
     */
    private static boolean containsRagKeys(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return false;
        }

        try {
            // Parse JSON with fastjson2
            JSONObject json = JSON.parseObject(jsonString);

            // Check each keyword from RAG_KEYWORDS
            for (String keyword : RAG_KEYWORDS) {
                if (json.containsKey(keyword)) {
                    return true;  // Found RAG keyword as key
                }
            }

        } catch (Exception e) {
            // Not valid JSON or parsing error
            return false;
        }

        return false;
    }
}
