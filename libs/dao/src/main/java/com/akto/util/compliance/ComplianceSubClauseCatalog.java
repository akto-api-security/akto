package com.akto.util.compliance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fixed catalog of sub-clauses per compliance framework — the denominator for Framework readiness
 * (see PostureService#frameworkReadiness). No such catalog exists anywhere else in the repo:
 * guardrail-policy clause lists are always empty (GuardrailComplianceSuggestionHandler writes
 * new ArrayList<>() per framework) and GuardrailComplianceInfosDao has no writer in this repo.
 *
 * Framework keys MUST match GuardrailComplianceSuggestionHandler.FRAMEWORK_TRIGGERS and
 * ComplianceMenu.jsx#getCompliances() — the same invariant that file already documents. Adding a
 * framework here without adding it there (or vice versa) silently breaks the join.
 *
 * The LLM clause-attribution step (GuardrailClauseAttributionHandler) is constrained to pick only
 * from subClausesFor(framework)'s ids, so the numerator is always a subset of this list and
 * readiness can never exceed 100%. This is a deliberate, load-bearing invariant, not a limitation
 * to work around: it is what makes "N of M sub-clauses covered" a stable, comparable number across
 * scans and LLM providers instead of a count of however many labels a model happened to invent.
 * A violation that doesn't fit any listed clause is real signal that this first-pass catalog is
 * incomplete — GuardrailClauseAttributionHandler surfaces it as a non-scoring "suggested clause"
 * (see ComplianceClauseCoverage#suggestedClauses) for a human to fold into this file, rather than
 * either silently discarding it or letting an unreviewed LLM opinion move the score.
 */
public final class ComplianceSubClauseCatalog {

    private ComplianceSubClauseCatalog() {}

    /** One sub-clause: a short, stable id an LLM can reproduce exactly (unlike the full label,
     *  which is prose an LLM will legitimately paraphrase — "GDPR Art. 32" vs "Article 32 -
     *  Security of processing" describe the same clause but don't string-match), plus the
     *  human-readable label for prompts/display. */
    public static final class SubClause {
        public final String id;
        public final String label;

        SubClause(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static final Map<String, List<String>> CATALOG = new LinkedHashMap<>();
    // Derived from CATALOG below, once, at class-init — see deriveId().
    private static final Map<String, List<SubClause>> SUBCLAUSES = new LinkedHashMap<>();
    // normalized(key) -> canonical framework name, so lookups are case/punctuation-insensitive
    // the way TestConfigYamlParser (uppercases) and normalizeComplianceLabel in issues/transform.js
    // (lowercases, strips punctuation) each independently need to be.
    private static final Map<String, String> NORMALIZED_TO_CANONICAL = new LinkedHashMap<>();

    static {
        put("GDPR", Arrays.asList(
                "Article 5 - Principles relating to processing",
                "Article 6 - Lawfulness of processing",
                "Article 9 - Special categories of personal data",
                "Article 15 - Right of access",
                "Article 17 - Right to erasure",
                "Article 25 - Data protection by design and by default",
                "Article 32 - Security of processing",
                "Article 33 - Breach notification"
        ));
        put("HIPAA", Arrays.asList(
                "164.502 - Uses and disclosures of PHI",
                "164.308 - Administrative safeguards",
                "164.310 - Physical safeguards",
                "164.312 - Technical safeguards",
                "164.314 - Organizational requirements",
                "164.316 - Policies and procedures",
                "164.404 - Breach notification to individuals"
        ));
        put("PCI DSS", Arrays.asList(
                "Requirement 1 - Network security controls",
                "Requirement 3 - Protect stored account data",
                "Requirement 4 - Protect cardholder data in transit",
                "Requirement 6 - Develop and maintain secure systems",
                "Requirement 7 - Restrict access by business need to know",
                "Requirement 8 - Identify users and authenticate access",
                "Requirement 10 - Log and monitor all access",
                "Requirement 11 - Test security of systems and networks"
        ));
        put("SOC 2", Arrays.asList(
                "CC6.1 - Logical access controls",
                "CC6.6 - Boundary protection",
                "CC6.7 - Data transmission and disposal controls",
                "CC7.2 - Anomaly and security event detection",
                "CC7.3 - Security incident response",
                "CC8.1 - Change management"
        ));
        put("ISO 27001", Arrays.asList(
                "A.5.1 - Policies for information security",
                "A.8.1 - User endpoint devices",
                "A.8.2 - Privileged access rights",
                "A.8.3 - Information access restriction",
                "A.8.12 - Data leakage prevention",
                "A.8.24 - Use of cryptography",
                "A.5.34 - Privacy and protection of PII"
        ));
        put("OWASP LLM", Arrays.asList(
                "LLM01 - Prompt Injection",
                "LLM02 - Sensitive Information Disclosure",
                "LLM03 - Supply Chain",
                "LLM04 - Data and Model Poisoning",
                "LLM05 - Improper Output Handling",
                "LLM06 - Excessive Agency",
                "LLM07 - System Prompt Leakage",
                "LLM08 - Vector and Embedding Weaknesses",
                "LLM09 - Misinformation",
                "LLM10 - Unbounded Consumption"
        ));
        put("OWASP Agentic Top 10", Arrays.asList(
                "Agentic01 - Agent Goal Manipulation",
                "Agentic02 - Agent Impersonation",
                "Agentic03 - Excessive Agent Autonomy",
                "Agentic04 - Agent Memory and Context Poisoning",
                "Agentic05 - Multi-Agent Exploitation",
                "Agentic06 - Agent Supply Chain",
                "Agentic07 - Agent Communication Poisoning",
                "Agentic08 - Rogue Agents",
                "Agentic09 - Human-in-the-Loop Bypass",
                "Agentic10 - Insufficient Agent Monitoring"
        ));
        put("OWASP Agentic Skills Top 10", Arrays.asList(
                "Skills01 - Unauthorized Tool Invocation",
                "Skills02 - Excessive Tool Permissions",
                "Skills03 - Untrusted Third-Party Skills",
                "Skills04 - Tool Output Injection",
                "Skills05 - Tool Chaining Abuse",
                "Skills06 - Credential Leakage via Tools",
                "Skills07 - Insecure Tool Registration",
                "Skills08 - Tool Result Tampering",
                "Skills09 - Missing Tool Authorization Checks",
                "Skills10 - Tool Sandbox Escape"
        ));
        put("MITRE ATLAS", Arrays.asList(
                "Reconnaissance",
                "Resource Development",
                "Initial Access",
                "ML Model Access",
                "Execution",
                "Persistence",
                "Defense Evasion",
                "Discovery",
                "Collection",
                "Exfiltration",
                "Impact"
        ));
        put("OWASP", Arrays.asList(
                "A01 - Broken Access Control",
                "A02 - Cryptographic Failures",
                "A03 - Injection",
                "A04 - Insecure Design",
                "A05 - Security Misconfiguration",
                "A06 - Vulnerable and Outdated Components",
                "A07 - Identification and Authentication Failures",
                "A08 - Software and Data Integrity Failures",
                "A09 - Security Logging and Monitoring Failures",
                "A10 - Server-Side Request Forgery"
        ));
        put("CIS Controls", Arrays.asList(
                "Control 3 - Data Protection",
                "Control 4 - Secure Configuration",
                "Control 6 - Access Control Management",
                "Control 8 - Audit Log Management",
                "Control 10 - Malware Defenses",
                "Control 13 - Network Monitoring and Defense"
        ));
        put("CSA CCM", Arrays.asList(
                "IAM - Identity and Access Management",
                "DSI - Data Security and Information Lifecycle Management",
                "IVS - Infrastructure and Virtualization Security",
                "CCC - Change Control and Configuration Management",
                "STA - Supply Chain Management and Transparency",
                "GRC - Governance, Risk and Compliance"
        ));
        put("EU AI Act", Arrays.asList(
                "Article 9 - Risk management system",
                "Article 10 - Data and data governance",
                "Article 13 - Transparency and provision of information",
                "Article 14 - Human oversight",
                "Article 15 - Accuracy, robustness and cybersecurity",
                "Article 26 - Obligations of deployers",
                "Article 52 - Transparency obligations for GPAI"
        ));
        put("NIST 800-53", Arrays.asList(
                "AC - Access Control",
                "AU - Audit and Accountability",
                "IA - Identification and Authentication",
                "SC - System and Communications Protection",
                "SI - System and Information Integrity",
                "IR - Incident Response",
                "CA - Assessment, Authorization and Monitoring"
        ));
        put("NIST 800-171", Arrays.asList(
                "3.1 - Access Control",
                "3.3 - Audit and Accountability",
                "3.5 - Identification and Authentication",
                "3.8 - Media Protection",
                "3.13 - System and Communications Protection",
                "3.14 - System and Information Integrity"
        ));
        put("Cybersecurity Maturity Model Certification (CMMC)", Arrays.asList(
                "AC - Access Control",
                "AU - Audit and Accountability",
                "IA - Identification and Authentication",
                "SC - System and Communications Protection",
                "IR - Incident Response",
                "MP - Media Protection"
        ));
        put("FISMA", Arrays.asList(
                "Categorize - System categorization",
                "Select - Security control selection",
                "Implement - Security control implementation",
                "Assess - Security control assessment",
                "Authorize - System authorization",
                "Monitor - Continuous monitoring"
        ));
        put("FedRAMP", Arrays.asList(
                "AC - Access Control",
                "AU - Audit and Accountability",
                "IA - Identification and Authentication",
                "SC - System and Communications Protection",
                "CM - Configuration Management",
                "IR - Incident Response"
        ));
        put("NIST AI Risk Management Framework", Arrays.asList(
                "GOVERN - AI risk governance and culture",
                "MAP - Context and AI risk identification",
                "MEASURE - AI risk analysis and tracking",
                "MANAGE - AI risk response and monitoring"
        ));

        for (Map.Entry<String, List<String>> e : CATALOG.entrySet()) {
            NORMALIZED_TO_CANONICAL.put(normalizeKey(e.getKey()), e.getKey());

            List<SubClause> subClauses = new ArrayList<>();
            Set<String> idsSeenInFramework = new HashSet<>();
            for (String label : e.getValue()) {
                String id = deriveId(label);
                // Every label in this file happens to have a unique leading code within its own
                // framework (checked at class-load, not just by inspection) — if a future entry
                // collides, fail loudly at startup rather than silently merging two clauses under
                // one id.
                if (!idsSeenInFramework.add(id)) {
                    throw new IllegalStateException("Duplicate sub-clause id '" + id + "' in framework '"
                            + e.getKey() + "' — give it a distinguishing prefix in the label.");
                }
                subClauses.add(new SubClause(id, label));
            }
            SUBCLAUSES.put(e.getKey(), Collections.unmodifiableList(subClauses));
        }
    }

    private static void put(String framework, List<String> clauses) {
        CATALOG.put(framework, Collections.unmodifiableList(clauses));
    }

    /** Lowercase, strip everything but letters/digits — the Java counterpart of
     *  normalizeComplianceLabel() in apps/dashboard/.../pages/issues/transform.js, needed because
     *  the catalog, guardrail policies, and TestConfigYamlParser (which uppercases) each spell
     *  framework names with different casing/punctuation. */
    private static String normalizeKey(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** The short code every label in this file already starts with ("Article 5 - ...", "LLM01 -
     *  ...", "CC6.1 - ..."), or the whole label when there's no " - " separator (MITRE ATLAS's
     *  entries are bare phase names). This is what the LLM is asked to return instead of the full
     *  label — trivial to reproduce exactly, unlike prose it will legitimately paraphrase. */
    private static String deriveId(String label) {
        int dash = label.indexOf(" - ");
        return (dash > 0 ? label.substring(0, dash) : label).trim();
    }

    /** Canonical framework name for any casing/punctuation variant, or null if unknown. */
    public static String canonicalFramework(String raw) {
        if (raw == null) return null;
        return NORMALIZED_TO_CANONICAL.get(normalizeKey(raw));
    }

    /** Ordered, fixed sub-clause list for a framework (normalized lookup), or empty if unknown. */
    public static List<SubClause> subClausesFor(String framework) {
        String canonical = canonicalFramework(framework);
        if (canonical == null) return Collections.emptyList();
        return SUBCLAUSES.getOrDefault(canonical, Collections.emptyList());
    }

    /** Same list, labels only — kept for callers that only need display text, not ids. */
    public static List<String> clausesFor(String framework) {
        String canonical = canonicalFramework(framework);
        if (canonical == null) return Collections.emptyList();
        return CATALOG.getOrDefault(canonical, Collections.emptyList());
    }

    /** The sub-clause whose id matches (case/whitespace-insensitive), or null. This — not a label
     *  match — is what GuardrailClauseAttributionHandler validates an LLM's answer against. */
    public static SubClause findById(String framework, String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        for (SubClause sc : subClausesFor(framework)) {
            if (sc.id.equalsIgnoreCase(trimmed)) return sc;
        }
        return null;
    }

    public static int totalClauses(String framework) {
        return subClausesFor(framework).size();
    }

    public static Set<String> frameworks() {
        return Collections.unmodifiableSet(CATALOG.keySet());
    }
}
