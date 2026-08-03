package com.akto.action;

import com.akto.dto.OwaspAstCategory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Grounding helpers for skill validation.
 *
 * A finding is only trustworthy if the text it quotes actually exists in the skill file, so every
 * quote the model returns is located in the content before it is reported. Quotes that cannot be
 * located are dropped, and the survivors collapse to one event per rule: a rule appears at most
 * once, carrying every distinct quote that demonstrates it.
 */
public class SkillEvidenceUtils {

    /** Separates the quotes collected under one rule. */
    public static final String EVIDENCE_SEPARATOR = "\n";

    private SkillEvidenceUtils() {}

    /**
     * Returns the exact substring of {@code content} that the model quoted, or null if the quote is
     * not in the file. An exact match wins; otherwise the lookup retries with runs of whitespace
     * collapsed and returns the real span from {@code content}. The value handed back is therefore
     * always character-for-character present in the skill file.
     */
    public static String locate(String content, String evidence) {
        if (content == null || evidence == null) return null;
        String quote = evidence.trim();
        if (quote.isEmpty()) return null;
        if (content.contains(quote)) return quote;

        Collapsed collapsedContent = collapse(content);
        String collapsedQuote = collapse(quote).text.trim();
        if (collapsedQuote.isEmpty()) return null;

        int start = collapsedContent.text.indexOf(collapsedQuote);
        if (start < 0) return null;
        int end = start + collapsedQuote.length() - 1;
        return content.substring(collapsedContent.offsets[start], collapsedContent.offsets[end] + 1);
    }

    /**
     * Locates an evidence value that may carry several quotes joined by {@link #EVIDENCE_SEPARATOR}.
     * A genuinely multi-line quote is tried whole first; failing that, each line is located on its
     * own and the ones found in the file are rejoined. Returns null when no line can be located.
     */
    public static String locateQuotes(String content, String evidence) {
        if (evidence == null) return null;
        String whole = locate(content, evidence);
        if (whole != null) return whole;

        List<String> found = new ArrayList<>();
        for (String line : evidence.split("\\R")) {
            String located = locate(content, line);
            if (located != null && !found.contains(located)) found.add(located);
        }
        return found.isEmpty() ? null : String.join(EVIDENCE_SEPARATOR, found);
    }

    /**
     * Keeps only the events whose evidence is present in {@code content}, rewriting each quote to the
     * verbatim span from the file, then collapses them to one event per rule. The surviving event
     * carries every distinct quote for that rule joined by {@link #EVIDENCE_SEPARATOR}, the highest
     * risk score, and the union of the OWASP categories.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> verifyAndMergeEvents(List<?> rawEvents, String content) {
        Map<String, Map<String, Object>> byRule = new LinkedHashMap<>();
        if (rawEvents == null) return new ArrayList<>();

        for (Object raw : rawEvents) {
            if (!(raw instanceof Map)) continue;
            Map<String, Object> event = new LinkedHashMap<>((Map<String, Object>) raw);
            Object quoted = event.get("evidence");
            String located = locateQuotes(content, quoted == null ? null : String.valueOf(quoted));
            if (located == null) continue;
            event.put("evidence", located);

            String rule = event.get("rule") == null ? "" : String.valueOf(event.get("rule")).trim().toLowerCase();
            Map<String, Object> existing = byRule.get(rule);
            if (existing == null) {
                byRule.put(rule, event);
            } else {
                merge(existing, event);
            }
        }
        return new ArrayList<>(byRule.values());
    }

    /** Validated OWASP ids attached to the given events. */
    public static Set<String> categoriesOf(List<Map<String, Object>> events) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> event : events) {
            addValidIds(event.get("owaspCategories"), ids);
        }
        return ids;
    }

    /** Adds every value of {@code raw} that resolves to a known OWASP AST category into {@code target}. */
    public static void addValidIds(Object raw, Set<String> target) {
        if (!(raw instanceof List)) return;
        for (Object id : (List<?>) raw) {
            OwaspAstCategory category = OwaspAstCategory.fromId(String.valueOf(id));
            if (category != null) target.add(category.getId());
        }
    }

    private static void merge(Map<String, Object> target, Map<String, Object> incoming) {
        for (String quote : String.valueOf(incoming.get("evidence")).split(Pattern.quote(EVIDENCE_SEPARATOR))) {
            addEvidence(target, quote);
        }
        if (riskOf(incoming) > riskOf(target)) {
            target.put("reason", incoming.get("reason"));
            target.put("riskScore", incoming.get("riskScore"));
        }
        Set<String> categories = new LinkedHashSet<>();
        addValidIds(target.get("owaspCategories"), categories);
        addValidIds(incoming.get("owaspCategories"), categories);
        target.put("owaspCategories", new ArrayList<>(categories));
    }

    /**
     * Adds one more quote to a rule's evidence. Quotes describing the same statement (identical,
     * nested or overlapping spans) do not stack — the longer of the two wins its slot.
     */
    private static void addEvidence(Map<String, Object> target, String incoming) {
        List<String> quotes = new ArrayList<>(
                Arrays.asList(String.valueOf(target.get("evidence")).split(Pattern.quote(EVIDENCE_SEPARATOR))));
        String incomingKey = collapse(incoming).text.trim().toLowerCase();

        for (int i = 0; i < quotes.size(); i++) {
            String existingKey = collapse(quotes.get(i)).text.trim().toLowerCase();
            if (existingKey.contains(incomingKey)) return;
            if (incomingKey.contains(existingKey)) {
                quotes.set(i, incoming);
                target.put("evidence", String.join(EVIDENCE_SEPARATOR, quotes));
                return;
            }
        }
        quotes.add(incoming);
        target.put("evidence", String.join(EVIDENCE_SEPARATOR, quotes));
    }

    private static double riskOf(Map<String, Object> event) {
        Object score = event.get("riskScore");
        return score instanceof Number ? ((Number) score).doubleValue() : 0.0;
    }

    /** Text with runs of whitespace collapsed to one space, plus the origin offset of each kept char. */
    private static final class Collapsed {
        final String text;
        final int[] offsets;

        Collapsed(String text, int[] offsets) {
            this.text = text;
            this.offsets = offsets;
        }
    }

    private static Collapsed collapse(String source) {
        StringBuilder sb = new StringBuilder(source.length());
        int[] offsets = new int[source.length()];
        boolean previousWasWhitespace = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            boolean whitespace = Character.isWhitespace(c);
            if (whitespace && previousWasWhitespace) continue;
            previousWasWhitespace = whitespace;
            offsets[sb.length()] = i;
            sb.append(whitespace ? ' ' : c);
        }
        return new Collapsed(sb.toString(), offsets);
    }
}
