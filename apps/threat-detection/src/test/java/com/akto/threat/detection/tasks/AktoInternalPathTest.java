package com.akto.threat.detection.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AktoInternalPathTest {

    @Test
    public void testAktoOwnCallsAreSkipped() {
        assertTrue(MaliciousTrafficDetectorTask.isAktoInternalPath("/api/dashboard/get_threat_configuration"));
        assertTrue(MaliciousTrafficDetectorTask.isAktoInternalPath("/api/threat_detection/record_malicious_event"));
        assertTrue(MaliciousTrafficDetectorTask.isAktoInternalPath("/api/ingestData"));
        assertTrue(MaliciousTrafficDetectorTask.isAktoInternalPath("/api/ingestData?batch=1"));
        assertTrue(MaliciousTrafficDetectorTask.isAktoInternalPath("https://tbs.akto.io/api/threat_detection/update_remediation"));
        assertTrue(MaliciousTrafficDetectorTask.isAktoInternalPath("http://10.0.0.5:9090/api/dashboard/list_threat_actors?limit=10"));
    }

    @Test
    public void testClientEndpointsAreChecked() {
        // A client's own endpoint named like an Akto route was never checked for threats.
        assertFalse(MaliciousTrafficDetectorTask.isAktoInternalPath("/api/dashboard"));
        assertFalse(MaliciousTrafficDetectorTask.isAktoInternalPath("/api/dashboard?id=1"));
        assertFalse(MaliciousTrafficDetectorTask.isAktoInternalPath("https://api.example.com/api/dashboard"));
        assertFalse(MaliciousTrafficDetectorTask.isAktoInternalPath("/v1/api/dashboard/summary"));
        assertFalse(MaliciousTrafficDetectorTask.isAktoInternalPath("/api/dashboards"));
        assertFalse(MaliciousTrafficDetectorTask.isAktoInternalPath("/reports/api/threat_detection/export"));
        assertFalse(MaliciousTrafficDetectorTask.isAktoInternalPath("https://api.example.com"));
        assertFalse(MaliciousTrafficDetectorTask.isAktoInternalPath(""));
        assertFalse(MaliciousTrafficDetectorTask.isAktoInternalPath(null));
    }
}
