package com.akto.service.posture;

import com.akto.service.insights.InsightResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AgentDetailResult {

    private Header header;
    private List<Tool> tools = new ArrayList<>();
    private SensitiveData data = new SensitiveData();
    private List<Rule> protection = new ArrayList<>();
    private RedTeam redTeam = new RedTeam();
    private List<ScoreBreakdownRow> scoreBreakdown = new ArrayList<>();
    private List<RemediationRow> remediation = new ArrayList<>();
    private GuardrailActivity guardrailActivity = new GuardrailActivity();
    private Finding openedFromFinding;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Header {
        private int collectionId;
        private String name;
        private String description;
        private long riskScore;
        private String severity;
        private String environment;
        private Integer createdAt;
        private Integer lastActive;
        private String host;
        private String topIssue;
        /** Covering guardrail policy names joined by ", ", or "Not covered". */
        private String guardrailCoverage;
        private List<InsightResult.Cta> ctas = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Tool {
        /** The tool's own name, not the raw URL — ToolClassificationCron.toolNameFromUrl. */
        private String name;
        private String method;
        private String url;
        private String capability;
        private String capabilityLabel;
        /** The "privileged · destructive" line under the name, from the capability alone. */
        private String detail;
        private boolean privileged;
        private int lastSeen;
    }

    /** available=false means the guardrail activity could not be read, which is not the same as
     *  the agent having no detections. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SensitiveData {
        private boolean available = true;
        private List<String> types = new ArrayList<>();
        private boolean sensitiveDataAccess;
        private long detections;
    }

    /** One configured rule of a policy covering this agent, not the policy itself. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Rule {
        private String name;
        private boolean enabled;
        /** Subtype values the enabling policies configured — PII types, denied topics, regex
         *  patterns. Empty for rules that have no subtypes. */
        private List<String> details = new ArrayList<>();
        /** How many subtypes there are in total, so the UI can render "+N" past what it shows. */
        private int detailsTotal;
        private String appliesOn;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Finding {
        private String title;
        private String severity;
    }

    /** scanned=false means no red-team run has ever touched this agent — openFindings and the
     *  findings list are then meaningless and the UI shows a not-scanned state instead. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RedTeam {
        private boolean scanned;
        private Integer lastScannedAt;
        private long openFindings;
        /** One entry per non-zero severity among the same counts the posture score cron already
         *  groups by collection, in CRITICAL..LOW order. */
        private List<SeverityCount> severityCounts = new ArrayList<>();
        private List<RedTeamFinding> findings = new ArrayList<>();
        private List<InsightResult.Cta> ctas = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SeverityCount {
        private String severity;
        private int count;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RedTeamFinding {
        private String test;
        /** The test's own description from its YAML template — null when the template has none. */
        private String description;
        private String endpoint;
        private String severity;
        private int lastSeen;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ScoreBreakdownRow {
        private String category;
        private int weight;
        private double subScore;
        private double points;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RemediationRow {
        private String category;
        private double points;
        private String remediation;
    }

    /** available=false means the guardrail activity could not be read — not the same as no events. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class GuardrailActivity {
        private boolean available = true;
        private long total;
        private List<GuardrailEvent> events = new ArrayList<>();
        private List<InsightResult.Cta> ctas = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class GuardrailEvent {
        private long timestamp;
        /** subCategory, else category — same field agentProfile's own maliciousEventsSection uses. */
        private String event;
        private String url;
        private String severity;
    }
}
