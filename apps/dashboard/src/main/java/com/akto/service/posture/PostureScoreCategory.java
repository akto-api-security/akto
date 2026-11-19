package com.akto.service.posture;

// The six AgenticPostureScoreCron categories; keys and weights must match that cron's postureSubScores.
enum PostureScoreCategory {

    RED_TEAM("redTeam", "Red teaming", 30,
            "Has open red-teaming findings",
            "Fix or accept the open red-team findings, then re-run the scan to confirm they are closed."),
    GUARDRAIL_MALICIOUS("guardrailMalicious", "Guardrail & malicious activity", 30,
            "Has guardrail-caught or malicious activity",
            "Review the flagged events, block the offending prompts or users, and tighten the policy that fired."),
    COVERAGE("coverage", "Coverage", 10,
            "Not covered by a guardrail policy or red-team scan",
            "Apply a guardrail policy to this agent and schedule a red-team scan against it."),
    SENSITIVE_DATA("sensitiveData", "Sensitive data", 10,
            "Accesses sensitive data",
            "Confirm the agent needs this data; otherwise remove access or add PII/secret redaction in a guardrail."),
    ACCESS_AUTH("accessAuth", "Access & authentication", 10,
            "Publicly accessible or unauthenticated",
            "Put the endpoint behind authentication and restrict it to private network access where possible."),
    OVERPRIVILEGED_TOOLS("overprivilegedTools", "Overprivileged tools", 10,
            "Has privileged tool access",
            "Remove destructive or credential-reading tools the agent doesn't need, or require approval before use.");

    final String key;
    final String label;
    final int weight;
    final String issue;
    final String remediation;

    PostureScoreCategory(String key, String label, int weight, String issue, String remediation) {
        this.key = key;
        this.label = label;
        this.weight = weight;
        this.issue = issue;
        this.remediation = remediation;
    }

    static PostureScoreCategory fromKey(String key) {
        for (PostureScoreCategory c : values()) {
            if (c.key.equals(key)) return c;
        }
        return null;
    }

    // Sub-score for this category on one agent; 0 when the cron didn't write it.
    double subScore(java.util.Map<String, Object> subScores) {
        Object v = subScores == null ? null : subScores.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0d;
    }

    // Points this category adds to a 0-100 composite (weights sum to 100).
    double points(java.util.Map<String, Object> subScores) {
        return weight * subScore(subScores) / 100d;
    }
}
