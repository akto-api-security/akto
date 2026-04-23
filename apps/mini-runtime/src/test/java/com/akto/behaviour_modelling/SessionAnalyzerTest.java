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
    public void setUp() {
        aktoPolicyNew = new AktoPolicyNew();
        capturedSnapshot.set(null);

        Map<URLStatic, PolicyCatalog> strictUrls = new HashMap<>();
        Map<URLTemplate, PolicyCatalog> templateUrls = new HashMap<>();

        addStrict(strictUrls, "GET", "/products");
        addStrict(strictUrls, "POST", "/cart/add");
        addStrict(strictUrls, "POST", "/checkout");

        // /products/INTEGER — registered as a template entry
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
        HttpRequestParams req = new HttpRequestParams(method, url, "", new HashMap<>(), "", COLLECTION_ID);
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

    private WindowSnapshot flushAndCapture(SessionAnalyzer analyzer) throws InterruptedException {
        analyzer.triggerWindowEnd();
        // Flush is async — give CompletableFuture a moment to land.
        Thread.sleep(100);
        return capturedSnapshot.get();
    }

    private ApiInfoKey key(String method, String url) {
        return new ApiInfoKey(COLLECTION_ID, url, URLMethods.Method.fromString(method));
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

        Assertions.assertTrue(snapshot.getTransitionCounts().isEmpty(),
                "No transitions expected — only one unique API seen");

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
        Assertions.assertTrue(windowN1.getTransitionCounts().isEmpty(),
                "No cross-window transition expected — deque must reset on window boundary");

        analyzer.shutdown();
    }
}
