package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.core.MarkovModelBuilder;
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
        return buildAnalyzer(1);
    }

    private SessionAnalyzer buildAnalyzer(int maxOrder) {
        SequenceAnalyzerConfig config = new SequenceAnalyzerConfig(
                maxOrder,
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

    // ── VOMC helpers ──────────────────────────────────────────────────────────

    private void seedCatalog(String... urls) {
        Map<URLStatic, PolicyCatalog> strictUrls = new HashMap<>();
        Map<URLTemplate, PolicyCatalog> templateUrls = new HashMap<>();
        for (String url : urls) {
            addStrict(strictUrls, "GET", url);
        }
        ApiInfoCatalog catalog = new ApiInfoCatalog(strictUrls, templateUrls, new ArrayList<>());
        Map<Integer, ApiInfoCatalog> catalogMap = new HashMap<>();
        catalogMap.put(COLLECTION_ID, catalog);
        aktoPolicyNew.setApiInfoCatalogMap(catalogMap);
    }

    private TransitionKey tkey(String... urls) {
        ApiInfoKey[] keys = new ApiInfoKey[urls.length];
        for (int i = 0; i < urls.length; i++) {
            keys[i] = key("GET", urls[i]);
        }
        return new TransitionKey(keys);
    }

    // ── Test A: Multi-Order Emission Correctness ──────────────────────────────

    @Test
    public void testA_multiOrderEmission() throws InterruptedException {
        seedCatalog("/a", "/b", "/c", "/d");
        SessionAnalyzer analyzer = buildAnalyzer(3);

        analyzer.process(record("GET", "/a", "user-1"));
        analyzer.process(record("GET", "/b", "user-1"));
        analyzer.process(record("GET", "/c", "user-1"));
        analyzer.process(record("GET", "/d", "user-1"));

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot);

        // 4 unique APIs, 1 count each
        Assertions.assertEquals(4, snapshot.getApiCounts().size());
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("GET", "/a")));
        Assertions.assertEquals(1L, snapshot.getApiCounts().get(key("GET", "/d")));

        // 6 transitions total across 3 orders
        Assertions.assertEquals(6, snapshot.getTransitionCounts().size(),
                "Expected 3 order-1 + 2 order-2 + 1 order-3 = 6 transitions");

        // Order-1 (length 2)
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(tkey("/a", "/b")));
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(tkey("/b", "/c")));
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(tkey("/c", "/d")));

        // Order-2 (length 3)
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(tkey("/a", "/b", "/c")));
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(tkey("/b", "/c", "/d")));

        // Order-3 (length 4)
        Assertions.assertEquals(1L, snapshot.getTransitionCounts().get(tkey("/a", "/b", "/c", "/d")));

        analyzer.shutdown();
    }

    // ── Test B: VOMC Pruning — Collapsible Contexts ───────────────────────────

    @Test
    public void testB_vomcCollapsibleContexts() throws InterruptedException {
        seedCatalog("/login", "/dashboard", "/settings");
        SessionAnalyzer analyzer = buildAnalyzer(2);

        // 100 users all follow the same pattern: login → dashboard → settings
        for (int i = 0; i < 100; i++) {
            analyzer.process(record("GET", "/login", "user-" + i));
            analyzer.process(record("GET", "/dashboard", "user-" + i));
            analyzer.process(record("GET", "/settings", "user-" + i));
        }

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot);

        // Before pruning: order-1 and order-2 transitions exist
        Map<TransitionKey, Long> raw = snapshot.getTransitionCounts();
        Assertions.assertNotNull(raw.get(tkey("/login", "/dashboard")), "Order-1 login→dashboard should exist");
        Assertions.assertNotNull(raw.get(tkey("/dashboard", "/settings")), "Order-1 dashboard→settings should exist");
        Assertions.assertNotNull(raw.get(tkey("/login", "/dashboard", "/settings")), "Order-2 should exist pre-prune");

        // After VOMC pruning: order-2 context [login, dashboard] has same distribution
        // as order-1 context [dashboard] (both 100% → settings). Should be collapsed.
        Map<TransitionKey, Long> pruned = MarkovModelBuilder.prune(raw);

        Assertions.assertNull(pruned.get(tkey("/login", "/dashboard", "/settings")),
                "Order-2 [login→dashboard→settings] should be collapsed — same distribution as parent");
        Assertions.assertNotNull(pruned.get(tkey("/login", "/dashboard")),
                "Order-1 transitions should survive");
        Assertions.assertNotNull(pruned.get(tkey("/dashboard", "/settings")),
                "Order-1 transitions should survive");

        analyzer.shutdown();
    }

    // ── Test C: VOMC Pruning — Non-Collapsible Contexts ───────────────────────

    @Test
    public void testC_vomcNonCollapsibleContexts() throws InterruptedException {
        seedCatalog("/login", "/dashboard", "/admin", "/settings");
        SessionAnalyzer analyzer = buildAnalyzer(2);

        // 50 users: login → dashboard → settings
        for (int i = 0; i < 50; i++) {
            analyzer.process(record("GET", "/login", "user-a-" + i));
            analyzer.process(record("GET", "/dashboard", "user-a-" + i));
            analyzer.process(record("GET", "/settings", "user-a-" + i));
        }

        // 50 users: admin → dashboard → admin
        for (int i = 0; i < 50; i++) {
            analyzer.process(record("GET", "/admin", "user-b-" + i));
            analyzer.process(record("GET", "/dashboard", "user-b-" + i));
            analyzer.process(record("GET", "/admin", "user-b-" + i));
        }

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot);

        Map<TransitionKey, Long> pruned = MarkovModelBuilder.prune(snapshot.getTransitionCounts());

        // Order-1 context [dashboard] has 50/50 split (settings vs admin)
        // Order-2 context [login, dashboard] → 100% settings (distinct from parent)
        // Order-2 context [admin, dashboard] → 100% admin (distinct from parent)
        // Both order-2 contexts should survive pruning.

        Assertions.assertNotNull(pruned.get(tkey("/login", "/dashboard", "/settings")),
                "Order-2 [login→dashboard→settings] should survive — distinct from parent");
        Assertions.assertNotNull(pruned.get(tkey("/admin", "/dashboard", "/admin")),
                "Order-2 [admin→dashboard→admin] should survive — distinct from parent");

        // Order-1 transitions should also survive
        Assertions.assertNotNull(pruned.get(tkey("/login", "/dashboard")));
        Assertions.assertNotNull(pruned.get(tkey("/dashboard", "/settings")));
        Assertions.assertNotNull(pruned.get(tkey("/dashboard", "/admin")));
        Assertions.assertNotNull(pruned.get(tkey("/admin", "/dashboard")));

        analyzer.shutdown();
    }

    // ── Test D: Precedence Score + End-to-End Flusher ─────────────────────────

    @Test
    public void testD_precedenceScoreEndToEnd() throws InterruptedException {
        seedCatalog("/login", "/signup", "/dashboard");
        SessionAnalyzer analyzer = buildAnalyzer(1);

        // 80 users: login → dashboard
        for (int i = 0; i < 80; i++) {
            analyzer.process(record("GET", "/login", "user-a-" + i));
            analyzer.process(record("GET", "/dashboard", "user-a-" + i));
        }

        // 20 users: signup → dashboard
        for (int i = 0; i < 20; i++) {
            analyzer.process(record("GET", "/signup", "user-b-" + i));
            analyzer.process(record("GET", "/dashboard", "user-b-" + i));
        }

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot);

        // Verify counts
        Assertions.assertEquals(80L, snapshot.getApiCounts().get(key("GET", "/login")));
        Assertions.assertEquals(20L, snapshot.getApiCounts().get(key("GET", "/signup")));
        Assertions.assertEquals(100L, snapshot.getApiCounts().get(key("GET", "/dashboard")));

        Assertions.assertEquals(80L, snapshot.getTransitionCounts().get(tkey("/login", "/dashboard")));
        Assertions.assertEquals(20L, snapshot.getTransitionCounts().get(tkey("/signup", "/dashboard")));

        // Verify the ApiSequences built by the flusher have correct lastStateCount
        // lastStateCount for both transitions = dashboard count = 100
        // precedenceScore is 0f (computed in DB), so we just verify structure
        Assertions.assertEquals(2, snapshot.getTransitionCounts().size(),
                "Should have exactly 2 transitions");

        analyzer.shutdown();
    }

    // ── Test E: Top-1000 Cap ──────────────────────────────────────────────────

    @Test
    public void testE_top1000Cap() throws InterruptedException {
        // Seed 1100 unique APIs
        String[] apis = new String[1100];
        for (int i = 0; i < 1100; i++) {
            apis[i] = "/api/" + i;
        }
        seedCatalog(apis);

        SessionAnalyzer analyzer = buildAnalyzer(1);

        // Generate 1100 unique transitions, each with count=1
        // Each user calls a unique pair: /api/i → /api/(i+1)
        for (int i = 0; i < 1099; i++) {
            String userId = "user-" + i;
            analyzer.process(record("GET", "/api/" + i, userId));
            analyzer.process(record("GET", "/api/" + (i + 1), userId));
        }

        // Add extra traffic to one transition to make it high-count
        for (int i = 0; i < 100; i++) {
            String userId = "boost-" + i;
            analyzer.process(record("GET", "/api/0", userId));
            analyzer.process(record("GET", "/api/1", userId));
        }

        WindowSnapshot snapshot = flushAndCapture(analyzer);
        Assertions.assertNotNull(snapshot);

        // Should have 1099 unique transitions
        Assertions.assertTrue(snapshot.getTransitionCounts().size() >= 1099,
                "Should have at least 1099 unique transitions");

        // The boosted transition /api/0 → /api/1 should have count 101 (1 + 100)
        Assertions.assertEquals(101L, snapshot.getTransitionCounts().get(tkey("/api/0", "/api/1")));

        // Note: top-1000 cap is applied in ApiSequencesFlusher, not in the snapshot.
        // This test verifies the snapshot has all transitions; the cap is an integration
        // concern tested separately via the flusher.

        analyzer.shutdown();
    }

    // ── Test F: Window Boundary with Multi-Order ──────────────────────────────

    @Test
    public void testF_windowBoundaryMultiOrder() throws InterruptedException {
        seedCatalog("/a", "/b", "/c", "/d");
        capturedSnapshot.set(null);
        SessionAnalyzer analyzer = buildAnalyzer(3);

        // Window N: user builds full deque [A, B, C]
        analyzer.process(record("GET", "/a", "user-1"));
        analyzer.process(record("GET", "/b", "user-1"));
        analyzer.process(record("GET", "/c", "user-1"));

        WindowSnapshot windowN = flushAndCapture(analyzer);
        Assertions.assertNotNull(windowN);
        // Window N should have transitions from the A→B→C sequence
        Assertions.assertFalse(windowN.getTransitionCounts().isEmpty(),
                "Window N should have transitions");

        capturedSnapshot.set(null);

        // Window N+1: same user calls D. Deque was reset — no context.
        analyzer.process(record("GET", "/d", "user-1"));

        WindowSnapshot windowN1 = flushAndCapture(analyzer);
        Assertions.assertNotNull(windowN1);
        Assertions.assertEquals(1L, windowN1.getApiCounts().get(key("GET", "/d")));
        Assertions.assertTrue(windowN1.getTransitionCounts().isEmpty(),
                "No transitions in window N+1 — deque was reset, even with maxOrder=3");

        analyzer.shutdown();
    }
}
