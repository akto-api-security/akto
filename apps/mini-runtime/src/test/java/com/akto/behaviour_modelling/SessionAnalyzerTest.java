package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.impl.IpBasedIdentifier;
import com.akto.behaviour_modelling.impl.RawCountAccumulator;
import com.akto.behaviour_modelling.model.TransitionKey;
import com.akto.behaviour_modelling.model.WindowSnapshot;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfoCatalog;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.PolicyCatalog;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.hybrid_runtime.policies.AktoPolicyNew;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SessionAnalyzerTest {

    private static final int COLLECTION_ID = 1001;

    private AktoPolicyNew aktoPolicyNew;
    private final AtomicReference<WindowSnapshot> capturedSnapshot = new AtomicReference<>();

    @BeforeEach
    public void setUp() throws Exception {
        aktoPolicyNew = new AktoPolicyNew();
        capturedSnapshot.set(null);
        seedDefaultCatalog();
    }

    private void seedDefaultCatalog() {
        Map<URLStatic, PolicyCatalog> strictUrls = new HashMap<>();
        Map<URLTemplate, PolicyCatalog> templateUrls = new HashMap<>();

        addStrict(strictUrls, "GET", "/products");
        addStrict(strictUrls, "POST", "/cart/add");
        addStrict(strictUrls, "POST", "/checkout");
        addTemplate(templateUrls, "GET", "/products/123");

        ApiInfoCatalog catalog = new ApiInfoCatalog(strictUrls, templateUrls, new ArrayList<>());
        Map<Integer, ApiInfoCatalog> catalogMap = new HashMap<>();
        catalogMap.put(COLLECTION_ID, catalog);
        aktoPolicyNew.setApiInfoCatalogMap(catalogMap);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private SessionAnalyzer buildAnalyzer() {
        SequenceAnalyzerConfig config = new SequenceAnalyzerConfig(
                2,
                10 * 60 * 1000L,
                new IpBasedIdentifier(),
                RawCountAccumulator::new,
                snapshot -> capturedSnapshot.set(snapshot),
                aktoPolicyNew
        );
        return new SessionAnalyzer(config);
    }

    private HttpResponseParams record(String method, String url, String sourceIp) {
        return record(method, url, sourceIp, COLLECTION_ID);
    }

    private HttpResponseParams record(String method, String url, String sourceIp, int collectionId) {
        HttpRequestParams req = new HttpRequestParams(method, url, "", new HashMap<>(), "", collectionId);
        HttpResponseParams resp = new HttpResponseParams(
                "", 200, "", new HashMap<>(), "", req, 0, "0", false,
                HttpResponseParams.Source.OTHER, "", "");
        resp.setSourceIP(sourceIp);
        return resp;
    }

    private static void addStrict(Map<URLStatic, PolicyCatalog> map, String method, String url) {
        URLMethods.Method m = URLMethods.Method.fromString(method);
        map.put(new URLStatic(url, m), new PolicyCatalog(new ApiInfo(
                new ApiInfoKey(COLLECTION_ID, url, m)), null));
    }

    private static void addTemplate(Map<URLTemplate, PolicyCatalog> map, String method, String rawUrl) {
        URLMethods.Method m = URLMethods.Method.fromString(method);
        URLTemplate tmpl = APICatalogSync.tryParamteresingUrl(new URLStatic(rawUrl, m), false);
        if (tmpl == null) return;
        map.put(tmpl, new PolicyCatalog(new ApiInfo(
                new ApiInfoKey(COLLECTION_ID, tmpl.getTemplateString(), m)), null));
    }

    @SuppressWarnings("unchecked")
    private static void setEnv(String key, String value) throws Exception {
        Map<String, String> env = System.getenv();
        java.lang.reflect.Field field = env.getClass().getDeclaredField("m");
        field.setAccessible(true);
        ((Map<String, String>) field.get(env)).put(key, value);
    }

    private WindowSnapshot flushAndCapture(SessionAnalyzer analyzer) throws InterruptedException {
        analyzer.triggerWindowEnd();
        // Flush is async — give CompletableFuture a moment to land.
        for (int i = 0; i < 50 && capturedSnapshot.get() == null; i++) {
            Thread.sleep(10);
        }
        return capturedSnapshot.get();
    }

    private ApiInfoKey key(String method, String url) {
        return key(method, url, COLLECTION_ID);
    }

    private ApiInfoKey key(String method, String url, int collectionId) {
        return new ApiInfoKey(collectionId, url, URLMethods.Method.fromString(method));
    }

    private TransitionKey transition(String fromMethod, String fromUrl, String toMethod, String toUrl) {
        return new TransitionKey(new ApiInfoKey[]{key(fromMethod, fromUrl), key(toMethod, toUrl)});
    }

    // ── Test 1: Templatization + known-API filtering ───────────────────────────

    @Test
    public void test1_templatizationAndUnknownApiFiltering() throws InterruptedException {
        SessionAnalyzer analyzer = buildAnalyzer();

        // Two records with different numeric IDs → same templatized key
        analyzer.process(record("GET", "/products/111", "1.1.1.1"));
        analyzer.process(record("GET", "/products/222", "1.1.1.1"));
        // Unknown API → should be dropped silently
        analyzer.process(record("GET", "/unknown/path", "1.1.1.1"));

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot, "Snapshot should not be null");

        // Both /products/111 and /products/222 must collapse to one templatized key
        Assertions.assertEquals(1, snapshot.getApiCounts().size(),
                "Only one templatized API key expected");

        ApiInfoKey expected = key("GET", "/products/INTEGER");
        Long count = snapshot.getApiCounts().get(expected);
        Assertions.assertNotNull(count, "Templatized key not found in snapshot");
        Assertions.assertEquals(2L, count, "Both numeric variants should map to count=2");

        // Two consecutive calls to /products/INTEGER produce a self-loop transition
        Assertions.assertEquals(1, snapshot.getTransitionCounts().size(),
                "Exactly one self-loop transition expected");
        TransitionKey selfLoop = transition("GET", "/products/INTEGER", "GET", "/products/INTEGER");
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(selfLoop),
                "Self-loop count should be 1");

        // Unknown API was dropped — not present in either map
        ApiInfoKey unknown = key("GET", "/unknown/path");
        Assertions.assertFalse(snapshot.getApiCounts().containsKey(unknown),
                "Unknown API should have been dropped");

        analyzer.shutdown();
    }

    // ── Test 2: Transition accumulation across multiple users ──────────────────

    @Test
    public void test2_transitionAggregationAcrossUsers() throws InterruptedException {
        SessionAnalyzer analyzer = buildAnalyzer();

        // User A: GET /products → POST /cart/add
        analyzer.process(record("GET", "/products", "user-a"));
        analyzer.process(record("POST", "/cart/add", "user-a"));

        // User B: same transition
        analyzer.process(record("GET", "/products", "user-b"));
        analyzer.process(record("POST", "/cart/add", "user-b"));

        // User C: different transition
        analyzer.process(record("GET", "/products", "user-c"));
        analyzer.process(record("POST", "/checkout", "user-c"));

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot);

        Assertions.assertEquals(3L, snapshot.getApiCounts().get(key("GET", "/products")));
        Assertions.assertEquals(2L, snapshot.getApiCounts().get(key("POST", "/cart/add")));
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("POST", "/checkout")));

        TransitionKey toCart = transition("GET", "/products", "POST", "/cart/add");
        Assertions.assertEquals(2L, snapshot.getTransitionCounts().get(toCart));

        TransitionKey toCheckout = transition("GET", "/products", "POST", "/checkout");
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(toCheckout));

        analyzer.shutdown();
    }

    // ── Test 3: Window boundary — no cross-window transitions ─────────────────

    @Test
    public void test3_windowBoundaryIsolation() throws InterruptedException {
        SessionAnalyzer analyzer = buildAnalyzer();

        // Window N: user-a calls GET /products only (deque = [GET /products])
        analyzer.process(record("GET", "/products", "user-a"));

        Thread.sleep(100);
        WindowSnapshot windowN = flushAndCapture(analyzer);
        Assertions.assertNotNull(windowN, "Window N snapshot should not be null");
        Assertions.assertEquals(1L, windowN.getApiCounts().get(key("GET", "/products")));
        Assertions.assertTrue(windowN.getTransitionCounts().isEmpty(),
                "No transitions in window N — only one call");

        capturedSnapshot.set(null);
        // Window N+1: same user calls POST /checkout
        // Deque was lazily reset at window boundary — no transition should fire.
        analyzer.process(record("POST", "/checkout", "user-a"));

        WindowSnapshot windowN1 = flushAndCapture(analyzer);
        Assertions.assertNotNull(windowN1, "Window N+1 snapshot should not be null");
        Assertions.assertEquals(1L, windowN1.getApiCounts().get(key("POST", "/checkout")));

        System.out.println("DEBUG test3: windowN1 transitionCounts = " + windowN1.getTransitionCounts());
        System.out.println("DEBUG test3: windowN1 transitionCounts.isEmpty() = " + windowN1.getTransitionCounts().isEmpty());
        if (!windowN1.getTransitionCounts().isEmpty()) {
            windowN1.getTransitionCounts().forEach((k, v) ->
                System.out.println("DEBUG test3: transition = " + k + ", count = " + v));
        }

        Assertions.assertTrue(windowN1.getTransitionCounts().isEmpty(),
                "No cross-window transition expected — deque must reset on window boundary");

        analyzer.shutdown();
    }

    // ── Test 7: Multiple collections isolation ─────────────────────────────────

    @Test
    public void test7_multipleCollectionsIsolation() throws InterruptedException {
        // Seed catalog for both collections BEFORE building analyzer
        Map<URLStatic, PolicyCatalog> strictUrls1 = new HashMap<>();
        Map<URLTemplate, PolicyCatalog> templateUrls1 = new HashMap<>();
        addStrict(strictUrls1, "GET", "/products");
        addTemplate(templateUrls1, "GET", "/products/123");

        ApiInfoCatalog catalog1 = new ApiInfoCatalog(strictUrls1, templateUrls1, new ArrayList<>());

        Map<URLStatic, PolicyCatalog> strictUrls2 = new HashMap<>();
        Map<URLTemplate, PolicyCatalog> templateUrls2 = new HashMap<>();
        addStrict(strictUrls2, "POST", "/orders");
        addTemplate(templateUrls2, "POST", "/orders/456");

        ApiInfoCatalog catalog2 = new ApiInfoCatalog(strictUrls2, templateUrls2, new ArrayList<>());

        Map<Integer, ApiInfoCatalog> catalogMap = new HashMap<>();
        catalogMap.put(1001, catalog1);
        catalogMap.put(1002, catalog2);
        aktoPolicyNew.setApiInfoCatalogMap(catalogMap);


        // Build analyzer AFTER setting up the dual-collection catalog
        SessionAnalyzer analyzer = buildAnalyzer();

        // Collection 1001: GET /products
        analyzer.process(record("GET", "/products", "user-a", 1001));
        analyzer.process(record("GET", "/products/111", "user-a", 1001));

        // Collection 1002: POST /orders
        analyzer.process(record("POST", "/orders", "user-b", 1002));
        analyzer.process(record("POST", "/orders/789", "user-b", 1002));

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot);

        // Collection 1001: /products → /products/INTEGER
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("GET", "/products", 1001)),
                "Collection 1001: GET /products count should be 1");
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("GET", "/products/INTEGER", 1001)),
                "Collection 1001: GET /products/INTEGER count should be 1");
        TransitionKey trans1 = new TransitionKey(new ApiInfoKey[]{
                key("GET", "/products", 1001), key("GET", "/products/INTEGER", 1001)});
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(trans1),
                "Collection 1001: transition /products → /products/INTEGER should be 1");

        // Collection 1002: /orders → /orders/INTEGER
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("POST", "/orders", 1002)),
                "Collection 1002: POST /orders count should be 1");
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("POST", "/orders/INTEGER", 1002)),
                "Collection 1002: POST /orders/INTEGER count should be 1");
        TransitionKey trans2 = new TransitionKey(new ApiInfoKey[]{
                key("POST", "/orders", 1002), key("POST", "/orders/INTEGER", 1002)});
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(trans2),
                "Collection 1002: transition /orders → /orders/INTEGER should be 1");

        // Overall counts
        Assertions.assertEquals(4, snapshot.getApiCounts().size(),
                "Should have 4 total API entries across both collections");
        Assertions.assertEquals(2, snapshot.getTransitionCounts().size(),
                "Should have 2 total transitions across both collections");

        analyzer.shutdown();

        // Reset catalog to default for subsequent tests
        seedDefaultCatalog();
    }

    // ── Test 8: Rapid user scale-up ────────────────────────────────────────────

    @Test
    public void test8_rapidUserScaleUp() throws InterruptedException {
        SessionAnalyzer analyzer = buildAnalyzer();

        // Feed 1000 unique users, each making 2 calls (A → B transition)
        for (int i = 0; i < 1000; i++) {
            String userId = "user-" + i;
            analyzer.process(record("GET", "/products", userId));
            analyzer.process(record("POST", "/cart/add", userId));
        }

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot);

        // 1000 unique users × 1 call to /products = 1000
        Assertions.assertEquals(1000L, snapshot.getApiCounts().get(key("GET", "/products")));
        // 1000 unique users × 1 call to /cart/add = 1000
        Assertions.assertEquals(1000L, snapshot.getApiCounts().get(key("POST", "/cart/add")));
        // 1000 unique users × 1 transition = 1000
        TransitionKey trans = transition("GET", "/products", "POST", "/cart/add");
        Assertions.assertEquals(1000L, snapshot.getTransitionCounts().get(trans));

        analyzer.shutdown();
    }

    // ── Test 10: API method separation ─────────────────────────────────────────

    @Test
    public void test10_apiMethodSeparation() throws InterruptedException {
        // Seed catalog with same URL, different methods
        Map<URLStatic, PolicyCatalog> strictUrls = new HashMap<>();
        Map<URLTemplate, PolicyCatalog> templateUrls = new HashMap<>();

        addStrict(strictUrls, "GET", "/api/users");
        addStrict(strictUrls, "POST", "/api/users");
        addTemplate(templateUrls, "PUT", "/api/users/123");

        ApiInfoCatalog catalog = new ApiInfoCatalog(strictUrls, templateUrls, new ArrayList<>());
        Map<Integer, ApiInfoCatalog> catalogMap = new HashMap<>();
        catalogMap.put(COLLECTION_ID, catalog);
        aktoPolicyNew.setApiInfoCatalogMap(catalogMap);

        SessionAnalyzer analyzer = buildAnalyzer();

        // Same user, different methods on same URL path
        analyzer.process(record("GET", "/api/users", "user-a"));
        analyzer.process(record("POST", "/api/users", "user-a"));
        analyzer.process(record("PUT", "/api/users/456", "user-a"));

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot);

        // Each method is a separate key
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("GET", "/api/users")));
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("POST", "/api/users")));
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("PUT", "/api/users/INTEGER")));

        // Transitions respect method boundaries
        TransitionKey getToPost = transition("GET", "/api/users", "POST", "/api/users");
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(getToPost));

        TransitionKey postToPut = transition("POST", "/api/users", "PUT", "/api/users/INTEGER");
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(postToPut));

        analyzer.shutdown();
    }

    // ── Test 12: Per-user deque independence ───────────────────────────────────

    @Test
    public void test12_perUserDequeIndependence() throws InterruptedException {
        SessionAnalyzer analyzer = buildAnalyzer();

        // User A: builds full deque context (3 calls)
        analyzer.process(record("GET", "/products", "user-a"));
        analyzer.process(record("GET", "/products/111", "user-a"));
        analyzer.process(record("POST", "/cart/add", "user-a"));

        // User B: only 1 call (insufficient deque context)
        analyzer.process(record("POST", "/checkout", "user-b"));

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot);

        // All 4 APIs counted
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("GET", "/products")));
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("GET", "/products/INTEGER")));
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("POST", "/cart/add")));
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("POST", "/checkout")));

        // User A emitted 2 transitions (products→products/INTEGER, products/INTEGER→cart/add)
        TransitionKey aTransition1 = transition("GET", "/products", "GET", "/products/INTEGER");
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(aTransition1));

        TransitionKey aTransition2 = transition("GET", "/products/INTEGER", "POST", "/cart/add");
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(aTransition2));

        // User B: only 1 call, no context = no transition emitted
        // Total transitions should be exactly 2 (not 3)
        Assertions.assertEquals(2, snapshot.getTransitionCounts().size(),
                "User B's single call should not emit a transition");

        analyzer.shutdown();
    }
}
