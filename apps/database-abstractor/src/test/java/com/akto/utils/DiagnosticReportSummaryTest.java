package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class DiagnosticReportSummaryTest {

    @Test
    public void macReport() {
        String report = String.join("\n",
                "[akto-diag] phase=install scope=full outcome=failed",
                "========================================",
                "  INSTALL HEALTH VERDICT (read this first)",
                "========================================",
                "  ❌ Agent: NOT running in gui/501",
                "  ❌ VERDICT: CONFIGURED BUT NOT RUNNING — no agent process.",
                "  ⚠️  Proxy not listening",
                "  ❌ Agent: NOT running in gui/501",  // duplicate: counted, listed once
                "    ❌ quoted agent log line",       // deeper indent: quoted output
                "[2026] ERROR ❌ something");         // marker not first on the line
        DiagnosticReportSummary s = DiagnosticReportSummary.of(report);
        assertEquals("CONFIGURED BUT NOT RUNNING — no agent process.", s.getVerdict());
        assertEquals(Collections.singletonList("Agent: NOT running in gui/501"), s.getFindings());
        assertEquals(2, s.getErrorCount());
        assertEquals(1, s.getWarningCount());
    }

    @Test
    public void healthyWithoutVerdictLine() {
        DiagnosticReportSummary s = DiagnosticReportSummary.of("  ✅ HEALTHY — all present for u.\r\n  ✅ ok");
        assertEquals("HEALTHY — all present for u.", s.getVerdict());
        assertEquals(0, s.getErrorCount());
    }

    @Test
    public void windowsMarkers() {
        String report = String.join("\r\n",
                "  [OK]   Binary present",
                "  [FAIL] Task MCPEndpointShieldAgent is not running",
                "  [WARN] Proxy port 8080 not listening",
                "  [FAIL] VERDICT: CONFIGURED BUT NOT RUNNING — tasks registered, agent not running.",
                "  [INFO] Suggested fix: …");
        DiagnosticReportSummary s = DiagnosticReportSummary.of(report);
        assertEquals("CONFIGURED BUT NOT RUNNING — tasks registered, agent not running.", s.getVerdict());
        assertEquals(Arrays.asList("Task MCPEndpointShieldAgent is not running"), s.getFindings());
        assertEquals(1, s.getErrorCount());
        assertEquals(1, s.getWarningCount());
    }

    @Test
    public void noVerdict() {
        assertNull(DiagnosticReportSummary.of("plain text\nno markers").getVerdict());
    }

    @Test
    public void findingsCapped() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 15; i++) b.append("  ❌ problem ").append(i).append('\n');
        DiagnosticReportSummary s = DiagnosticReportSummary.of(b.toString());
        assertEquals(DiagnosticReportSummary.MAX_FINDINGS, s.getFindings().size());
        assertEquals(15, s.getErrorCount());
    }
}
