package com.akto.threat.detection.hyperscan;

import com.akto.dto.test_editor.Category;

import java.util.HashMap;
import java.util.Map;

/**
 * Single source of truth mapping Hyperscan pattern prefixes to YAML filter IDs and categories.
 *
 * To add a new threat category:
 *   1. Add an enum constant with its pattern prefixes, YAML filter ID, category name, and severity.
 *   2. Add corresponding patterns to threat-patterns-example.txt using the prefix.
 *   That's it — no other code changes needed.
 */
public enum ThreatCategory {

    SQL_INJECTION(
            new String[]{"sqli"},
            "SQLInjection", "SQL_INJECTION", "SQL Injection", "HIGH"),

    XSS(
            new String[]{"xss"},
            "XSS", "XSS", "Cross-site Scripting (XSS)", "HIGH"),

    NOSQL_INJECTION(
            new String[]{"nosql"},
            "NoSQLInjection", "NOSQL_INJECTION", "NoSQL Injection", "HIGH"),

    OS_COMMAND_INJECTION(
            new String[]{"os_cmd"},
            "OSCommandInjection", "OS_COMMAND_INJECTION", "OS Command Injection", "HIGH"),

    WINDOWS_COMMAND_INJECTION(
            new String[]{"windows"},
            "WindowsCommandInjection", "COMMAND_INJECTION", "Command Injection", "HIGH"),

    SSRF(
            new String[]{"ssrf"},
            "SSRF", "SSRF", "Server Side Request Forgery (SSRF)", "HIGH"),

    LFI_RFI(
            new String[]{"lfi"},
            "LocalFileInclusionLFIRFI", "LFI_RFI", "Local File Inclusion / Remote File Inclusion", "HIGH"),

    SECURITY_MISCONFIG(
            new String[]{"debug", "version", "stack_trace"},
            "SecurityMisconfig", "SecurityMisconfig", "Security Misconfiguration", "LOW");

    private final String[] patternPrefixes;
    private final String filterId;
    private final String categoryName;
    private final String displayName;
    private final String severity;

    // Prefix → ThreatCategory lookup map, built once at class load
    private static final Map<String, ThreatCategory> PREFIX_MAP = new HashMap<>();

    static {
        for (ThreatCategory tc : values()) {
            for (String prefix : tc.patternPrefixes) {
                PREFIX_MAP.put(prefix, tc);
            }
        }
    }

    ThreatCategory(String[] patternPrefixes, String filterId, String categoryName,
                   String displayName, String severity) {
        this.patternPrefixes = patternPrefixes;
        this.filterId = filterId;
        this.categoryName = categoryName;
        this.displayName = displayName;
        this.severity = severity;
    }

    /**
     * Resolve a full pattern prefix (e.g. "os_cmd_shell_commands") to its ThreatCategory.
     * Tries progressively shorter prefixes until a match is found.
     * Returns null if prefix is null/empty or no matching category is found.
     */
    public static ThreatCategory fromPatternPrefix(String fullPrefix) {
        if (fullPrefix == null || fullPrefix.isEmpty()) return null;

        // Try progressively shorter prefixes: os_cmd_shell_commands → os_cmd_shell → os_cmd → os
        String candidate = fullPrefix;
        while (true) {
            ThreatCategory tc = PREFIX_MAP.get(candidate);
            if (tc != null) return tc;

            int lastUnderscore = candidate.lastIndexOf('_');
            if (lastUnderscore <= 0) break;
            candidate = candidate.substring(0, lastUnderscore);
        }
        return null;
    }

    public String getFilterId() {
        return filterId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSeverity() {
        return severity;
    }

    public Category toYamlCategory() {
        return new Category(categoryName, displayName, displayName);
    }
}
