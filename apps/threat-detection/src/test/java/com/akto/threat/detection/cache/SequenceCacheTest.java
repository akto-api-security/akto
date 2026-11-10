package com.akto.threat.detection.cache;

import com.akto.data_actor.DataActor;
import com.akto.dto.ApiSequences;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.URLMethods.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SequenceCacheTest {

    private static final int COLLECTION = 1001;
    // ApiInfoKey.toString() format: "{collectionId} {url} {method}"
    private static final String SEARCH = "1001 /stations/search POST";
    private static final String VIEW = "1001 /stations/view POST";
    private static final String UPDATE = "1001 /stations/update POST";

    private DataActor mockDataActor;

    @BeforeEach
    public void setUp() {
        mockDataActor = mock(DataActor.class);
    }

    private SequenceCache buildCache(List<ApiSequences> rules) {
        when(mockDataActor.fetchSequenceRules()).thenReturn(rules);
        SequenceCache c = new SequenceCache(mockDataActor);
        c.refresh();
        return c;
    }

    private static ApiSequences rule(String... paths) {
        return new ApiSequences(COLLECTION, Arrays.asList(paths), 100, 100, 100, 1f, 1f);
    }

    private static HttpResponseParams request(String session) {
        Map<String, List<String>> headers = new HashMap<>();
        if (session != null) headers.put("cookie", Collections.singletonList("coulomb_sess=" + session));
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(new HttpRequestParams());
        params.getRequestParams().setHeaders(headers);
        return params;
    }

    private static boolean call(SequenceCache cache, String session, String apiKey) {
        String[] parts = apiKey.split(" ");
        return cache.checkSequenceAnomaly(request(session), Integer.parseInt(parts[0]), parts[1], Method.fromString(parts[2]));
    }

    @Test
    public void requiredStepPresent_noAlert() {
        SequenceCache cache = buildCache(Collections.singletonList(rule(VIEW, UPDATE)));
        assertFalse(call(cache, "s1", VIEW));
        assertFalse(call(cache, "s1", UPDATE));
    }

    @Test
    public void requiredStepMissing_alertsOncePerSession() {
        SequenceCache cache = buildCache(Collections.singletonList(rule(VIEW, UPDATE)));
        assertTrue(call(cache, "s1", UPDATE));
        assertFalse(call(cache, "s1", UPDATE));
        assertTrue(call(cache, "s2", UPDATE));
    }

    @Test
    public void requiredStepFromAnotherSession_stillAlerts() {
        SequenceCache cache = buildCache(Collections.singletonList(rule(VIEW, UPDATE)));
        assertFalse(call(cache, "s1", VIEW));
        assertTrue(call(cache, "s2", UPDATE));
    }

    @Test
    public void unrelatedStepsInBetween_noAlert() {
        SequenceCache cache = buildCache(Collections.singletonList(rule(VIEW, UPDATE)));
        assertFalse(call(cache, "s1", VIEW));
        assertFalse(call(cache, "s1", "1001 /menu GET"));
        assertFalse(call(cache, "s1", UPDATE));
    }

    @Test
    public void twoStepRequirement() {
        SequenceCache cache = buildCache(Collections.singletonList(rule(SEARCH, VIEW, UPDATE)));
        assertFalse(call(cache, "s1", SEARCH));
        assertTrue(call(cache, "s1", UPDATE));

        assertFalse(call(cache, "s2", SEARCH));
        assertFalse(call(cache, "s2", VIEW));
        assertFalse(call(cache, "s2", UPDATE));
    }

    @Test
    public void noSession_noAlert() {
        SequenceCache cache = buildCache(Collections.singletonList(rule(VIEW, UPDATE)));
        assertFalse(call(cache, null, UPDATE));
    }

    @Test
    public void noRules_noAlert() {
        SequenceCache cache = buildCache(new ArrayList<ApiSequences>());
        assertEquals(0, cache.size());
        assertFalse(call(cache, "s1", UPDATE));
    }
}
