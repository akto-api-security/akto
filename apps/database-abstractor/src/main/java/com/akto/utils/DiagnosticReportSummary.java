package com.akto.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The summary of an endpoint-shield diagnostic report — verdict, findings and
 * counts — derived from the report text. Computed here, once, so the stored
 * summary follows one set of rules whatever version of the installer or agent
 * sent it (those keep their own copy only for local display: the pointer log
 * line, the on-demand command's output).
 *
 * Markers: diagnose_mac.sh prints "  ❌ " / "  ⚠️ " / "  ✅ "; diagnose_windows.ps1
 * prints "  [FAIL] " / "  [WARN] " / "  [OK] ". A finding is such a line with the
 * marker as the first thing (at most two leading spaces) — deeper-indented
 * markers are quoted log output, not a finding about the device.
 */
public final class DiagnosticReportSummary {

    public static final int MAX_FINDINGS = 10;
    private static final int MAX_FINDING_CHARS = 240;
    private static final int MAX_VERDICT_CHARS = 300;

    private static final Pattern ERROR_LINE = Pattern.compile("^ {0,2}(?:❌|\\[FAIL\\])\\s*(.*)$");
    private static final Pattern WARNING_LINE = Pattern.compile("^ {0,2}(?:⚠️|⚠|\\[WARN\\])");
    private static final Pattern HEALTHY_LINE = Pattern.compile("^ {0,2}(?:✅|\\[OK\\])\\s*(HEALTHY —.*)$");
    private static final String VERDICT = "VERDICT:";

    private final String verdict;
    private final List<String> findings;
    private final int errorCount;
    private final int warningCount;

    private DiagnosticReportSummary(String verdict, List<String> findings, int errorCount, int warningCount) {
        this.verdict = verdict;
        this.findings = findings;
        this.errorCount = errorCount;
        this.warningCount = warningCount;
    }

    public static DiagnosticReportSummary of(String report) {
        String verdict = null;
        String healthy = null;
        Set<String> findings = new LinkedHashSet<>();
        int errors = 0;
        int warnings = 0;
        for (String raw : report.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (verdict == null) {
                int i = line.indexOf(VERDICT);
                if (i >= 0) {
                    verdict = cap(line.substring(i + VERDICT.length()).trim(), MAX_VERDICT_CHARS);
                }
            }
            if (healthy == null) {
                Matcher h = HEALTHY_LINE.matcher(line);
                if (h.matches()) {
                    healthy = cap(h.group(1).trim(), MAX_VERDICT_CHARS);
                }
            }
            if (WARNING_LINE.matcher(line).find()) {
                warnings++;
                continue;
            }
            Matcher e = ERROR_LINE.matcher(line);
            if (e.matches() && !line.contains(VERDICT)) {
                errors++;
                String f = cap(e.group(1).trim(), MAX_FINDING_CHARS);
                if (!f.isEmpty() && findings.size() < MAX_FINDINGS) {
                    findings.add(f);
                }
            }
        }
        return new DiagnosticReportSummary(verdict != null ? verdict : healthy,
                new ArrayList<>(findings), errors, warnings);
    }

    private static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    public String getVerdict() { return verdict; }
    public List<String> getFindings() { return findings; }
    public int getErrorCount() { return errorCount; }
    public int getWarningCount() { return warningCount; }
}
