package com.akto.dto;

public enum OwaspAstCategory {

    AST01("Malicious Skills", "CRITICAL"),
    AST02("Supply Chain Compromise", "CRITICAL"),
    AST03("Over-Privileged Skills", "HIGH"),
    AST04("Insecure Metadata", "HIGH"),
    AST05("Unsafe Deserialization", "HIGH"),
    AST06("Weak Isolation", "HIGH"),
    AST07("Update Drift", "MEDIUM"),
    AST08("Poor Scanning", "MEDIUM"),
    AST09("No Governance", "MEDIUM"),
    AST10("Cross-Platform Reuse", "MEDIUM");

    private final String displayName;
    private final String severity;

    OwaspAstCategory(String displayName, String severity) {
        this.displayName = displayName;
        this.severity = severity;
    }

    public String getDisplayName() { return displayName; }
    public String getSeverity() { return severity; }
    public String getId() { return this.name(); }

    public static OwaspAstCategory fromId(String id) {
        if (id == null) return null;
        try {
            return OwaspAstCategory.valueOf(id.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
