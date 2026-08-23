package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.insights.*;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Actionable item #6 — "Likely false positives: flag as min risk, don't hide". See
 * akto_insights_actionable_items card 6 (the "test-guardrail labeled a bearer token as PII-email,
 * 71% precision" worked example).
 *
 * Research finding this is built around, and the reason this ISN'T a naive "regex the evidence
 * for an email" check: request/response payloads are unconditionally anonymized before a
 * violation is even stored (mcp/threat_reporter.go's ReportThreat -> anonymizeEventPayload, in the
 * same pinned Go validator dependency traced for items #4/#5) — by the time EvidenceText reads the
 * stored payload, a GENUINE email/SSN/card number may already be replaced by a redaction
 * placeholder. Checking only "does the raw pattern appear" would misclassify true positives that
 * got redacted as false positives too — the opposite of what this insight is for. So every
 * checkable violation is put into one of three buckets, not two:
 *   - raw pattern present in evidence           -> likely true positive
 *   - no raw pattern, but evidence looks redacted (a generic "[SOMETHING_REDACTED]"-shaped marker,
 *     since the real redactor's exact tag format lives in an external Presidio container this repo
 *     doesn't have visibility into) -> inconclusive, excluded from the precision denominator
 *   - no raw pattern and nothing that looks redacted -> likely false positive (the doc's own
 *     worked example: a plain, unredacted bearer token that never contained an email at all)
 * Precision = true positives / (true positives + false positives), per policy, deliberately
 * excluding inconclusive violations rather than guessing which way they'd resolve.
 *
 * Scope: only PII-&lt;type&gt; violations where PiiPatterns mirrors a real detector regex are
 * checked (see PiiPatterns' own doc for exactly which of the ~29 real PII types are covered and
 * why the rest are excluded rather than approximated). Extending this same methodology to
 * regexPatternsV2-based custom rules is plausible future work (the pattern text is retrievable
 * from the policy) but out of scope here — this covers the doc's own worked example exactly.
 */
public class LikelyFalsePositivesProvider extends AbstractInsightProvider {

    private static final String PII_PREFIX = "PII-";
    private static final int EVENT_PAGE_LIMIT = 2000;
    private static final int EVIDENCE_ROW_CAP = 20;
    private static final int MIN_SAMPLE_SIZE = 5;

    public LikelyFalsePositivesProvider() { super(InsightId.LIKELY_FALSE_POSITIVES, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        List<ThreatCategoryCount> subCategoryCounts = bundle.subCategoryCounts;
        if (subCategoryCounts == null) {
            return failed("THREAT_BACKEND", "Could not load violation counts");
        }

        List<ThreatCategoryCount> piiRows = subCategoryCounts.stream()
                .filter(c -> c.getSubCategory() != null && c.getSubCategory().regionMatches(true, 0, PII_PREFIX, 0, PII_PREFIX.length()))
                .collect(Collectors.toList());
        long totalPiiViolations = piiRows.stream().mapToLong(ThreatCategoryCount::getCount).sum();
        Set<String> policiesWithPii = piiRows.stream().map(ThreatCategoryCount::getCategory).collect(Collectors.toCollection(HashSet::new));

        InsightResult.Metric totalMetric = new InsightResult.Metric(
                "total_pii_violations", "PII-labeled violations",
                totalPiiViolations, null, "count", InsightUtil.count(totalPiiViolations, "violations"), null);

        InsightResult result = skeleton();

        if (totalPiiViolations == 0) {
            result.setStatus(InsightResult.Status.READY.name());
            result.setHeadline("No PII-labeled violations in this window.");
            result.setMetrics(Collections.singletonList(totalMetric));
            result.setMetricsComplete(true);
            result.setDataGaps(new ArrayList<>());
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        InsightResult.Metric policyCountMetric = new InsightResult.Metric(
                "policies_with_pii_rules", "Policies with PII-labeled violations",
                policiesWithPii.size(), null, "count", InsightUtil.count(policiesWithPii.size(), "policies"), null);
        List<InsightResult.Metric> baseMetrics = new ArrayList<>(Arrays.asList(totalMetric, policyCountMetric));

        if (scope == Scope.LIST) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(totalPiiViolations, "violations") + " labeled PII across "
                    + InsightUtil.count(policiesWithPii.size(), "policies")
                    + "; a precision estimate requires the detail view.");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "PROVIDER", "DEFERRED_TO_DETAIL",
                    "Precision per policy requires checking evidence text against the declared PII pattern, done only in the detail view.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        // DETAIL: narrow the raw-event page to exactly the PII-* subCategory values discovered
        // above (the exact type list is per-account/dynamic — see PiiPatterns' doc — so it can't
        // be known in advance; the free aggregate just told us which ones actually occur here).
        List<String> exactSubCategories = piiRows.stream().map(ThreatCategoryCount::getSubCategory).distinct().collect(Collectors.toList());
        List<DashboardMaliciousEvent> events = bundle.fetchViolationEvents(scope, EVENT_PAGE_LIMIT,
                Collections.singletonMap("subCategory", exactSubCategories), null);
        if (events == null) events = new ArrayList<>();

        if (events.isEmpty()) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(totalPiiViolations, "violations") + " labeled PII across "
                    + InsightUtil.count(policiesWithPii.size(), "policies") + ".");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "THREAT_BACKEND", "NO_ROWS",
                    "Could not estimate precision — the violation lookup returned no rows despite "
                            + totalPiiViolations + " known PII violations from the aggregate count.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        Map<String, PolicyStats> byPolicy = new HashMap<>();
        long notCheckableCount = 0;
        for (DashboardMaliciousEvent event : events) {
            String subCategory = event.getSubCategory();
            if (subCategory == null || !subCategory.regionMatches(true, 0, PII_PREFIX, 0, PII_PREFIX.length())) {
                continue;
            }
            String piiType = subCategory.substring(PII_PREFIX.length());
            String policyName = StringUtils.defaultIfBlank(event.getFilterId(), "(unknown policy)");
            PolicyStats stats = byPolicy.computeIfAbsent(policyName, PolicyStats::new);

            if (!PiiPatterns.isCheckable(piiType)) {
                notCheckableCount++;
                continue;
            }
            String evidenceText = EvidenceText.extract(event);
            if (PiiPatterns.rawPatternPresent(piiType, evidenceText)) {
                stats.truePositive++;
            } else if (PiiPatterns.looksRedacted(evidenceText)) {
                stats.inconclusive++;
            } else {
                stats.falsePositive++;
                String credentialLabel = CredentialPatterns.match(evidenceText);
                if (credentialLabel != null) {
                    stats.falsePositiveLooksLikeCredential++;
                }
                if (stats.sampleFalsePositive == null) {
                    stats.sampleFalsePositive = new SampleFp(piiType, evidenceText, credentialLabel);
                }
            }
        }

        List<PolicyStats> withSample = byPolicy.values().stream()
                .filter(s -> s.checkedTotal() >= MIN_SAMPLE_SIZE)
                .sorted(Comparator.comparingDouble(PolicyStats::precision))
                .collect(Collectors.toList());

        long totalChecked = byPolicy.values().stream().mapToLong(PolicyStats::checkedTotal).sum();
        long totalTruePositive = byPolicy.values().stream().mapToLong(s -> s.truePositive).sum();
        long totalFalsePositive = byPolicy.values().stream().mapToLong(s -> s.falsePositive).sum();
        long totalInconclusive = byPolicy.values().stream().mapToLong(s -> s.inconclusive).sum();

        List<InsightResult.Metric> metrics = new ArrayList<>(baseMetrics);
        if (totalChecked > 0) {
            metrics.add(new InsightResult.Metric(
                    "likely_false_positive_count", "Likely false positives",
                    totalFalsePositive, totalChecked, "count",
                    InsightUtil.ofTotal(totalFalsePositive, totalChecked, "violations"), null));
            double overallPrecision = totalChecked > 0 ? (double) totalTruePositive / totalChecked : 0.0;
            metrics.add(new InsightResult.Metric(
                    "overall_precision_percent", "Overall precision (checkable violations)",
                    Math.round(overallPrecision * 100), null, "percent", InsightUtil.percent(overallPrecision), null));
        }
        if (totalInconclusive > 0) {
            metrics.add(new InsightResult.Metric(
                    "inconclusive_count", "Inconclusive (evidence appears redacted)",
                    totalInconclusive, events.size(), "count", InsightUtil.ofTotal(totalInconclusive, events.size(), "violations"), null));
        }
        if (notCheckableCount > 0) {
            metrics.add(new InsightResult.Metric(
                    "not_independently_checkable_count", "PII type not independently checkable",
                    notCheckableCount, events.size(), "count", InsightUtil.ofTotal(notCheckableCount, events.size(), "violations"), null));
        }

        Map<String, GuardrailPolicies> policyByLowerName = new HashMap<>();
        List<GuardrailPolicies> policies = bundle.policies;
        if (policies != null) {
            for (GuardrailPolicies p : policies) {
                if (p.getName() != null) {
                    policyByLowerName.put(lower(p.getName()), p);
                }
            }
        }

        PolicyStats worst = withSample.isEmpty() ? null : withSample.get(0);
        String severity;
        String headline;
        if (worst == null) {
            severity = totalFalsePositive > 0 ? "MEDIUM" : "LOW";
            headline = totalChecked > 0
                    ? String.format(Locale.US, "%s likely false positives of %s checkable PII violations — no single policy has enough volume yet for a reliable per-policy precision estimate.",
                        InsightUtil.count(totalFalsePositive, "violations"), InsightUtil.count(totalChecked, "violations"))
                    : "No PII violations had a mirrored, checkable pattern in this window ("
                        + InsightUtil.count(notCheckableCount, "violations") + " not independently checkable, "
                        + InsightUtil.count(totalInconclusive, "violations") + " inconclusive).";
        } else {
            severity = worst.precision() < 0.5 ? "HIGH" : (worst.precision() < 0.9 ? "MEDIUM" : "LOW");
            headline = String.format(Locale.US,
                    "Worst policy \"%s\" is estimated %s precise (%s of %s checkable violations were likely real) — %s.",
                    worst.policyName, InsightUtil.percent(worst.precision()), InsightUtil.count(worst.truePositive, "hits"),
                    InsightUtil.count(worst.checkedTotal(), "hits"),
                    worst.falsePositiveLooksLikeCredential > 0
                            ? "some false positives look like credentials, not PII"
                            : "refining its conditions would cut the noise without disabling it");
        }

        List<String> caveats = new ArrayList<>();
        caveats.add("Only PII types with a mirrored detection pattern are checked (email, SSN, credit card, and other well-known formats) — see the provider's own notes for the full list; types outside that set are counted separately, never assumed false.");
        caveats.add("Violations whose evidence text already looks redacted are excluded from the precision estimate rather than guessed either way, since request/response payloads are anonymized before storage and the exact redaction format used isn't confirmed.");
        caveats.add("Per-policy precision is only shown once a policy has at least " + MIN_SAMPLE_SIZE + " checkable violations in this window.");
        if (events.size() == EVENT_PAGE_LIMIT) {
            caveats.add("Violations examined are capped at " + EVENT_PAGE_LIMIT + "; more may exist beyond this page.");
        }

        List<PolicyStats> topRows = withSample.stream().limit(EVIDENCE_ROW_CAP).collect(Collectors.toList());
        List<String> evidenceColumns = Arrays.asList("Policy", "Checked", "Likely true positive", "Likely false positive", "Precision", "Sample false positive");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PolicyStats s : topRows) {
            List<Object> cells = Arrays.asList(
                    s.policyName, InsightUtil.count(s.checkedTotal(), "hits"),
                    InsightUtil.count(s.truePositive, "hits"), InsightUtil.count(s.falsePositive, "hits"),
                    InsightUtil.percent(s.precision()), sampleDisplay(s.sampleFalsePositive));
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < evidenceColumns.size(); i++) row.put(evidenceColumns.get(i), cells.get(i));
            rows.add(row);
        }
        List<InsightResult.Evidence> evidence = new ArrayList<>();
        evidence.add(new InsightResult.Evidence("false_positive_precision_by_policy", "Estimated precision by policy",
                evidenceColumns, rows, withSample.size()));

        result.setStatus(InsightResult.Status.READY.name());
        result.setHeadline(headline);
        result.setSeverity(severity);
        result.setMetrics(metrics);
        result.setMetricsComplete(true);
        result.setDataGaps(new ArrayList<>());
        result.setCaveats(caveats);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(worst, policyByLowerName));
        return result;
    }

    private static class SampleFp {
        final String piiType;
        final String evidenceText;
        final String credentialLabel;

        SampleFp(String piiType, String evidenceText, String credentialLabel) {
            this.piiType = piiType;
            this.evidenceText = evidenceText;
            this.credentialLabel = credentialLabel;
        }
    }

    private static class PolicyStats {
        final String policyName;
        long truePositive;
        long falsePositive;
        long inconclusive;
        long falsePositiveLooksLikeCredential;
        SampleFp sampleFalsePositive;

        PolicyStats(String policyName) {
            this.policyName = policyName;
        }

        long checkedTotal() {
            return truePositive + falsePositive;
        }

        double precision() {
            long total = checkedTotal();
            return total > 0 ? (double) truePositive / total : 0.0;
        }
    }

    /** Same redaction caution as CredentialExposureProvider (item #4): if the sample also looks
     *  like a credential, describe it rather than show it — this is meant to prove a mislabeling
     *  problem exists, not to become a second way to leak the same secret. */
    private static String sampleDisplay(SampleFp sample) {
        if (sample == null) {
            return "—";
        }
        if (sample.credentialLabel != null) {
            return "(looks like a " + sample.credentialLabel + " — see Credential exposure insight)";
        }
        if (StringUtils.isBlank(sample.evidenceText)) {
            return "(no evidence text)";
        }
        String flat = sample.evidenceText.replaceAll("\\s+", " ").trim();
        String truncated = flat.length() <= 120 ? flat : flat.substring(0, 120) + "…";
        return "declared \"" + sample.piiType + "\": " + truncated;
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }


    private static List<InsightResult.Cta> buildCtas(PolicyStats worst, Map<String, GuardrailPolicies> policyByLowerName) {
        if (worst == null) {
            return new ArrayList<>();
        }
        GuardrailPolicies policy = policyByLowerName.get(lower(worst.policyName));
        Map<String, Object> params = new HashMap<>();
        params.put("policyId", policy != null ? policy.getHexId() : null);
        params.put("policyName", worst.policyName);

        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(new InsightResult.Cta("add_policy_conditions", "Add policy conditions",
                "BULK_ACTION", "/dashboard/guardrails/policies", params, true));
        if (worst.falsePositiveLooksLikeCredential > 0) {
            ctas.add(new InsightResult.Cta("reclassify_as_credential", "Reclassify as credential detection",
                    "NAVIGATE", "/dashboard/guardrails/policies", params, false));
        }
        ctas.add(new InsightResult.Cta("mark_false_positive", "Mark false positive",
                "NAVIGATE", "/dashboard/guardrails/violations", params, false));
        return ctas;
    }
}
