package com.akto.threat.detection.tasks;

import com.akto.threat.detection.ip_api_counter.CmsCounterLayer;
import com.akto.threat.detection.ip_api_counter.ParamEnumerationDetector;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ParamEnumerationDetectorTest {

    private static final int THRESHOLD = 50;
    private static final int WINDOW_SIZE_MINUTES = 5;
    private static final int API_COLLECTION_ID = 1000;
    private static final String METHOD = "GET";

    private ParamEnumerationDetector detector;
    private CmsCounterLayer mockCms;

    @Before
    public void setUp() {
        Map<String, Long> counters = new ConcurrentHashMap<>();

        mockCms = mock(CmsCounterLayer.class);

        when(mockCms.incrementAndQueryRange(anyString(), anyLong(), anyLong(), anyLong()))
            .thenAnswer(inv -> {
                String itemKey = inv.getArgument(0);
                long currentMin = inv.getArgument(1);
                long windowStart = inv.getArgument(2);
                long windowEnd = inv.getArgument(3);

                String incrKey = itemKey + "|" + currentMin;
                counters.merge(incrKey, 1L, Long::sum);

                long total = 0;
                for (long m = windowStart; m <= windowEnd; m++) {
                    total += counters.getOrDefault(itemKey + "|" + m, 0L);
                }
                return total;
            });

        when(mockCms.estimateCountInRange(anyString(), anyLong(), anyLong()))
            .thenAnswer(inv -> {
                String itemKey = inv.getArgument(0);
                long windowStart = inv.getArgument(1);
                long windowEnd = inv.getArgument(2);

                long total = 0;
                for (long m = windowStart; m <= windowEnd; m++) {
                    total += counters.getOrDefault(itemKey + "|" + m, 0L);
                }
                return total;
            });

        detector = new ParamEnumerationDetector(mockCms, THRESHOLD, WINDOW_SIZE_MINUTES);
    }

    @Test
    public void shouldDetectWhenThresholdExceeded() {
        String ip = "1.2.3.4";
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";

        for (int i = 1; i <= 51; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            if (i <= THRESHOLD) {
                assertFalse("Should NOT flag at " + i + " unique values (threshold=" + THRESHOLD + ")", result);
            } else {
                assertTrue("Should flag when exceeding threshold at " + i + " unique values", result);
            }
        }
    }

    @Test
    public void shouldNotDetectWhenBelowThreshold() {
        String ip = "1.2.3.4";
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";

        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            assertFalse("Should NOT flag at " + i + " unique values", result);
        }

        assertEquals(30, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName));
    }

    @Test
    public void shouldNotDetectAtExactThreshold() {
        String ip = "1.2.3.4";
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";

        for (int i = 1; i <= THRESHOLD; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            assertFalse("Should NOT flag at exactly threshold", result);
        }

        assertEquals(THRESHOLD, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName));
    }

    @Test
    public void shouldDeduplicateSameValue() {
        String ip = "1.2.3.4";
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";
        String paramValue = "42";

        for (int i = 0; i < 100; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName, paramValue);
            assertFalse("Same value shouldn't trigger detection", result);
        }

        assertEquals(1, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName));
    }

    @Test
    public void shouldIsolateDifferentIPs() {
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";

        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck("1.1.1.1", API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            assertFalse("IP1 should not be flagged", result);
        }

        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck("2.2.2.2", API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            assertFalse("IP2 should not be flagged", result);
        }

        assertEquals(30, detector.getUniqueCount("1.1.1.1", API_COLLECTION_ID, METHOD, apiTemplate, paramName));
        assertEquals(30, detector.getUniqueCount("2.2.2.2", API_COLLECTION_ID, METHOD, apiTemplate, paramName));
    }

    @Test
    public void shouldIsolateDifferentAPIs() {
        String ip = "1.2.3.4";
        String paramName = "id";

        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, "/users/{id}", paramName, String.valueOf(i));
            assertFalse("/users API should not be flagged", result);
        }

        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, "/orders/{id}", paramName, String.valueOf(i));
            assertFalse("/orders API should not be flagged", result);
        }

        assertEquals(30, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, "/users/{id}", paramName));
        assertEquals(30, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, "/orders/{id}", paramName));
    }

    @Test
    public void shouldIsolateDifferentParams() {
        String ip = "1.2.3.4";
        String apiTemplate = "/users/{userId}/orgs/{orgId}";

        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, "userId", String.valueOf(i));
            assertFalse("userId param should not be flagged", result);
        }

        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, "orgId", String.valueOf(i));
            assertFalse("orgId param should not be flagged", result);
        }

        assertEquals(30, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, apiTemplate, "userId"));
        assertEquals(30, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, apiTemplate, "orgId"));
    }

    @Test
    public void shouldHandleMixedTraffic() {
        String maliciousIp = "6.6.6.6";
        String normalIp = "7.7.7.7";
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";

        for (int i = 1; i <= 5; i++) {
            assertFalse(detector.recordAndCheck(normalIp, API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i)));
        }

        boolean flagged = false;
        for (int i = 1; i <= 60; i++) {
            boolean result = detector.recordAndCheck(maliciousIp, API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            if (result) {
                flagged = true;
                assertTrue("Should only flag after threshold exceeded", i > THRESHOLD);
            }
        }

        assertTrue("Malicious IP should have been flagged", flagged);
        assertEquals(5, detector.getUniqueCount(normalIp, API_COLLECTION_ID, METHOD, apiTemplate, paramName));
        assertEquals(60, detector.getUniqueCount(maliciousIp, API_COLLECTION_ID, METHOD, apiTemplate, paramName));
    }

    @Test
    public void shouldDetectMultipleIPsCrossingThreshold() {
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";
        String[] maliciousIps = {"10.0.0.1", "10.0.0.2", "10.0.0.3"};

        int[] flaggedAt = new int[maliciousIps.length];

        for (int value = 1; value <= 60; value++) {
            for (int ipIdx = 0; ipIdx < maliciousIps.length; ipIdx++) {
                boolean result = detector.recordAndCheck(
                    maliciousIps[ipIdx], API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(value));

                if (result && flaggedAt[ipIdx] == 0) {
                    flaggedAt[ipIdx] = value;
                }
            }
        }

        for (int ipIdx = 0; ipIdx < maliciousIps.length; ipIdx++) {
            assertTrue("IP " + maliciousIps[ipIdx] + " should be flagged after threshold",
                flaggedAt[ipIdx] > THRESHOLD);
            assertEquals("IP " + maliciousIps[ipIdx] + " should have 60 unique values",
                60, detector.getUniqueCount(maliciousIps[ipIdx], API_COLLECTION_ID, METHOD, apiTemplate, paramName));
        }
    }

}
