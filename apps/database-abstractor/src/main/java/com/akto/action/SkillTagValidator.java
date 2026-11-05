package com.akto.action;

import com.akto.action.SkillEvidenceUtils.SkillDocument;
import com.akto.action.SkillEvidenceUtils.Span;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Turns the model's claimed findings into tags that can be defended line by line.
 *
 * A finding survives only when:
 *   - it names a known criterion (the tag is derived from the criterion, never taken from the model)
 *   - every quote it keeps is an exact span of the skill file, outside warnings and threat write-ups
 *   - its quotes carry the criterion's literal signal (anchor, companion, untrusted host)
 *   - none of its lines already backs a higher-precedence finding: one line, one tag
 * Malicious (AST01) findings must additionally be backed by a malicious verdict and be unexplained by
 * the skill's purpose; otherwise they fall back to their factual criterion or are dropped.
 */
public class SkillTagValidator {

    static final double DESCRIPTION_MISMATCH_THRESHOLD = 0.7;

    private SkillTagValidator() {}

    public static final class Finding {
        public final SkillTagCriteria criteria;
        public final String reason;
        public final boolean purposeAligned;
        public final List<Span> spans;

        Finding(SkillTagCriteria criteria, String reason, boolean purposeAligned, List<Span> spans) {
            this.criteria = criteria;
            this.reason = reason;
            this.purposeAligned = purposeAligned;
            this.spans = spans;
        }

        public String evidenceText() {
            return spans.stream().map(s -> s.text).collect(Collectors.joining("\n"));
        }
    }

    private static final class Candidate {
        String reason;
        boolean purposeAligned;
        final List<Span> spans = new ArrayList<>();

        Candidate(String reason, boolean purposeAligned) {
            this.reason = reason;
            this.purposeAligned = purposeAligned;
        }
    }

    /**
     * @param rawFindings          the model's "findings" array
     * @param verdictMalicious     the model's skill-level verdict; AST01 findings need it to be true
     * @param descriptionMatchScore the model's name/description vs behaviour score; AST04 needs a mismatch
     */
    @SuppressWarnings("unchecked")
    public static List<Finding> validate(List<?> rawFindings, SkillDocument doc, boolean verdictMalicious,
            double descriptionMatchScore) {
        TreeMap<SkillTagCriteria, Candidate> candidates = new TreeMap<>();

        if (rawFindings != null) {
            for (Object raw : rawFindings) {
                if (!(raw instanceof Map)) continue;
                Map<String, Object> finding = (Map<String, Object>) raw;
                SkillTagCriteria criteria = SkillTagCriteria.fromId(stringOf(finding.get("criterion")));
                // Invisible characters are found by the scanner below; the model cannot reproduce them reliably.
                if (criteria == null || criteria == SkillTagCriteria.AST08_INVISIBLE_UNICODE) continue;
                // A missing flag is read as "explained by the purpose", the safe side for the verdict.
                boolean purposeAligned = !Boolean.FALSE.equals(finding.get("purposeAligned"));

                if (criteria.isMalicious() && (!verdictMalicious || purposeAligned)) {
                    criteria = criteria.demoted();
                    if (criteria == null) continue;
                } else if (verdictMalicious && !purposeAligned && criteria.promoted() != null) {
                    // The model judged the skill malicious but filed the line under its factual
                    // criterion. Try the malicious one; it still has to prove itself below, and falls
                    // back to this criterion when it does not.
                    criteria = criteria.promoted();
                }
                if (criteria == SkillTagCriteria.AST04_UNDISCLOSED_BEHAVIOR
                        && descriptionMatchScore >= DESCRIPTION_MISMATCH_THRESHOLD) continue;

                Candidate candidate = candidates.computeIfAbsent(criteria,
                        c -> new Candidate(stringOf(finding.get("reason")), purposeAligned));
                candidate.purposeAligned &= purposeAligned;
                for (String quote : quotesOf(finding.get("evidence"))) {
                    Span span = firstDirectiveSpan(doc, quote);
                    if (span != null) addSpan(candidate.spans, span);
                }
            }
        }

        List<Span> invisible = doc.invisibleCharacterSpans();
        if (!invisible.isEmpty()) {
            Candidate candidate = new Candidate(
                    "Contains zero-width, bidi-override or tag characters that are invisible when the skill is read.", false);
            invisible.forEach(span -> addSpan(candidate.spans, span));
            candidates.put(SkillTagCriteria.AST08_INVISIBLE_UNICODE, candidate);
        }

        // Highest precedence first, so a line shared by two findings goes to the more specific one.
        List<Finding> accepted = new ArrayList<>();
        List<Span> claimed = new ArrayList<>();
        Entry<SkillTagCriteria, Candidate> next;
        while ((next = candidates.pollFirstEntry()) != null) {
            SkillTagCriteria criteria = next.getKey();
            Candidate candidate = next.getValue();
            List<Span> unclaimed = candidate.spans.stream()
                    .filter(span -> claimed.stream().noneMatch(span::sharesLineWith))
                    .collect(Collectors.toList());
            List<Span> supported = supportedSpans(criteria, unclaimed);

            if (supported != null) {
                claimed.addAll(supported);
                accepted.add(new Finding(criteria, candidate.reason, candidate.purposeAligned, supported));
            } else if (criteria.demoted() != null) {
                Candidate fallback = candidates.computeIfAbsent(criteria.demoted(),
                        c -> new Candidate(candidate.reason, candidate.purposeAligned));
                candidate.spans.forEach(span -> addSpan(fallback.spans, span));
            }
        }
        return accepted;
    }

    static final int MAX_CANDIDATE_LINES = 80;
    private static final int MAX_CANDIDATE_LINE_CHARS = 200;

    /**
     * Lines of the skill that carry some criterion's literal signal and are not a warning or threat
     * write-up, listed for the model to confirm or reject. The model is good at judging a line and poor
     * at finding every one in a long file, so the search is done here and the judgement there.
     */
    static List<String> candidateLines(SkillDocument doc) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < doc.lineCount() && lines.size() < MAX_CANDIDATE_LINES; i++) {
            String text = doc.line(i);
            if (text.trim().isEmpty()) continue;
            List<String> matches = new ArrayList<>();
            for (SkillTagCriteria criteria : SkillTagCriteria.values()) {
                if (!criteria.isCandidateSource()) continue;
                int at = criteria.anchorAt(text);
                if (at < 0 || doc.isProtectiveAt(i, at)) continue;
                if (criteria.requiresUntrustedHost() && SkillEvidenceUtils.untrustedHosts(text).isEmpty()) continue;
                matches.add(criteria.getId() + " " + criteria.getLabel());
            }
            if (matches.isEmpty()) continue;
            String shown = text.trim();
            if (shown.length() > MAX_CANDIDATE_LINE_CHARS) shown = shown.substring(0, MAX_CANDIDATE_LINE_CHARS) + " [...]";
            lines.add("L" + (i + 1) + ": " + shown + "\n      may match: " + String.join(" | ", matches));
        }
        return lines;
    }

    /**
     * The spans that demonstrate {@code criteria}, or null when they do not prove it. Spans that carry
     * none of the criterion's signals are dropped even if the finding survives — every reported line
     * has to show the behaviour on its own.
     */
    static List<Span> supportedSpans(SkillTagCriteria criteria, List<Span> spans) {
        boolean anchored = false;
        boolean companioned = !criteria.hasCompanion();
        boolean hostFound = !criteria.requiresUntrustedHost();
        List<Span> supported = new ArrayList<>();
        for (Span span : spans) {
            boolean anchors = criteria.anchors(span.text);
            boolean companions = criteria.companions(span.text);
            boolean host = criteria.requiresUntrustedHost() && !SkillEvidenceUtils.untrustedHosts(span.text).isEmpty();
            if (!anchors && !companions && !host) continue;
            anchored |= anchors;
            companioned |= companions;
            hostFound |= host;
            supported.add(span);
        }
        return anchored && companioned && hostFound ? supported : null;
    }

    /** The first occurrence of the quote that is a directive rather than a warning or threat write-up. */
    static Span firstDirectiveSpan(SkillDocument doc, String quote) {
        for (Span span : doc.locateAll(quote)) {
            if (!doc.isProtectiveContext(span)) return span;
        }
        return null;
    }

    /** Adds a span unless one on the same line is already there; the longer of the two keeps the slot. */
    static void addSpan(List<Span> spans, Span incoming) {
        for (int i = 0; i < spans.size(); i++) {
            Span existing = spans.get(i);
            if (!existing.sharesLineWith(incoming)) continue;
            if (incoming.text.length() > existing.text.length()) spans.set(i, incoming);
            return;
        }
        spans.add(incoming);
    }

    private static List<String> quotesOf(Object evidence) {
        List<String> quotes = new ArrayList<>();
        if (evidence instanceof List) {
            for (Object quote : (List<?>) evidence) {
                if (quote != null) quotes.add(String.valueOf(quote));
            }
        } else if (evidence != null) {
            quotes.addAll(Arrays.asList(String.valueOf(evidence).split("\\R")));
        }
        return quotes;
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
