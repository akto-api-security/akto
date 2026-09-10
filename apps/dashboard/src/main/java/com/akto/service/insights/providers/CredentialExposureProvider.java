package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.service.insights.*;
import com.akto.util.AgenticObserveUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Actionable item #4 — "Credentials and secrets, separated from PII": a live bearer token or API
 * key is not the same object as an email address, but today both can land in the same subCategory
 * bucket at the same severity. See akto_insights_actionable_items card 4.
 *
 * Research finding this is built around: guardrails DOES have a dedicated secrets scanner
 * (GuardrailPolicies.secretsDetection -> Go validator -> agent-guard's "Secrets" scanner), and a
 * violation it fires is persisted with subCategory == "Secrets" — a real, free-to-query,
 * distinguishable value via the same get_subcategory_wise_count aggregation item #2 already uses.
 * But it only fires for policies that have that scanner enabled; a policy without it (the doc's own
 * "test-guardrail" example) lets a real bearer token fall through into whatever else is enabled and
 * come out mislabeled (there, as "PII-email"). So the free aggregate alone would undercount exactly
 * the case this insight exists to surface. LIST scope reports the free, honest "Secrets" count;
 * DETAIL scope pages raw violations and pattern-matches evidence text against known credential
 * shapes to find the mislabeled ones too, regardless of what subCategory they landed in.
 *
 * Security note: matched credential VALUES are never included in the response — only the pattern
 * type (e.g. "Bearer token") and where/who. This provider must not become a secrets-leak vector.
 */
public class CredentialExposureProvider extends AbstractInsightProvider {

    private static final String SECRETS_SUBCATEGORY = "Secrets";
    private static final int EVENT_PAGE_LIMIT = 1000;
    // Above this many mislabeled (undetected) credential hits, this reads as active, ongoing
    // leakage rather than a handful of missed detections — escalate past HIGH.
    private static final int CRITICAL_MISLABELED_THRESHOLD = 20;
    private static final int EVIDENCE_ROW_CAP = 20;

    public CredentialExposureProvider() { super(InsightId.CREDENTIAL_EXPOSURE, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        List<ThreatCategoryCount> subCategoryCounts = bundle.subCategoryCounts;
        // subCategoryCounts is a threat-backend aggregate and the bundle never returns null
        // for it — a failed call is signalled by threatBackendAvailable instead, so an empty
        // list here can't be told apart from a real zero unless that flag is also checked.
        if (!bundle.threatBackendAvailable) {
            return failed("THREAT_BACKEND", "Could not load violation counts");
        }

        long labeledSecretsCount = subCategoryCounts.stream()
                .filter(c -> SECRETS_SUBCATEGORY.equalsIgnoreCase(c.getSubCategory()))
                .mapToLong(ThreatCategoryCount::getCount)
                .sum();

        InsightResult.Metric labeledMetric = new InsightResult.Metric(
                "secrets_labeled_count", "Violations already labeled \"Secrets\"",
                labeledSecretsCount, null, "count",
                InsightUtil.count(labeledSecretsCount, "violations"), null);

        InsightResult result = skeleton();

        if (scope == Scope.LIST) {
            // A real severity when there's already-labeled Secrets volume to show — but this can
            // only ever be a floor: the mislabeled-credential case this insight exists to catch is,
            // by definition, NOT counted in labeledSecretsCount, and finding it needs the raw-event
            // scan below. So unlike most providers here, labeledSecretsCount == 0 does NOT mean
            // severity stays null out of caution — it stays null because there is genuinely nothing
            // cheap to show yet, not because it's a confirmed-clean result.
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(labeledSecretsCount, "violations")
                    + " already labeled Secrets; checking other buckets for mislabeled credentials requires the detail view.");
            if (labeledSecretsCount > 0) {
                result.setSeverity("MEDIUM");
            }
            result.setMetrics(Collections.singletonList(labeledMetric));
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "PROVIDER", "DEFERRED_TO_DETAIL",
                    "Credentials mislabeled under a different category (e.g. PII) require paging raw evidence, done only in the detail view.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        // DETAIL: page the most recent violations account-wide (no subCategory narrowing — the
        // whole point is to find credential-shaped evidence wherever it landed) and pattern-match.
        // "exclude" drops Skills Evaluations traffic — a skill's own documentation matching a
        // credential-shaped pattern isn't a live credential leak (same convention as
        // AlertFatigueProvider and PromptInjectionRepeatsProvider).
        List<DashboardMaliciousEvent> events = bundle.fetchViolationEvents(scope, EVENT_PAGE_LIMIT, null, "exclude");
        if (events == null) events = new ArrayList<>();

        if (events.isEmpty()) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(labeledSecretsCount, "violations") + " already labeled Secrets.");
            result.setMetrics(Collections.singletonList(labeledMetric));
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "THREAT_BACKEND", "NO_ROWS",
                    "Could not scan for mislabeled credentials — the violation lookup returned no rows.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        List<Match> matches = new ArrayList<>();
        for (DashboardMaliciousEvent event : events) {
            String evidenceText = EvidenceText.extract(event);
            if (StringUtils.isBlank(evidenceText)) {
                continue;
            }
            String patternLabel = CredentialPatterns.match(evidenceText);
            if (patternLabel == null) {
                continue;
            }
            boolean labeledSecrets = SECRETS_SUBCATEGORY.equalsIgnoreCase(event.getSubCategory());
            matches.add(new Match(event, patternLabel, labeledSecrets));
        }

        long totalMatches = matches.size();
        long mislabeledCount = matches.stream().filter(m -> !m.labeledSecrets).count();
        double mislabeledFraction = totalMatches > 0 ? (double) mislabeledCount / totalMatches : 0.0;

        Set<String> distinctUsers = matches.stream()
                .map(m -> StringUtils.defaultIfBlank(m.event.getActor(), "(unattributed)"))
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> distinctDestinations = matches.stream()
                .map(m -> StringUtils.defaultIfBlank(m.event.getHost(), "(unknown)"))
                .collect(Collectors.toCollection(HashSet::new));

        List<InsightResult.Metric> metrics = new ArrayList<>();
        metrics.add(labeledMetric);
        metrics.add(new InsightResult.Metric(
                "credential_pattern_matches", "Credential-shaped violations found",
                totalMatches, (long) events.size(), "count",
                InsightUtil.ofTotal(totalMatches, events.size(), "violations"), null));
        metrics.add(new InsightResult.Metric(
                "mislabeled_count", "Mislabeled as something other than Secrets",
                mislabeledCount, totalMatches, "count",
                InsightUtil.ofTotal(mislabeledCount, totalMatches, "violations"), null));
        if (totalMatches > 0) {
            metrics.add(new InsightResult.Metric(
                    "mislabeled_percent", "Percent mislabeled",
                    Math.round(mislabeledFraction * 100), null, "percent",
                    InsightUtil.percent(mislabeledFraction), null));
        }
        metrics.add(new InsightResult.Metric(
                "distinct_users_affected", "Users affected",
                distinctUsers.size(), null, "count",
                InsightUtil.count(distinctUsers.size(), "users"), null));
        metrics.add(new InsightResult.Metric(
                "distinct_destinations_affected", "Destinations affected",
                distinctDestinations.size(), null, "count",
                InsightUtil.count(distinctDestinations.size(), "destinations"), null));

        String severity;
        String headline;
        if (mislabeledCount > 0) {
            severity = mislabeledCount >= CRITICAL_MISLABELED_THRESHOLD ? "CRITICAL" : "HIGH";
            headline = String.format(Locale.US,
                    "%s look like live credentials but %s labeled as something other than Secrets — same bucket, same severity as PII.",
                    InsightUtil.count(totalMatches, "violations"), InsightUtil.ofTotal(mislabeledCount, totalMatches, "violations"));
        } else if (totalMatches > 0) {
            severity = "MEDIUM";
            headline = InsightUtil.count(totalMatches, "violations")
                    + " look like live credentials, all already correctly labeled Secrets.";
        } else {
            severity = "LOW";
            headline = "No credential-shaped evidence found among the " + InsightUtil.count(events.size(), "violations") + " examined.";
        }

        List<String> caveats = new ArrayList<>();
        caveats.add("Matched credential values are never shown — only the pattern type, e.g. \"Bearer token\".");
        caveats.add("This is a lower bound over the most recent " + EVENT_PAGE_LIMIT
                + " violations, matched against known credential shapes (Bearer/AWS/GitHub/Slack/OpenAI/JWT/PEM-key/generic key=value); obfuscated or unusual formats may be missed.");
        if (events.size() == EVENT_PAGE_LIMIT) {
            caveats.add("Violations examined are capped at " + EVENT_PAGE_LIMIT + "; more may exist beyond this page.");
        }

        String concern = null;
        String impact = null;
        String remediation = null;
        if (mislabeledCount > 0) {
            concern = String.format(Locale.US,
                    "%s look like live credentials (e.g. %s) but %s labeled as something other than Secrets, "
                            + "across %s and %s.",
                    InsightUtil.count(totalMatches, "violations"), matches.stream().filter(m -> !m.labeledSecrets).findFirst().map(m -> m.patternLabel).orElse("a credential pattern"),
                    InsightUtil.ofTotal(mislabeledCount, totalMatches, "violations"),
                    InsightUtil.count(distinctUsers.size(), "users"), InsightUtil.count(distinctDestinations.size(), "destinations"));
            impact = "A credential mislabeled as PII or something else evades any policy control that's specifically "
                    + "tuned for Secrets (e.g. stricter blocking, different alerting) — it's real credential exposure "
                    + "hiding under the wrong control.";
            remediation = "Enable the Secrets scanner on whichever policy is catching these hosts, so future hits are "
                    + "correctly classified and routed to the right control instead of falling through to a generic one.";
        }

        List<InsightResult.Evidence> evidence = new ArrayList<>();
        if (!matches.isEmpty()) {
            List<Match> sorted = matches.stream()
                    // Mislabeled first — that's the actionable surprise; correctly-labeled ones still
                    // matter for "Rotate exposed credential" but are the less urgent half.
                    .sorted((a, b) -> Boolean.compare(a.labeledSecrets, b.labeledSecrets))
                    .limit(EVIDENCE_ROW_CAP)
                    .collect(Collectors.toList());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Match m : sorted) {
                String actor = StringUtils.defaultIfBlank(m.event.getActor(), "(unattributed)");
                String host = StringUtils.defaultIfBlank(m.event.getHost(), "(unknown)");
                String device = AgenticObserveUtil.extractEndpointId(host);
                String labeledAs = m.labeledSecrets ? "Secrets (correct)"
                        : InsightUtil.humanizeSubCategory(m.event.getSubCategory());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("User", actor);
                row.put("Device", StringUtils.defaultString(device));
                row.put("Destination", host);
                row.put("Pattern type", m.patternLabel);
                row.put("Currently labeled as", labeledAs);
                rows.add(row);
            }
            evidence.add(new InsightResult.Evidence("credential_exposure_matches", "Credential-shaped violations",
                    Arrays.asList("User", "Device", "Destination", "Pattern type", "Currently labeled as"),
                    rows, matches.size()));
        }

        result.setStatus(InsightResult.Status.READY.name());
        result.setHeadline(headline);
        result.setSeverity(severity);
        result.setMetrics(metrics);
        result.setMetricsComplete(true);
        result.setDataGaps(new ArrayList<>());
        result.setCaveats(caveats);
        result.setConcern(concern);
        result.setImpact(impact);
        result.setRemediation(remediation);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(matches));
        return result;
    }

    private static class Match {
        final DashboardMaliciousEvent event;
        final String patternLabel;
        final boolean labeledSecrets;

        Match(DashboardMaliciousEvent event, String patternLabel, boolean labeledSecrets) {
            this.event = event;
            this.patternLabel = patternLabel;
            this.labeledSecrets = labeledSecrets;
        }
    }


    private static List<InsightResult.Cta> buildCtas(List<Match> matches) {
        if (matches.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> actors = matches.stream()
                .map(m -> StringUtils.defaultIfBlank(m.event.getActor(), "(unattributed)"))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        // ViolationsPage.jsx reads "?user=" and seeds its existing per-user card-click filter —
        // there's no bulk "force sync/block on this class" action anywhere today (that's a policy
        // config change, not something these actor identities alone can drive), so all three route
        // to the filtered evidence instead of a Guardrail Policies page they have nothing to act on.
        Map<String, Object> params = new HashMap<>();
        params.put("user", String.join(",", actors));

        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(new InsightResult.Cta("rotate_exposed_credential", "Rotate exposed credential",
                "NAVIGATE", "/dashboard/guardrails/violations", params, true));
        ctas.add(new InsightResult.Cta("review_affected_users", "Review affected users' violations",
                "NAVIGATE", "/dashboard/guardrails/violations", params, false));
        ctas.add(new InsightResult.Cta("notify_user_security_lead", "Notify user + security lead",
                "NAVIGATE", "/dashboard/guardrails/violations", params, false));
        return ctas;
    }
}
