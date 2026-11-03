package com.akto.action.skill_validation_v3;

import com.akto.action.SkillTagCriteria;
import com.akto.action.skill_validation_v3.SkillEvidenceV3.SkillDocument;
import com.akto.action.skill_validation_v3.SkillEvidenceV3.Span;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns the model's claimed findings into tags that can be defended line by line.
 *
 * A finding survives only when:
 *   - it names a known criterion (the tag is derived from the criterion, never taken from the model)
 *   - every quote it keeps is an exact span of the skill file, outside warnings and threat write-ups
 *   - its quotes carry the criterion's literal signal (anchor, companion, untrusted host)
 *   - none of its lines already backs a higher-precedence finding: one line, one tag
 * Tags are information and never carry the malicious verdict: an AST01 criterion among them falls back
 * to its factual criterion or is dropped. The verdict comes from the model's separate malicious claim,
 * which becomes the one AST01 finding when its own quotes prove it, and otherwise falls back the same way.
 */
public class SkillTagValidatorV3 {

    static final double DESCRIPTION_MISMATCH_THRESHOLD = 0.7;

    // Behaviour most skills need to do their job (an unpinned install of the skill's own tool): a tag
    // only when the purpose does not explain it.
    private static final Set<SkillTagCriteria> ROUTINE_CRITERIA = EnumSet.of(SkillTagCriteria.AST07_UNPINNED);

    // A path under the user's home, and what follows it.
    private static final Pattern HOME_PATH = Pattern.compile(
            "(?:~|\\$home\\b|\\$\\{home\\}|%userprofile%|/users/[^/\\s]+|/home/[^/\\s]+|/root)(/[^\\s'\"`)>\\]}]*)?",
            Pattern.CASE_INSENSITIVE);

    // The agent's own folders, and the tool caches and config every CLI keeps under ~. Not the user's files.
    private static final Pattern OWN_HOME_DIR = Pattern.compile(
            "^/\\.(codex|claude|cursor|agents|kiro|windsurf|codeium|copilot|vscode|antigravity|cache|config|npm|local/share)(/|$)",
            Pattern.CASE_INSENSITIVE);

    private SkillTagValidatorV3() {}

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
     * @param rawTags              the model's "tags" array
     * @param maliciousClaim       the model's malicious object when it claims the skill is malicious (and
     *                             the skill is not vendor-shipped), else null
     * @param descriptionMatchScore the model's name/description vs behaviour score; AST04 needs a mismatch
     * @param vendorShipped        a built-in skill: only behaviour its purpose does not explain is tagged
     */
    @SuppressWarnings("unchecked")
    public static List<Finding> validate(List<?> rawTags, Map<String, Object> maliciousClaim, SkillDocument doc,
            double descriptionMatchScore, boolean vendorShipped) {
        TreeMap<SkillTagCriteria, Candidate> candidates = new TreeMap<>();

        if (rawTags != null) {
            for (Object raw : rawTags) {
                if (!(raw instanceof Map)) continue;
                Map<String, Object> tag = (Map<String, Object>) raw;
                SkillTagCriteria criteria = SkillTagCriteria.fromId(stringOf(tag.get("criterion")));
                // Invisible characters are found by the scanner below; the model cannot reproduce them reliably.
                if (criteria == null || criteria == SkillTagCriteria.AST08_INVISIBLE_UNICODE) continue;
                // The verdict lives in the malicious claim; a tag only ever states the behaviour.
                if (criteria.isMalicious()) {
                    criteria = criteria.demoted();
                    if (criteria == null) continue;
                }
                if (criteria == SkillTagCriteria.AST04_UNDISCLOSED_BEHAVIOR
                        && descriptionMatchScore >= DESCRIPTION_MISMATCH_THRESHOLD) continue;
                // A missing flag is read as "explained by the purpose".
                boolean purposeAligned = !Boolean.FALSE.equals(tag.get("purposeAligned"));
                addCandidate(candidates, criteria, stringOf(tag.get("reason")), purposeAligned,
                        locate(doc, tag.get("evidence")));
            }
        }

        if (maliciousClaim != null) {
            List<Span> spans = locate(doc, maliciousClaim.get("evidence"));
            SkillTagCriteria criteria = maliciousCriterion(stringOf(maliciousClaim.get("criterion")), spans);
            if (criteria != null) {
                addCandidate(candidates, criteria, stringOf(maliciousClaim.get("reason")), false, spans);
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
            // Explained by the purpose and not a real risk on its own: not a tag, and its lines stay free.
            if (candidate.purposeAligned && (vendorShipped || ROUTINE_CRITERIA.contains(criteria))) continue;
            // A built-in skill's sudo installs the packages it needs; the vendor ships it, so no tag.
            if (vendorShipped && criteria == SkillTagCriteria.AST06_ROOT) continue;
            List<Span> unclaimed = candidate.spans.stream()
                    .filter(span -> claimed.stream().noneMatch(span::sharesLineWith))
                    .filter(span -> criteria != SkillTagCriteria.AST03_OUTSIDE_WORKSPACE || !onlyOwnHomeDirs(span.text))
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

    /**
     * The AST01 criterion the malicious claim is judged under. A factual criterion is read as its
     * malicious counterpart. When the quotes do not prove the claimed criterion, the first AST01
     * criterion they do prove is used; failing that the claimed one is kept, so the claim falls back to
     * its factual tag. Null when there is no criterion at all: the claim has nothing to stand on.
     */
    static SkillTagCriteria maliciousCriterion(String claimed, List<Span> spans) {
        SkillTagCriteria criteria = SkillTagCriteria.fromId(claimed);
        if (criteria != null && !criteria.isMalicious()) criteria = criteria.promoted();
        if (criteria != null && supportedSpans(criteria, spans) != null) return criteria;
        for (SkillTagCriteria candidate : SkillTagCriteria.values()) {
            if (candidate.isMalicious() && supportedSpans(candidate, spans) != null) return candidate;
        }
        return criteria;
    }

    private static void addCandidate(TreeMap<SkillTagCriteria, Candidate> candidates, SkillTagCriteria criteria,
            String reason, boolean purposeAligned, List<Span> spans) {
        Candidate candidate = candidates.computeIfAbsent(criteria, c -> new Candidate(reason, purposeAligned));
        candidate.purposeAligned &= purposeAligned;
        spans.forEach(span -> addSpan(candidate.spans, span));
    }

    private static List<Span> locate(SkillDocument doc, Object evidence) {
        List<Span> spans = new ArrayList<>();
        for (String quote : quotesOf(evidence)) {
            Span span = firstDirectiveSpan(doc, quote);
            if (span != null) addSpan(spans, span);
        }
        return spans;
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
                if (criteria.requiresUntrustedHost() && SkillEvidenceV3.untrustedHosts(text).isEmpty()) continue;
                matches.add(criteria.getId() + " " + criteria.getLabel());
            }
            if (matches.isEmpty()) continue;
            String shown = text.trim();
            if (shown.length() > MAX_CANDIDATE_LINE_CHARS) shown = shown.substring(0, MAX_CANDIDATE_LINE_CHARS) + " [...]";
            int part = doc.partOfLine(i);
            String where = (part == 0 ? "" : doc.partName(part) + " ") + "L" + (doc.lineInPart(i) + 1);
            lines.add(where + ": " + shown + "\n      may match: " + String.join(" | ", matches));
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
            boolean host = criteria.requiresUntrustedHost() && !SkillEvidenceV3.untrustedHosts(span.text).isEmpty();
            if (!anchors && !companions && !host) continue;
            anchored |= anchors;
            companioned |= companions;
            hostFound |= host;
            supported.add(span);
        }
        return anchored && companioned && hostFound ? supported : null;
    }

    /**
     * True when every home path the text names is the agent's own folder or a tool's cache or config
     * (~/.codex/skills, ~/.cache/wrangler): touching those is not touching the user's files. A path
     * that climbs out with ".." (~/.cache/../Documents) is never one of them.
     */
    static boolean onlyOwnHomeDirs(String text) {
        Matcher m = HOME_PATH.matcher(text);
        boolean found = false;
        while (m.find()) {
            found = true;
            String rest = m.group(1);
            if (rest == null || rest.contains("..") || !OWN_HOME_DIR.matcher(rest).find()) return false;
        }
        return found;
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
