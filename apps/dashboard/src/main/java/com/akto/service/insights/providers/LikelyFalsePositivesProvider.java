package com.akto.service.insights.providers;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.insights.CredentialPatterns;
import com.akto.service.insights.DataGap;
import com.akto.service.insights.EvidenceRow;
import com.akto.service.insights.EvidenceTable;
import com.akto.service.insights.EvidenceText;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightCta;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightMetric;
import com.akto.service.insights.InsightProvider;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightScope;
import com.akto.service.insights.InsightStatus;
import com.akto.service.insights.Loaded;
import com.akto.service.insights.MetricFormat;
import com.akto.service.insights.PiiPatterns;
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
public class LikelyFalsePositivesProvider implements InsightProvider {

    private static final String PII_PREFIX = "PII-";
    private static final int EVENT_PAGE_LIMIT = 2000;
    private static final int EVIDENCE_ROW_CAP = 20;
    private static final int MIN_SAMPLE_SIZE = 5;

    @Override
    public InsightId getInsightId() {
        return InsightId.LIKELY_FALSE_POSITIVES;
    }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, InsightScope scope,
                                  AbstractThreatDetectionAction threatClient) {
        Loaded<List<ThreatCategoryCount>> subCatLoaded = bundle.getSubCategoryCounts();
        if (!subCatLoaded.isOk()) {
            return failed("Could not load violation counts: " + subCatLoaded.getFailureReason());
        }

        List<ThreatCategoryCount> piiRows = subCatLoaded.getValue().stream()
                .filter(c -> c.getSubCategory() != null && c.getSubCategory().regionMatches(true, 0, PII_PREFIX, 0, PII_PREFIX.length()))
                .collect(Collectors.toList());
        long totalPiiViolations = piiRows.stream().mapToLong(ThreatCategoryCount::getCount).sum();
        Set<String> policiesWithPii = piiRows.stream().map(ThreatCategoryCount::getCategory).collect(Collectors.toCollection(HashSet::new));

        InsightMetric totalMetric = new InsightMetric(
                "total_pii_violations", "PII-labeled violations",
                totalPiiViolations, null, "count", MetricFormat.count(totalPiiViolations, "violation"), null);

        InsightResult result = base();

        if (totalPiiViolations == 0) {
            result.setStatus(InsightStatus.READY.name());
            result.setHeadline("No PII-labeled violations in this window.");
            result.setMetrics(Collections.singletonList(totalMetric));
            result.setMetricsComplete(true);
            result.setDataGaps(new ArrayList<>());
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        InsightMetric policyCountMetric = new InsightMetric(
                "policies_with_pii_rules", "Policies with PII-labeled violations",
                policiesWithPii.size(), null, "count", MetricFormat.count(policiesWithPii.size(), "policy"), null);
        List<InsightMetric> baseMetrics = new ArrayList<>(Arrays.asList(totalMetric, policyCountMetric));

        if (scope == InsightScope.LIST) {
            result.setStatus(InsightStatus.PARTIAL.name());
            result.setHeadline(MetricFormat.count(totalPiiViolations, "violation") + " labeled PII across "
                    + MetricFormat.count(policiesWithPii.size(), "policy")
                    + "; a precision estimate requires the detail view.");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new DataGap(
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
        List<DashboardMaliciousEvent> events;
        try {
            events = threatClient.fetchAllMaliciousEvents(ctx.getStartTs(), ctx.getEndTs(), EVENT_PAGE_LIMIT,
                    Collections.singletonMap("subCategory", exactSubCategories));
        } catch (Exception e) {
            events = new ArrayList<>();
        }

        if (events.isEmpty()) {
            result.setStatus(InsightStatus.PARTIAL.name());
            result.setHeadline(MetricFormat.count(totalPiiViolations, "violation") + " labeled PII across "
                    + MetricFormat.count(policiesWithPii.size(), "policy") + ".");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new DataGap(
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

        List<InsightMetric> metrics = new ArrayList<>(baseMetrics);
        if (totalChecked > 0) {
            metrics.add(new InsightMetric(
                    "likely_false_positive_count", "Likely false positives",
                    totalFalsePositive, totalChecked, "count",
                    MetricFormat.ofTotal(totalFalsePositive, totalChecked), null));
            double overallPrecision = totalChecked > 0 ? (double) totalTruePositive / totalChecked : 0.0;
            metrics.add(new InsightMetric(
                    "overall_precision_percent", "Overall precision (checkable violations)",
                    Math.round(overallPrecision * 100), null, "percent", MetricFormat.percent(overallPrecision), null));
        }
        if (totalInconclusive > 0) {
            metrics.add(new InsightMetric(
                    "inconclusive_count", "Inconclusive (evidence appears redacted)",
                    totalInconclusive, events.size(), "count", MetricFormat.ofTotal(totalInconclusive, events.size()), null));
        }
        if (notCheckableCount > 0) {
            metrics.add(new InsightMetric(
                    "not_independently_checkable_count", "PII type not independently checkable",
                    notCheckableCount, events.size(), "count", MetricFormat.ofTotal(notCheckableCount, events.size()), null));
        }

        Map<String, GuardrailPolicies> policyByLowerName = new HashMap<>();
        Loaded<List<GuardrailPolicies>> policiesLoaded = bundle.getPolicies();
        if (policiesLoaded.isOk()) {
            for (GuardrailPolicies p : policiesLoaded.getValue()) {
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
                        MetricFormat.count(totalFalsePositive, "violation"), MetricFormat.count(totalChecked, "violation"))
                    : "No PII violations had a mirrored, checkable pattern in this window ("
                        + MetricFormat.count(notCheckableCount, "violation") + " not independently checkable, "
                        + MetricFormat.count(totalInconclusive, "violation") + " inconclusive).";
        } else {
            severity = worst.precision() < 0.5 ? "HIGH" : (worst.precision() < 0.9 ? "MEDIUM" : "LOW");
            headline = String.format(Locale.US,
                    "Worst policy \"%s\" is estimated %s precise (%s of %s checkable violations were likely real) — %s.",
                    worst.policyName, MetricFormat.percent(worst.precision()), MetricFormat.count(worst.truePositive, "hit"),
                    MetricFormat.count(worst.checkedTotal(), "hit"),
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
        List<EvidenceRow> rows = new ArrayList<>();
        for (PolicyStats s : topRows) {
            GuardrailPolicies policy = policyByLowerName.get(lower(s.policyName));
            Map<String, Object> raw = new HashMap<>();
            raw.put("policyName", s.policyName);
            raw.put("policyId", policy != null ? policy.getHexId() : null);
            raw.put("truePositive", s.truePositive);
            raw.put("falsePositive", s.falsePositive);
            raw.put("looksLikeCredential", s.falsePositiveLooksLikeCredential > 0);

            rows.add(new EvidenceRow(Arrays.asList(
                    s.policyName, MetricFormat.count(s.checkedTotal(), "hit"),
                    MetricFormat.count(s.truePositive, "hit"), MetricFormat.count(s.falsePositive, "hit"),
                    MetricFormat.percent(s.precision()), sampleDisplay(s.sampleFalsePositive)
            ), raw));
        }
        List<EvidenceTable> evidence = new ArrayList<>();
        evidence.add(new EvidenceTable("false_positive_precision_by_policy", "Estimated precision by policy",
                Arrays.asList("Policy", "Checked", "Likely true positive", "Likely false positive", "Precision", "Sample false positive"),
                rows, withSample.size()));

        result.setStatus(InsightStatus.READY.name());
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

    private InsightResult base() {
        InsightResult r = new InsightResult();
        r.setInsightId(getInsightId().name());
        r.setTitle(getInsightId().getDefaultTitle());
        r.setCategory(getInsightId().getCategory().name());
        r.setCaveats(new ArrayList<>());
        return r;
    }

    private InsightResult failed(String reason) {
        InsightResult r = InsightResult.noData(getInsightId(), reason);
        r.setDataGaps(new ArrayList<>(Collections.singletonList(
                new DataGap("THREAT_BACKEND", "REQUEST_FAILED", reason))));
        return r;
    }

    private static List<InsightCta> buildCtas(PolicyStats worst, Map<String, GuardrailPolicies> policyByLowerName) {
        if (worst == null) {
            return new ArrayList<>();
        }
        GuardrailPolicies policy = policyByLowerName.get(lower(worst.policyName));
        Map<String, Object> params = new HashMap<>();
        params.put("policyId", policy != null ? policy.getHexId() : null);
        params.put("policyName", worst.policyName);

        List<InsightCta> ctas = new ArrayList<>();
        ctas.add(new InsightCta("add_policy_conditions", "Add policy conditions",
                "BULK_ACTION", "/dashboard/guardrails/policies", params, true));
        if (worst.falsePositiveLooksLikeCredential > 0) {
            ctas.add(new InsightCta("reclassify_as_credential", "Reclassify as credential detection",
                    "NAVIGATE", "/dashboard/guardrails/policies", params, false));
        }
        ctas.add(new InsightCta("mark_false_positive", "Mark false positive",
                "NAVIGATE", "/dashboard/guardrails/violations", params, false));
        return ctas;
    }
}
