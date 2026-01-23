package com.akto.threat.detection.tasks;

import com.akto.threat.detection.ip_api_counter.ParamEnumerationDetector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ParamEnumerationDetectorTest {

    private static final int THRESHOLD = 50;
    private static final int WINDOW_SIZE_MINUTES = 5;
    private static final int API_COLLECTION_ID = 1000;
    private static final String METHOD = "GET";

    private ParamEnumerationDetector detector;

    @BeforeEach
    void setUp() {
        // Initialize with null cache (in-memory only for tests), threshold=50, window=5min
        ParamEnumerationDetector.initialize(null, THRESHOLD, WINDOW_SIZE_MINUTES);
        detector = ParamEnumerationDetector.getInstance();
        detector.reset();
    }

    @Test
    void shouldDetectWhenThresholdExceeded() {
        String ip = "1.2.3.4";
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";

        // Hit 51 unique values - should flag on 51st
        for (int i = 1; i <= 51; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            if (i <= THRESHOLD) {
                assertFalse(result, "Should NOT flag at " + i + " unique values (threshold=" + THRESHOLD + ")");
            } else {
                assertTrue(result, "Should flag when exceeding threshold at " + i + " unique values");
            }
        }
    }

    @Test
    void shouldNotDetectWhenBelowThreshold() {
        String ip = "1.2.3.4";
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";

        // Hit 30 unique values - should never flag
        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            assertFalse(result, "Should NOT flag at " + i + " unique values");
        }

        assertEquals(30, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName));
    }

    @Test
    void shouldNotDetectAtExactThreshold() {
        String ip = "1.2.3.4";
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";

        // Hit exactly 50 unique values - should NOT flag (> not >=)
        for (int i = 1; i <= THRESHOLD; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            assertFalse(result, "Should NOT flag at exactly threshold");
        }

        assertEquals(THRESHOLD, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName));
    }

    @Test
    void shouldDeduplicateSameValue() {
        String ip = "1.2.3.4";
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";
        String paramValue = "42";

        // Hit same value 100 times - should count as 1
        for (int i = 0; i < 100; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName, paramValue);
            assertFalse(result, "Same value shouldn't trigger detection");
        }

        assertEquals(1, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, apiTemplate, paramName));
    }

    @Test
    void shouldIsolateDifferentIPs() {
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";

        // IP1: 30 unique values
        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck("1.1.1.1", API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            assertFalse(result, "IP1 should not be flagged");
        }

        // IP2: 30 unique values (separate tracking)
        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck("2.2.2.2", API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            assertFalse(result, "IP2 should not be flagged");
        }

        // Verify counts are separate
        assertEquals(30, detector.getUniqueCount("1.1.1.1", API_COLLECTION_ID, METHOD, apiTemplate, paramName));
        assertEquals(30, detector.getUniqueCount("2.2.2.2", API_COLLECTION_ID, METHOD, apiTemplate, paramName));
    }

    @Test
    void shouldIsolateDifferentAPIs() {
        String ip = "1.2.3.4";
        String paramName = "id";

        // 30 values on /users/{id}
        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, "/users/{id}", paramName, String.valueOf(i));
            assertFalse(result, "/users API should not be flagged");
        }

        // 30 values on /orders/{id}
        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, "/orders/{id}", paramName, String.valueOf(i));
            assertFalse(result, "/orders API should not be flagged");
        }

        // Verify counts are separate
        assertEquals(30, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, "/users/{id}", paramName));
        assertEquals(30, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, "/orders/{id}", paramName));
    }

    @Test
    void shouldIsolateDifferentParams() {
        String ip = "1.2.3.4";
        String apiTemplate = "/users/{userId}/orgs/{orgId}";

        // 30 values for userId
        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, "userId", String.valueOf(i));
            assertFalse(result, "userId param should not be flagged");
        }

        // 30 values for orgId
        for (int i = 1; i <= 30; i++) {
            boolean result = detector.recordAndCheck(ip, API_COLLECTION_ID, METHOD, apiTemplate, "orgId", String.valueOf(i));
            assertFalse(result, "orgId param should not be flagged");
        }

        // Verify counts are separate
        assertEquals(30, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, apiTemplate, "userId"));
        assertEquals(30, detector.getUniqueCount(ip, API_COLLECTION_ID, METHOD, apiTemplate, "orgId"));
    }

    @Test
    void shouldHandleMixedTraffic() {
        // Simulate realistic mixed traffic
        String maliciousIp = "6.6.6.6";
        String normalIp = "7.7.7.7";
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";

        // Normal user: hits 5 unique values
        for (int i = 1; i <= 5; i++) {
            assertFalse(detector.recordAndCheck(normalIp, API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i)));
        }

        // Malicious user: enumerates 60 values
        boolean flagged = false;
        for (int i = 1; i <= 60; i++) {
            boolean result = detector.recordAndCheck(maliciousIp, API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(i));
            if (result) {
                flagged = true;
                assertTrue(i > THRESHOLD, "Should only flag after threshold exceeded");
            }
        }

        assertTrue(flagged, "Malicious IP should have been flagged");
        assertEquals(5, detector.getUniqueCount(normalIp, API_COLLECTION_ID, METHOD, apiTemplate, paramName));
        assertEquals(60, detector.getUniqueCount(maliciousIp, API_COLLECTION_ID, METHOD, apiTemplate, paramName));
    }

    @Test
    void shouldDetectMultipleIPsCrossingThreshold() {
        String apiTemplate = "/users/{userId}";
        String paramName = "userId";
        String[] maliciousIps = {"10.0.0.1", "10.0.0.2", "10.0.0.3"};

        int[] flaggedAt = new int[maliciousIps.length];

        // All 3 IPs enumerate 60 values each (interleaved)
        for (int value = 1; value <= 60; value++) {
            for (int ipIdx = 0; ipIdx < maliciousIps.length; ipIdx++) {
                boolean result = detector.recordAndCheck(
                    maliciousIps[ipIdx], API_COLLECTION_ID, METHOD, apiTemplate, paramName, String.valueOf(value));

                if (result && flaggedAt[ipIdx] == 0) {
                    flaggedAt[ipIdx] = value;
                }
            }
        }

        // All 3 IPs should be flagged after threshold
        for (int ipIdx = 0; ipIdx < maliciousIps.length; ipIdx++) {
            assertTrue(flaggedAt[ipIdx] > THRESHOLD,
                "IP " + maliciousIps[ipIdx] + " should be flagged after threshold");
            assertEquals(60, detector.getUniqueCount(maliciousIps[ipIdx], API_COLLECTION_ID, METHOD, apiTemplate, paramName),
                "IP " + maliciousIps[ipIdx] + " should have 60 unique values");
        }
    }

}
