package com.akto.rag;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

import static com.akto.util.Constants.AKTO_RAG_DATABASE_TAG;

/**
 * RAG (Retrieval-Augmented Generation) detection system.
 * Simple detector that identifies vector database and RAG-related traffic.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RagDetector {

    private static final LoggerMaker logger = new LoggerMaker(RagDetector.class, LogDb.RUNTIME);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        "collection", "collections", "index", "indices"
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
            String responseBody = responseParams.getPayload();

            if (url == null) {
                return false;
            }

            // Check URL patterns
            if (checkUrlPatterns(url)) {
                logger.info("Detected RAG operation from URL: " + url);
                return true;
            }

            // Check body patterns
            if (checkBodyPatterns(requestBody, responseBody)) {
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
    private static boolean checkBodyPatterns(String requestBody, String responseBody) {
        try {
            // Check request body
            if (StringUtils.isNotBlank(requestBody)) {
                if (containsRagKeywords(requestBody)) {
                    JsonNode requestJson = OBJECT_MAPPER.readTree(requestBody);
                    if (hasVectorData(requestJson)) {
                        return true;
                    }
                }
            }

            // Check response body
            if (StringUtils.isNotBlank(responseBody)) {
                if (containsRagKeywords(responseBody)) {
                    JsonNode responseJson = OBJECT_MAPPER.readTree(responseBody);
                    if (hasVectorData(responseJson)) {
                        return true;
                    }
                }
            }

        } catch (Exception e) {
            logger.debug("Error checking body patterns: " + e.getMessage());
        }

        return false;
    }

    /**
     * Check if text contains RAG keywords
     */
    private static boolean containsRagKeywords(String text) {
        String lowerText = text.toLowerCase();
        for (String keyword : RAG_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if JSON contains vector/embedding data
     */
    private static boolean hasVectorData(JsonNode json) {
        if (json == null) return false;

        Iterator<String> fieldNames = json.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next().toLowerCase();
            if (RAG_KEYWORDS.contains(fieldName)) {
                JsonNode value = json.get(fieldName);
                // Check if it's an array of numbers (typical for embeddings/vectors)
                if (value.isArray() && value.size() > 0) {
                    JsonNode first = value.get(0);
                    if (first.isNumber() || (first.isArray() && first.size() > 0)) {
                        return true;
                    }
                }
                return true;
            }
        }

        // Recursively check nested objects
        Iterator<JsonNode> elements = json.elements();
        while (elements.hasNext()) {
            JsonNode element = elements.next();
            if (element.isObject() && hasVectorData(element)) {
                return true;
            }
        }

        return false;
    }
}
