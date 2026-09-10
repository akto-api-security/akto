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
import java.util.LinkedHashSet;
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
        // subCategoryCounts is a threat-backend aggregate and the bundle never returns null
        // for it — a failed call is signalled by threatBackendAvailable instead, so an empty
        // list here can't be told apart from a real zero unless that flag is also checked.
        if (!bundle.threatBackendAvailable) {
            return failed("THREAT_BACKEND", "Could not load violation counts");
        }

        // Taxonomy-mismatch fallback (confirmed on real accounts): subCategory frequently carries
        // the firing policy's own name instead of "PII-<type>". Recover the common, unambiguous
        // case — a policy that does ONLY PII detection (no Secrets/prompt-injection scanner also
        // enabled) — by trusting the policy's own config instead of the literal. A multi-scanner
        // policy is left on the exact-literal path only, since which scanner fired can't be told
        // apart from the aggregate alone.
        Set<String> piiOnlyPolicyNamesLower = new HashSet<>();
        Map<String, GuardrailPolicies> piiOnlyPolicyByLowerName = new HashMap<>();
        if (bundle.policies != null) {
            for (GuardrailPolicies p : bundle.policies) {
                if (p.getName() != null && InsightUtil.policyHasPiiDetection(p)
                        && !InsightUtil.policyHasSecretsDetection(p)
                        && !InsightUtil.policyHasPromptInjectionDetection(p)) {
                    piiOnlyPolicyNamesLower.add(lower(p.getName()));
                    piiOnlyPolicyByLowerName.put(lower(p.getName()), p);
                }
            }
        }
        List<ThreatCategoryCount> piiRows = subCategoryCounts.stream()
                .filter(c -> (c.getSubCategory() != null && c.getSubCategory().regionMatches(true, 0, PII_PREFIX, 0, PII_PREFIX.length()))
                        || piiOnlyPolicyNamesLower.contains(lower(c.getCategory())))
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
            // Real precision (the false-positive rate) still requires the detail view, but a reader
            // shouldn't see "Coming soon" for a card with real PII-labeled volume behind it — tier by
            // volume alone as a floor; DETAIL can move this to CRITICAL once precision is known.
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(totalPiiViolations, "violations") + " labeled PII across "
                    + InsightUtil.count(policiesWithPii.size(), "policies")
                    + "; a precision estimate requires the detail view.");
            result.setSeverity(totalPiiViolations >= 20 ? "MEDIUM" : "LOW");
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
        // Only rows that are actually PII-<type>-labeled feed this — a piiOnlyPolicyNamesLower
        // fallback row's subCategory is the policy's own name, not a real PII type. "exclude" drops
        // Skills Evaluations traffic — a skill's own documentation matching a PII pattern isn't a
        // live false-positive-worthy hit (same convention as AlertFatigueProvider).
        List<String> exactSubCategories = piiRows.stream()
                .map(ThreatCategoryCount::getSubCategory)
                .filter(sc -> sc != null && sc.regionMatches(true, 0, PII_PREFIX, 0, PII_PREFIX.length()))
                .distinct().collect(Collectors.toList());
        List<DashboardMaliciousEvent> events = new ArrayList<>();
        boolean anyFetchCapped = false;
        if (!exactSubCategories.isEmpty()) {
            List<DashboardMaliciousEvent> labeled = bundle.fetchViolationEvents(scope, EVENT_PAGE_LIMIT,
                    Collections.singletonMap("subCategory", exactSubCategories), "exclude");
            if (labeled != null) {
                events.addAll(labeled);
                anyFetchCapped = anyFetchCapped || labeled.size() == EVENT_PAGE_LIMIT;
            }
        }
        // Fallback: pull every hit from PII-only policies directly, regardless of what subCategory
        // literal it landed under — these policies do nothing but PII detection, so every hit is one.
        Set<String> fallbackPolicyNames = piiRows.stream()
                .filter(c -> piiOnlyPolicyNamesLower.contains(lower(c.getCategory())))
                .map(ThreatCategoryCount::getCategory)
                .distinct().collect(Collectors.toCollection(LinkedHashSet::new));
        if (!fallbackPolicyNames.isEmpty()) {
            List<DashboardMaliciousEvent> fallbackEvents = bundle.fetchViolationEvents(scope, EVENT_PAGE_LIMIT,
                    Collections.singletonMap("latestAttack", new ArrayList<>(fallbackPolicyNames)), "exclude");
            if (fallbackEvents != null) {
                anyFetchCapped = anyFetchCapped || fallbackEvents.size() == EVENT_PAGE_LIMIT;
                Set<String> seenIds = events.stream().map(DashboardMaliciousEvent::getId).collect(Collectors.toCollection(HashSet::new));
                for (DashboardMaliciousEvent e : fallbackEvents) {
                    if (seenIds.add(e.getId())) events.add(e);
                }
            }
        }

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
            String policyName = StringUtils.defaultIfBlank(event.getFilterId(), "(unknown policy)");
            List<String> candidateTypes;
            if (subCategory != null && subCategory.regionMatches(true, 0, PII_PREFIX, 0, PII_PREFIX.length())) {
                // Exactly labeled — the one true type is known.
                candidateTypes = Collections.singletonList(subCategory.substring(PII_PREFIX.length()));
            } else {
                GuardrailPolicies fallbackPolicy = piiOnlyPolicyByLowerName.get(lower(event.getFilterId()));
                if (fallbackPolicy == null || fallbackPolicy.getPiiTypes() == null) {
                    continue; // neither exactly labeled nor a known PII-only policy fallback
                }
                // Fallback — subCategory doesn't say which type, so try every type this PII-only
                // policy is configured to detect; a match against any of them is a true positive.
                candidateTypes = fallbackPolicy.getPiiTypes().stream()
                        .map(GuardrailPolicies.PiiType::getType)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
            }

            PolicyStats stats = byPolicy.computeIfAbsent(policyName, PolicyStats::new);
            List<String> checkableTypes = candidateTypes.stream().filter(PiiPatterns::isCheckable).collect(Collectors.toList());
            if (checkableTypes.isEmpty()) {
                notCheckableCount++;
                continue;
            }
            String evidenceText = EvidenceText.extract(event);
            String matchedType = checkableTypes.stream().filter(t -> PiiPatterns.rawPatternPresent(t, evidenceText)).findFirst().orElse(null);
            if (matchedType != null) {
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
                    stats.sampleFalsePositive = new SampleFp(checkableTypes.get(0), evidenceText, credentialLabel);
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
            severity = worst.precision() < 0.1 ? "CRITICAL"
                    : (worst.precision() < 0.5 ? "HIGH" : (worst.precision() < 0.9 ? "MEDIUM" : "LOW"));
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
        if (!fallbackPolicyNames.isEmpty()) {
            caveats.add("A hit is counted as PII if its subCategory says so, or if it came from a policy configured to do nothing but PII detection — subCategory alone understates this on some accounts. For a fallback hit, every PII type that policy checks for is tried against the evidence; a match on any of them counts as a true positive.");
        }
        if (anyFetchCapped) {
            caveats.add("Violations examined are capped at " + EVENT_PAGE_LIMIT + " per lookup; more may exist beyond this page.");
        }

        String concern = null;
        String impact = null;
        String remediation = null;
        if (worst != null) {
            concern = String.format(Locale.US,
                    "Policy \"%s\" is estimated %s precise — %s of the %s checkable violations it caught look like "
                            + "false positives, not real PII.",
                    worst.policyName, InsightUtil.percent(worst.precision()),
                    InsightUtil.count(worst.falsePositive, "violations"), InsightUtil.count(worst.checkedTotal(), "hits"));
            impact = "A noisy policy like this erodes trust in the alert queue — the team either starts ignoring its "
                    + "alerts or, worse, disables it outright, which loses the real hits along with the noise.";
            remediation = worst.falsePositiveLooksLikeCredential > 0
                    ? String.format(Locale.US, "Some of \"%s\"'s false positives look like credentials rather than "
                            + "PII — check the Credential Exposure insight, then narrow this policy's conditions to "
                            + "cut the noise without disabling it.", worst.policyName)
                    : String.format(Locale.US, "Narrow \"%s\"'s match conditions (tighter pattern, added context "
                            + "requirements) to cut the false-positive rate without disabling it outright.", worst.policyName);
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
        result.setConcern(concern);
        result.setImpact(impact);
        result.setRemediation(remediation);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(worst));
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
        return "declared " + InsightUtil.humanizeSubCategory(PII_PREFIX + sample.piiType) + ": " + truncated;
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }


    private static List<InsightResult.Cta> buildCtas(PolicyStats worst) {
        if (worst == null) {
            return new ArrayList<>();
        }
        // "?policy=<name>" is what both destinations actually read — GuardrailPolicies.jsx opens
        // that policy's edit drawer, ViolationsPage.jsx seeds its existing policy-filter.
        Map<String, Object> policyParams = InsightUtil.guardrailPolicyParams(worst.policyName);

        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(new InsightResult.Cta("add_policy_conditions", "Add policy conditions",
                "BULK_ACTION", "/dashboard/guardrails/policies", policyParams, true));
        if (worst.falsePositiveLooksLikeCredential > 0) {
            ctas.add(new InsightResult.Cta("reclassify_as_credential", "Reclassify as credential detection",
                    "NAVIGATE", "/dashboard/guardrails/policies", policyParams, false));
        }
        ctas.add(new InsightResult.Cta("mark_false_positive", "Mark false positive",
                "NAVIGATE", "/dashboard/guardrails/violations", policyParams, false));
        return ctas;
    }
}
