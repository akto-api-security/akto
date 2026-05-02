package com.akto.threat.detection.cache;

import com.akto.data_actor.DataActor;
import com.akto.dto.ApiSequences;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SequenceCacheTest {

    private static final String ACTOR_A = "192.168.1.1";
    private static final String ACTOR_B = "192.168.1.2";

    // ApiInfoKey.toString() format: "{collectionId} {url} {method}"
    private static final String API_LOGIN    = "1001 /login POST";
    private static final String API_DASH     = "1001 /dashboard GET";
    private static final String API_SETTINGS = "1001 /settings GET";
    private static final String API_UNKNOWN  = "1001 /admin/export POST";

    private DataActor mockDataActor;
    private SequenceCache cache;

    @Before
    public void setUp() {
        mockDataActor = mock(DataActor.class);
    }

    // Builds a cache seeded with at least MIN_CACHE_SIZE (50) entries.
    // The specific transition probabilities passed in are added on top of padding.
    private SequenceCache buildCache(List<ApiSequences> specific) {
        List<ApiSequences> sequences = new ArrayList<>(specific);

        // Pad to meet MIN_CACHE_SIZE with dummy unique transitions
        for (int i = sequences.size(); i < 50; i++) {
            sequences.add(seq("1001 /pad/" + i + " GET", "1001 /pad/" + (i + 1) + " GET", 0.9f));
        }

        when(mockDataActor.fetchApiSequences()).thenReturn(sequences);
        SequenceCache c = new SequenceCache(mockDataActor);
        c.refresh();
        return c;
    }

    private ApiSequences seq(String prev, String current, float probability) {
        return new ApiSequences(1001, Arrays.asList(prev, current), 10, 10, 10, 0f, probability);
    }

    // -----------------------------------------------------------------------

    @Test
    public void normalTransition_noAlert() {
        cache = buildCache(Arrays.asList(seq(API_LOGIN, API_DASH, 0.8f)));

        assertFalse(cache.checkSequenceAnomaly(ACTOR_A, API_LOGIN));
        assertFalse(cache.checkSequenceAnomaly(ACTOR_A, API_DASH));
    }

    @Test
    public void singleAnomalousTransition_noAlert() {
        cache = buildCache(Arrays.asList(seq(API_LOGIN, API_DASH, 0.8f)));

        assertFalse(cache.checkSequenceAnomaly(ACTOR_A, API_LOGIN));
        // API_UNKNOWN is not in cache — anomalous, but count=1 < threshold=5
        assertFalse(cache.checkSequenceAnomaly(ACTOR_A, API_UNKNOWN));
    }

    @Test
    public void fourConsecutiveAnomalous_noAlert() {
        cache = buildCache(new ArrayList<>());

        String[] apis = {API_LOGIN, API_DASH, API_SETTINGS, API_UNKNOWN, "1001 /x GET"};
        cache.checkSequenceAnomaly(ACTOR_A, apis[0]);
        for (int i = 1; i < apis.length; i++) {
            assertFalse("should not alert on transition " + i,
                    cache.checkSequenceAnomaly(ACTOR_A, apis[i]));
        }
        // 4 anomalous transitions made (none of these pairs are seeded), none should alert
    }

    @Test
    public void fiveConsecutiveAnomalous_alertOnFifth() {
        cache = buildCache(new ArrayList<>());

        String[] apis = {API_LOGIN, API_DASH, API_SETTINGS, API_UNKNOWN,
                "1001 /x GET", "1001 /y GET"};

        cache.checkSequenceAnomaly(ACTOR_A, apis[0]);
        for (int i = 1; i < apis.length - 1; i++) {
            assertFalse("should not alert yet on transition " + i,
                    cache.checkSequenceAnomaly(ACTOR_A, apis[i]));
        }
        // 5th anomalous transition
        assertTrue(cache.checkSequenceAnomaly(ACTOR_A, apis[apis.length - 1]));
    }

    @Test
    public void normalTransitionDoesNotResetCounter() {
        // Seed only LOGIN→DASH as known; everything else is unknown
        cache = buildCache(Arrays.asList(seq(API_LOGIN, API_DASH, 0.8f)));

        // 4 anomalous
        cache.checkSequenceAnomaly(ACTOR_A, API_UNKNOWN);
        cache.checkSequenceAnomaly(ACTOR_A, "1001 /x GET");
        cache.checkSequenceAnomaly(ACTOR_A, "1001 /y GET");
        cache.checkSequenceAnomaly(ACTOR_A, "1001 /z GET");

        // 1 normal transition (LOGIN→DASH is known) — should NOT reset counter
        cache.checkSequenceAnomaly(ACTOR_A, API_LOGIN);
        assertFalse(cache.checkSequenceAnomaly(ACTOR_A, API_DASH));

        // 1 more anomalous — should be 5th total, fires alert
        assertTrue(cache.checkSequenceAnomaly(ACTOR_A, API_UNKNOWN));
    }

    @Test
    public void afterAlert_counterResets_needsFullNAgain() {
        cache = buildCache(new ArrayList<>());

        String[] apis = {"1001 /a GET", "1001 /b GET", "1001 /c GET",
                "1001 /d GET", "1001 /e GET", "1001 /f GET"};

        cache.checkSequenceAnomaly(ACTOR_A, apis[0]);
        // Drive 5 anomalous → alert
        for (int i = 1; i <= 5; i++) {
            cache.checkSequenceAnomaly(ACTOR_A, apis[i % apis.length]);
        }

        // Now drive 4 more anomalous — should not alert (counter was reset)
        cache.checkSequenceAnomaly(ACTOR_A, "1001 /p GET");
        assertFalse(cache.checkSequenceAnomaly(ACTOR_A, "1001 /q GET"));
        assertFalse(cache.checkSequenceAnomaly(ACTOR_A, "1001 /r GET"));
        assertFalse(cache.checkSequenceAnomaly(ACTOR_A, "1001 /s GET"));
    }

    @Test
    public void firstRequestPerActor_noCheck() {
        cache = buildCache(new ArrayList<>());

        // No prior state — prevApiKey is null, no anomaly check possible
        assertFalse(cache.checkSequenceAnomaly(ACTOR_A, API_LOGIN));
    }

    @Test
    public void twoActors_independent() {
        cache = buildCache(new ArrayList<>());

        // Actor A makes 5 anomalous transitions
        cache.checkSequenceAnomaly(ACTOR_A, "1001 /a GET");
        cache.checkSequenceAnomaly(ACTOR_A, "1001 /b GET");
        cache.checkSequenceAnomaly(ACTOR_A, "1001 /c GET");
        cache.checkSequenceAnomaly(ACTOR_A, "1001 /d GET");
        cache.checkSequenceAnomaly(ACTOR_A, "1001 /e GET");
        boolean actorAAlert = cache.checkSequenceAnomaly(ACTOR_A, "1001 /f GET");

        // Actor B makes only 3 anomalous transitions (interleaved)
        cache.checkSequenceAnomaly(ACTOR_B, "1001 /x GET");
        cache.checkSequenceAnomaly(ACTOR_B, "1001 /y GET");
        boolean actorBAlert = cache.checkSequenceAnomaly(ACTOR_B, "1001 /z GET");

        assertTrue(actorAAlert);
        assertFalse(actorBAlert);
    }

    @Test
    public void belowMinCacheSize_detectionSkipped() {
        // Only 5 sequences — below MIN_CACHE_SIZE (50), detection is skipped entirely
        List<ApiSequences> small = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            small.add(seq("1001 /a" + i + " GET", "1001 /b" + i + " GET", 0.9f));
        }
        when(mockDataActor.fetchApiSequences()).thenReturn(small);
        cache = new SequenceCache(mockDataActor);
        cache.refresh();

        // Drive many anomalous transitions — should never alert
        cache.checkSequenceAnomaly(ACTOR_A, "1001 /unknown1 GET");
        for (int i = 0; i < 10; i++) {
            assertFalse(cache.checkSequenceAnomaly(ACTOR_A, "1001 /unknown" + (i + 2) + " GET"));
        }
    }

    @Test
    public void lowProbability_treatedAsAnomalous() {
        // LOGIN→DASH prob=0.03 < threshold — anomalous
        // DASH→LOGIN prob=0.9 — known, so the return trip doesn't count toward anomaly
        cache = buildCache(Arrays.asList(
                seq(API_LOGIN, API_DASH, 0.03f),
                seq(API_DASH, API_LOGIN, 0.9f)
        ));

        cache.checkSequenceAnomaly(ACTOR_A, API_LOGIN);
        // 4 LOGIN→DASH transitions (anomalous), return trip is known (not anomalous)
        for (int i = 0; i < 4; i++) {
            assertFalse(cache.checkSequenceAnomaly(ACTOR_A, API_DASH));
            cache.checkSequenceAnomaly(ACTOR_A, API_LOGIN); // DASH→LOGIN: known, doesn't count
        }
        // 5th LOGIN→DASH: fires alert
        assertTrue(cache.checkSequenceAnomaly(ACTOR_A, API_DASH));
    }

    @Test
    public void probabilityAtThreshold_notAnomalous() {
        // LOGIN→DASH prob=0.05 == threshold — condition is strict <, so NOT anomalous
        // DASH→LOGIN prob=0.9 — known, return trip also not anomalous
        cache = buildCache(Arrays.asList(
                seq(API_LOGIN, API_DASH, 0.05f),
                seq(API_DASH, API_LOGIN, 0.9f)
        ));

        cache.checkSequenceAnomaly(ACTOR_A, API_LOGIN);
        for (int i = 0; i < 10; i++) {
            assertFalse(cache.checkSequenceAnomaly(ACTOR_A, API_DASH));
            cache.checkSequenceAnomaly(ACTOR_A, API_LOGIN);
        }
    }

    @Test
    public void interleavedActors_countersStayIsolated() {
        cache = buildCache(new ArrayList<>());

        // Interleave Actor A and Actor B transitions
        String[] aApis = {"1001 /a1 GET", "1001 /a2 GET", "1001 /a3 GET",
                "1001 /a4 GET", "1001 /a5 GET", "1001 /a6 GET"};
        String[] bApis = {"1001 /b1 GET", "1001 /b2 GET", "1001 /b3 GET",
                "1001 /b4 GET", "1001 /b5 GET", "1001 /b6 GET"};

        cache.checkSequenceAnomaly(ACTOR_A, aApis[0]);
        cache.checkSequenceAnomaly(ACTOR_B, bApis[0]);

        boolean aAlerted = false;
        boolean bAlerted = false;
        for (int i = 1; i < aApis.length; i++) {
            aAlerted |= cache.checkSequenceAnomaly(ACTOR_A, aApis[i]);
            bAlerted |= cache.checkSequenceAnomaly(ACTOR_B, bApis[i]);
        }

        // Both made 5 anomalous transitions — both should have alerted exactly once
        assertTrue(aAlerted);
        assertTrue(bAlerted);
    }
}
