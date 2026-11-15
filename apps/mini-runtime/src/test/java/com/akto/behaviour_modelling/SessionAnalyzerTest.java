package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.core.UserIdentifier;
import com.akto.data_actor.DataActorFactory;
import com.akto.data_actor.DbActor;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfoCatalog;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.PolicyCatalog;
import com.akto.dto.SequenceCount;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.hybrid_runtime.policies.AktoPolicyNew;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class SessionAnalyzerTest {

    private static final int COLLECTION_ID = 1001;
    private static final int HOUR_START = 1_760_000_400; // multiple of 3600
    private static final long DAY_MS = 24 * 3600 * 1000L;

    private AktoPolicyNew aktoPolicyNew;
    private final List<SequenceCount> sent = new ArrayList<>();
    private final AtomicLong clockMs = new AtomicLong(HOUR_START * 1000L);
    private SessionAnalyzer analyzer;

    private final UserIdentifier ipIdentifier = HttpResponseParams::getSourceIP;

    // APICatalogSync's static init reads deactivated collections through the DataActor; keep it offline.
    @BeforeAll
    public static void stubDataActor() throws Exception {
        Field instance = DataActorFactory.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, new DbActor() {
            @Override
            public List<Integer> fetchDeactivatedCollections() {
                return new ArrayList<>();
            }

            @Override
            public ApiCollection findApiCollectionByName(String name) {
                return null;
            }
        });
    }

    @BeforeEach
    public void setUp() {
        aktoPolicyNew = new AktoPolicyNew();
        Map<URLStatic, PolicyCatalog> strictUrls = new HashMap<>();
        Map<URLTemplate, PolicyCatalog> templateUrls = new HashMap<>();
        for (String url : Arrays.asList("/a", "/b", "/c", "/d")) {
            addStrict(strictUrls, "GET", url);
        }
        addTemplate(templateUrls, "GET", "/products/123");
        ApiInfoCatalog catalog = new ApiInfoCatalog(strictUrls, templateUrls, new ArrayList<>());
        Map<Integer, ApiInfoCatalog> catalogMap = new HashMap<>();
        catalogMap.put(COLLECTION_ID, catalog);
        aktoPolicyNew.setApiInfoCatalogMap(catalogMap);
        sent.clear();
        analyzer = build(ipIdentifier);
    }

    @AfterEach
    public void tearDown() {
        analyzer.shutdown();
    }

    private SessionAnalyzer build(UserIdentifier identifier) {
        return new SessionAnalyzer(new SequenceAnalyzerConfig(
                SequenceAnalyzerConfig.DEFAULT_N_MAX, SequenceAnalyzerConfig.DEFAULT_MAX_HISTORY,
                SequenceAnalyzerConfig.DEFAULT_SESSION_IDLE_MS, DAY_MS, DAY_MS,
                identifier, sent::addAll, aktoPolicyNew, clockMs::get));
    }

    private static void addStrict(Map<URLStatic, PolicyCatalog> map, String method, String url) {
        URLMethods.Method m = URLMethods.Method.fromString(method);
        map.put(new URLStatic(url, m), new PolicyCatalog(new ApiInfo(new ApiInfoKey(COLLECTION_ID, url, m)), null));
    }

    private static void addTemplate(Map<URLTemplate, PolicyCatalog> map, String method, String rawUrl) {
        URLMethods.Method m = URLMethods.Method.fromString(method);
        URLTemplate tmpl = APICatalogSync.tryParamteresingUrl(new URLStatic(rawUrl, m), false);
        map.put(tmpl, new PolicyCatalog(new ApiInfo(new ApiInfoKey(COLLECTION_ID, tmpl.getTemplateString(), m)), null));
    }

    private static HttpResponseParams record(String url, String sourceIp, int timeSec) {
        HttpRequestParams req = new HttpRequestParams("GET", url, "", new HashMap<>(), "", COLLECTION_ID);
        HttpResponseParams resp = new HttpResponseParams("", 200, "", new HashMap<>(), "", req, timeSec, "0",
                false, HttpResponseParams.Source.OTHER, "", sourceIp);
        resp.setSourceIP(sourceIp);
        return resp;
    }

    private static String k(String url) {
        return new ApiInfoKey(COLLECTION_ID, url, URLMethods.Method.GET).toString();
    }

    private static String seq(int bucket, String next, String... context) {
        List<String> ctx = new ArrayList<>();
        for (String c : context) ctx.add(k(c));
        return bucket + "|" + ctx + "->" + k(next);
    }

    private Map<String, Long> flushed() {
        analyzer.triggerFlush();
        Map<String, Long> out = new HashMap<>();
        for (SequenceCount sc : sent) {
            Assertions.assertEquals(COLLECTION_ID, sc.getApiCollectionId());
            Assertions.assertEquals(0, sc.getBucketStart() % 3600);
            out.merge(sc.getBucketStart() + "|" + sc.getContext() + "->" + sc.getNext(), sc.getCount(), Long::sum);
        }
        sent.clear();
        return out;
    }

    private static Map<String, Long> expected(String... keys) {
        Map<String, Long> m = new HashMap<>();
        for (String key : keys) m.merge(key, 1L, Long::sum);
        return m;
    }

    @Test
    public void countsOrderZeroOneTwo() {
        int t = HOUR_START + 10;
        analyzer.process(record("/a", "1.1.1.1", t));
        analyzer.process(record("/b", "1.1.1.1", t + 1));
        analyzer.process(record("/c", "1.1.1.1", t + 2));

        Assertions.assertEquals(expected(
                seq(HOUR_START, "/a"),
                seq(HOUR_START, "/b"), seq(HOUR_START, "/b", "/a"),
                seq(HOUR_START, "/c"), seq(HOUR_START, "/c", "/b"), seq(HOUR_START, "/c", "/a", "/b")
        ), flushed());
    }

    @Test
    public void consecutiveRepeatsCollapse() {
        int t = HOUR_START + 10;
        analyzer.process(record("/a", "1.1.1.1", t));
        analyzer.process(record("/a", "1.1.1.1", t + 1));
        analyzer.process(record("/products/1", "1.1.1.1", t + 2));
        analyzer.process(record("/products/2", "1.1.1.1", t + 3));
        analyzer.process(record("/b", "1.1.1.1", t + 4));

        Assertions.assertEquals(expected(
                seq(HOUR_START, "/a"),
                seq(HOUR_START, "/products/INTEGER"), seq(HOUR_START, "/products/INTEGER", "/a"),
                seq(HOUR_START, "/b"), seq(HOUR_START, "/b", "/products/INTEGER"),
                seq(HOUR_START, "/b", "/a", "/products/INTEGER")
        ), flushed());
    }

    @Test
    public void unknownApiIgnored() {
        analyzer.process(record("/unknown", "1.1.1.1", HOUR_START + 1));
        Assertions.assertTrue(flushed().isEmpty());
        Assertions.assertEquals(0, analyzer.activeSessionCount());
    }

    @Test
    public void separateSessionsDoNotMix() {
        int t = HOUR_START + 10;
        analyzer.process(record("/a", "1.1.1.1", t));
        analyzer.process(record("/c", "2.2.2.2", t + 1));
        analyzer.process(record("/b", "1.1.1.1", t + 2));
        analyzer.process(record("/d", "2.2.2.2", t + 3));

        Assertions.assertEquals(expected(
                seq(HOUR_START, "/a"), seq(HOUR_START, "/b"), seq(HOUR_START, "/b", "/a"),
                seq(HOUR_START, "/c"), seq(HOUR_START, "/d"), seq(HOUR_START, "/d", "/c")
        ), flushed());
        Assertions.assertEquals(2, analyzer.activeSessionCount());
    }

    @Test
    public void idleGapStartsFreshHistory() {
        int t = HOUR_START + 10;
        analyzer.process(record("/a", "1.1.1.1", t));
        analyzer.process(record("/b", "1.1.1.1", t + 600));
        analyzer.process(record("/c", "1.1.1.1", t + 600 + 601));

        Assertions.assertEquals(expected(
                seq(HOUR_START, "/a"), seq(HOUR_START, "/b"), seq(HOUR_START, "/b", "/a"),
                seq(HOUR_START, "/c")
        ), flushed());
    }

    @Test
    public void evictionRemovesIdleSessions() {
        int t = HOUR_START + 10;
        clockMs.set(t * 1000L);
        analyzer.process(record("/a", "1.1.1.1", t));
        analyzer.process(record("/a", "2.2.2.2", t));
        clockMs.set((t + 700) * 1000L);
        analyzer.process(record("/b", "3.3.3.3", t + 700));

        analyzer.triggerEviction();
        Assertions.assertEquals(1, analyzer.activeSessionCount());

        clockMs.set((t + 700 + 601) * 1000L);
        analyzer.triggerEviction();
        Assertions.assertEquals(0, analyzer.activeSessionCount());
    }

    @Test
    public void evictionFollowsEventTimeDuringBacklog() {
        int t = HOUR_START + 10;
        clockMs.set((t + 5 * 3600) * 1000L);
        analyzer.process(record("/a", "1.1.1.1", t));
        analyzer.triggerEviction();
        Assertions.assertEquals(1, analyzer.activeSessionCount());

        clockMs.addAndGet(601 * 1000L);
        analyzer.triggerEviction();
        Assertions.assertEquals(0, analyzer.activeSessionCount());
    }

    @Test
    public void flushSpansTwoHours() {
        int t = HOUR_START + 3600 - 60;
        int nextHour = HOUR_START + 3600;
        analyzer.process(record("/a", "1.1.1.1", t));
        analyzer.process(record("/b", "1.1.1.1", t + 120));

        Assertions.assertEquals(expected(
                seq(HOUR_START, "/a"),
                seq(nextHour, "/b"), seq(nextHour, "/b", "/a")
        ), flushed());
    }

    @Test
    public void flushResetsCountsAndKeepsHistory() {
        int t = HOUR_START + 10;
        analyzer.process(record("/a", "1.1.1.1", t));
        flushed();
        analyzer.process(record("/b", "1.1.1.1", t + 1));
        Assertions.assertEquals(expected(seq(HOUR_START, "/b"), seq(HOUR_START, "/b", "/a")), flushed());
        Assertions.assertTrue(flushed().isEmpty());
    }

    @Test
    public void noTopNTruncation() {
        int t = HOUR_START + 10;
        for (int i = 0; i < 1500; i++) {
            analyzer.process(record("/a", "10.0." + (i / 256) + "." + (i % 256), t));
        }
        Set<String> sessions = new HashSet<>();
        for (int i = 0; i < 1500; i++) {
            String ip = "10.0." + (i / 256) + "." + (i % 256);
            String[] path = (i % 2 == 0) ? new String[]{"/b", "/c", "/d"} : new String[]{"/c", "/b", "/d"};
            for (String p : path) analyzer.process(record(p, ip, t + 1));
            sessions.add(ip);
        }
        Assertions.assertEquals(1500, sessions.size());
        Map<String, Long> out = flushed();
        Assertions.assertEquals(1500L, out.get(seq(HOUR_START, "/a")));
        Assertions.assertEquals(750L, out.get(seq(HOUR_START, "/d", "/b", "/c")));
        Assertions.assertEquals(750L, out.get(seq(HOUR_START, "/d", "/c", "/b")));

        // > 1000 distinct sequences are all sent
        sent.clear();
        analyzer.shutdown();
        analyzer = build(ipIdentifier);
        int distinct = 0;
        for (int hour = 0; hour < 400; hour++) {
            int ht = HOUR_START + hour * 3600 + 1;
            analyzer.process(record("/a", "1.1.1.1", ht));
            analyzer.process(record("/b", "1.1.1.1", ht + 1));
            analyzer.process(record("/c", "1.1.1.1", ht + 2));
            distinct += 6;
        }
        Assertions.assertEquals(distinct, flushed().size());
    }

    @Test
    public void flushedJsonShape() {
        analyzer.process(record("/a", "1.1.1.1", HOUR_START + 1));
        analyzer.process(record("/b", "1.1.1.1", HOUR_START + 2));
        analyzer.triggerFlush();
        SequenceCount order1 = null;
        for (SequenceCount sc : sent) if (sc.getContext().size() == 1) order1 = sc;
        Assertions.assertNotNull(order1);
        BasicDBObject body = new BasicDBObject("sequenceCounts", Collections.singletonList(order1));
        Assertions.assertEquals("{\"sequenceCounts\":[{\"apiCollectionId\":1001,\"context\":[\"1001 /a GET\"],"
                + "\"next\":\"1001 /b GET\",\"bucketStart\":" + HOUR_START + ",\"count\":1}]}", new Gson().toJson(body));
    }
}
