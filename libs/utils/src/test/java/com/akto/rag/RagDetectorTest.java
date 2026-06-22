package com.akto.rag;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RagDetectorTest {

    private HttpResponseParams params(String url, String payload, Map<String, List<String>> headers) {
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.setUrl(url);
        reqParams.setPayload(payload);
        reqParams.setHeaders(headers == null ? new HashMap<>() : headers);
        reqParams.setApiCollectionId(123456789);

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(reqParams);
        responseParams.statusCode = 200;
        return responseParams;
    }

    private HttpResponseParams params(String url, String payload) {
        return params(url, payload, null);
    }

    private Map<String, List<String>> hostHeader(String host) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Host", Collections.singletonList(host));
        return headers;
    }

    // ==================== FALSE POSITIVES ====================

    @Test
    public void testGraphQLQueryPathIsNotRag() {
        // The reported regression: GraphQL endpoint must never be tagged RAG.
        assertFalse(isRag("/graphql/query/GetTierGroupV2/tierGroup", null));
    }

    @Test
    public void testPaginatedLimitBodyIsNotRag() {
        assertFalse(isRag("/api/users", "{\"limit\":10,\"offset\":0}"));
    }

    @Test
    public void testSingleWeakKeyIsNotRag() {
        // One weak key, no corroborating path or second key.
        assertFalse(isRag("/api/items", "{\"vector\":[0.1,0.2,0.3]}"));
    }

    @Test
    public void testAmbiguousCollectionsQueryWithGraphQLBodyIsNotRag() {
        String body = "{\"query\":\"query { tierGroup { id } }\",\"operationName\":\"q\",\"variables\":{}}";
        assertFalse(isRag("/collections/users/query", body));
    }

    @Test
    public void testAmbiguousQueryPathWithNonVectorBodyIsNotRag() {
        assertFalse(isRag("/query", "{\"name\":\"x\"}"));
    }

    @Test
    public void testGenericCollectionsPathIsNotRag() {
        assertFalse(isRag("/api/v1/collections", "{\"name\":\"my-collection\"}"));
    }

    @Test
    public void testQueryPathWithRetrievalTermButNoVectorIsNotRag() {
        // analytics-style "top K" query - retrieval term but no vector/embedding key.
        assertFalse(isRag("/query", "{\"top_k\":5,\"metric\":\"count\"}"));
    }

    @Test
    public void testAnalyticsMetricTypeIsNotRag() {
        // monitoring/analytics body - metric_type is no longer a signal at all.
        assertFalse(isRag("/api/query", "{\"metric_type\":\"gauge\",\"similarity\":0.9}"));
    }

    @Test
    public void testGeoNearestNeighborsIsNotRag() {
        // geo/graph body - "nearest"/"neighbors" no longer count as signals.
        assertFalse(isRag("/api/locations/query", "{\"nearest\":true,\"neighbors\":[1,2]}"));
    }

    @Test
    public void testGraphicsVectorWithoutRetrievalIsNotRag() {
        // 3D/physics API uses "vector"/"vectors" heavily but has no retrieval intent.
        assertFalse(isRag("/api/render", "{\"vector\":[1,2,3],\"vectors\":[[4,5,6]]}"));
    }

    @Test
    public void testGenericPointsSearchIsNotRag() {
        // loyalty/map "points" search - not under /collections/<name>/points.
        assertFalse(isRag("/api/points/search", "{\"q\":\"x\"}"));
    }

    // ==================== TRUE POSITIVES ====================

    @Test
    public void testPineconeVendorHostIsRag() {
        assertTrue(isRag("/query", null, hostHeader("my-index-abc.svc.us-east1.pinecone.io")));
    }

    @Test
    public void testWeaviateVendorHostOverGraphQLIsRag() {
        // Weaviate runs vector search over GraphQL - host wins before the GraphQL exclusion.
        assertTrue(isRag("/v1/graphql", "{\"query\":\"{ Get { Doc } }\"}", hostHeader("my-cluster.weaviate.network")));
    }

    @Test
    public void testPineconeQueryPathCorroboratedByBodyIsRag() {
        assertTrue(isRag("/query", "{\"vector\":[0.1,0.2],\"topK\":10,\"includeMetadata\":true}"));
    }

    @Test
    public void testChromaCollectionsQueryIsRag() {
        // query_embeddings is a strong key; also a corroborated ambiguous path.
        assertTrue(isRag("/api/v1/collections/docs/query", "{\"query_embeddings\":[[0.1,0.2]],\"n_results\":10}"));
    }

    @Test
    public void testQdrantPointsSearchPathIsRag() {
        assertTrue(isRag("/collections/docs/points/search", "{\"vector\":[0.1,0.2],\"limit\":5}"));
    }

    @Test
    public void testEmbeddingsPathIsRag() {
        assertTrue(isRag("/v1/embeddings", "{\"input\":\"hello\",\"model\":\"text-embedding-3-small\"}"));
    }

    @Test
    public void testStrongBodyKeyIsRag() {
        assertTrue(isRag("/api/search", "{\"query_vector\":[0.1,0.2,0.3]}"));
    }

    @Test
    public void testTwoWeakKeysIsRag() {
        assertTrue(isRag("/api/search", "{\"vector\":[0.1,0.2],\"top_k\":5}"));
    }

    @Test
    public void testNestedVectorBodyIsRag() {
        // Qdrant-style nested payload - keys collected recursively.
        assertTrue(isRag("/api/search", "{\"params\":{\"vector\":[0.1],\"top_k\":3}}"));
    }

    // ==================== EDGE CASES ====================

    @Test
    public void testNullRequestParamsIsNotRag() {
        HttpResponseParams rp = new HttpResponseParams();
        assertFalse(RagDetector.isRagRequest(rp));
    }

    @Test
    public void testNullResponseParamsIsNotRag() {
        assertFalse(RagDetector.isRagRequest(null));
    }

    @Test
    public void testBlankUrlIsNotRag() {
        assertFalse(isRag("", "{\"query_vector\":[0.1]}"));
    }

    @Test
    public void testNonJsonBodyIsNotRag() {
        // Raw text containing keywords must not be keyword-scanned.
        assertFalse(isRag("/api/search", "this is a vector embedding similarity top_k blob"));
    }

    // ==================== helpers ====================

    private boolean isRag(String url, String payload) {
        return RagDetector.isRagRequest(params(url, payload));
    }

    private boolean isRag(String url, String payload, Map<String, List<String>> headers) {
        return RagDetector.isRagRequest(params(url, payload, headers));
    }
}
