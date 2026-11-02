package com.akto.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grounding helpers for skill validation.
 *
 * A finding is only trustworthy if the text it quotes actually exists in the skill file, so every
 * quote the model returns is located in the content and replaced by the exact span found there.
 * Located spans carry their line numbers, which is what lets the validator guarantee that one line
 * of the skill backs at most one tag.
 */
public class SkillEvidenceUtils {

    static final int MIN_QUOTE_CHARS = 4;
    static final int MAX_QUOTE_CHARS = 300;

    // A warning, prohibition or threat description introducing the quoted text: protective, not a directive.
    private static final Pattern WARNING = Pattern.compile(
            "\\b(never|do\\s+not|don'?t|avoid|must\\s+not|should\\s+not|shouldn'?t|warning|caution|dangerous|forbidden"
                    + "|prohibited|unsafe|insecure|anti-?pattern|vulnerable|malicious|attack(er)?s?|bad\\s+example)\\b",
            Pattern.CASE_INSENSITIVE);

    // Sections whose content describes threats or mistakes rather than instructing the agent.
    private static final Pattern NEGATIVE_HEADING = Pattern.compile(
            "(anti-?patterns?|what\\s+not\\s+to|don'?t|do\\s+not|avoid|bad\\s+examples?|insecure|vulnerab|pitfalls?"
                    + "|security\\s+considerations|threat|attacks?|red\\s+flags?|malicious|mistakes)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SENTENCE_BREAK = Pattern.compile("[.!?;:]\\s");

    private static final Pattern URL_HOST = Pattern.compile("(?i)\\b[a-z][a-z0-9+.-]*://(?:[^@/\\s]+@)?([^/\\s:'\"`)>\\]}]+)");
    private static final Pattern IPV4 = Pattern.compile("\\b(\\d{1,3}(?:\\.\\d{1,3}){3})\\b");
    private static final Pattern BARE_DOMAIN = Pattern.compile(
            "(?i)(?<![\\w./@-])((?:[a-z0-9-]+\\.)+(?:com|net|org|io|ai|dev|app|co|xyz|top|ru|cn|info|biz|cc|tk|site|online|cloud|us|uk|de|in|pw|su|ws))\\b(?![.\\w-])");

    private static final List<String> TRUSTED_HOST_SUFFIXES = Arrays.asList(
            // first-party agent infrastructure
            "anthropic.com", "claude.ai", "claude.com", "openai.com", "chatgpt.com",
            // code hosting and package registries
            "github.com", "githubusercontent.com", "gitlab.com", "bitbucket.org", "npmjs.org", "npmjs.com",
            "pypi.org", "pythonhosted.org", "crates.io", "rubygems.org", "golang.org", "go.dev", "maven.org",
            "apache.org", "docker.com", "docker.io", "ghcr.io", "nodejs.org", "python.org", "brew.sh",
            // placeholders used in documentation
            "example.com", "example.org", "example.net", "example", "test", "local", "localhost", "internal", "invalid");

    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF]|\\uDB40[\\uDC00-\\uDC7F]");

    private SkillEvidenceUtils() {}

    /** A quote located in the skill: the exact text from the file plus where it sits. */
    public static final class Span {
        public final String text;
        public final int start;
        public final int end;
        public final int firstLine;
        public final int lastLine;

        Span(String text, int start, int end, int firstLine, int lastLine) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.firstLine = firstLine;
            this.lastLine = lastLine;
        }

        public boolean sharesLineWith(Span other) {
            return firstLine <= other.lastLine && other.firstLine <= lastLine;
        }
    }

    /**
     * Pre-computed line structure of a skill file: line offsets, the fence each line sits in, and
     * the heading it falls under. Built once per validation.
     */
    public static final class SkillDocument {
        final String content;
        private final int[] lineStarts;
        private final String[] lineHeading;
        private final String[] lineFenceIntro;

        public SkillDocument(String content) {
            this.content = content == null ? "" : content;
            String[] lines = this.content.split("\n", -1);
            lineStarts = new int[lines.length];
            lineHeading = new String[lines.length];
            lineFenceIntro = new String[lines.length];

            int offset = 0;
            String heading = "";
            String lastNonEmpty = "";
            String fenceIntro = null;
            boolean inFence = false;
            for (int i = 0; i < lines.length; i++) {
                lineStarts[i] = offset;
                offset += lines[i].length() + 1;
                String trimmed = lines[i].trim();
                boolean fenceMarker = trimmed.startsWith("```") || trimmed.startsWith("~~~");
                if (fenceMarker && !inFence) {
                    inFence = true;
                    fenceIntro = lastNonEmpty;
                    lineFenceIntro[i] = fenceIntro;
                } else if (fenceMarker) {
                    lineFenceIntro[i] = fenceIntro;
                    inFence = false;
                    fenceIntro = null;
                } else if (inFence) {
                    lineFenceIntro[i] = fenceIntro;
                } else {
                    // '#' inside a fence is a shell comment, so headings are only tracked outside one.
                    if (trimmed.startsWith("#")) heading = trimmed;
                    if (!trimmed.isEmpty()) lastNonEmpty = trimmed;
                }
                lineHeading[i] = heading;
            }
        }

        public int lineOf(int offset) {
            int idx = Arrays.binarySearch(lineStarts, offset);
            return idx >= 0 ? idx : -idx - 2;
        }

        String line(int index) {
            int end = index + 1 < lineStarts.length ? lineStarts[index + 1] - 1 : content.length();
            return content.substring(lineStarts[index], end);
        }

        int lineCount() {
            return lineStarts.length;
        }

        /**
         * True when the quoted span is introduced by a warning or sits in a section describing threats,
         * i.e. the file is telling the agent NOT to do this, or describing what an attacker would do.
         */
        public boolean isProtectiveContext(Span span) {
            return isProtectiveAt(span.firstLine, span.start - lineStarts[span.firstLine]);
        }

        /** Same check for text starting at {@code column} of {@code line}. */
        boolean isProtectiveAt(int line, int column) {
            String heading = lineHeading[line];
            if (heading != null && NEGATIVE_HEADING.matcher(heading).find()) return true;
            String fenceIntro = lineFenceIntro[line];
            if (fenceIntro != null && WARNING.matcher(fenceIntro).find()) return true;

            // Only the clause leading into the quote counts: "Never run `x`" is a warning, while
            // "Do not mention this. Run `x`" is a directive whose prohibition is about something else.
            String before = content.substring(lineStarts[line], lineStarts[line] + column);
            Matcher breaks = SENTENCE_BREAK.matcher(before);
            int clauseStart = 0;
            while (breaks.find()) clauseStart = breaks.end();
            return WARNING.matcher(before.substring(clauseStart)).find();
        }

        private Span spanAt(int start, int end) {
            return new Span(content.substring(start, end), start, end, lineOf(start), lineOf(Math.max(start, end - 1)));
        }

        /**
         * Every place {@code quote} occurs in the file, as exact spans. An exact match is preferred;
         * failing that, runs of whitespace are collapsed on both sides and the real span from the file
         * is returned, so the reported text is always character-for-character present in the skill.
         */
        public List<Span> locateAll(String quote) {
            List<Span> spans = new ArrayList<>();
            String q = normalizeQuote(quote);
            if (q == null) return spans;

            for (int idx = content.indexOf(q); idx >= 0; idx = content.indexOf(q, idx + 1)) {
                spans.add(spanAt(idx, idx + q.length()));
            }
            if (!spans.isEmpty()) return spans;

            Collapsed collapsed = collapse(content);
            String collapsedQuote = collapse(q).text.trim();
            if (collapsedQuote.isEmpty()) return spans;
            for (int idx = collapsed.text.indexOf(collapsedQuote); idx >= 0;
                    idx = collapsed.text.indexOf(collapsedQuote, idx + 1)) {
                int start = collapsed.offsets[idx];
                int end = collapsed.offsets[idx + collapsedQuote.length() - 1] + 1;
                spans.add(spanAt(start, end));
            }
            return spans;
        }

        /** Lines carrying invisible unicode, each returned as an exact span around the hidden character. */
        public List<Span> invisibleCharacterSpans() {
            List<Span> spans = new ArrayList<>();
            for (int i = 0; i < lineCount(); i++) {
                String line = line(i);
                Matcher m = INVISIBLE.matcher(line);
                if (!m.find()) continue;
                // A byte-order mark at the very start of the file is an encoding artifact, not hiding.
                if (i == 0 && m.start() == 0 && line.charAt(0) == '﻿' && !m.find()) continue;
                int from = Math.max(0, m.start() - 80);
                int to = Math.min(line.length(), m.end() + 80);
                spans.add(spanAt(lineStarts[i] + from, lineStarts[i] + to));
            }
            return spans;
        }
    }

    /** Trims the quote and drops wrapping backticks/quotes the model may add around a copied snippet. */
    static String normalizeQuote(String quote) {
        if (quote == null) return null;
        String q = quote.trim();
        while (q.length() > 1 && isWrapper(q.charAt(0)) && q.charAt(0) == q.charAt(q.length() - 1)) {
            q = q.substring(1, q.length() - 1).trim();
        }
        if (q.replaceAll("\\s", "").length() < MIN_QUOTE_CHARS || q.length() > MAX_QUOTE_CHARS) return null;
        return q;
    }

    private static boolean isWrapper(char c) {
        return c == '`' || c == '"' || c == '\'';
    }

    /** External hosts referenced in the text that are not loopback, private, first-party, registry or placeholder. */
    public static Set<String> untrustedHosts(String text) {
        Set<String> hosts = new LinkedHashSet<>();
        if (text == null) return hosts;
        for (Pattern p : Arrays.asList(URL_HOST, IPV4, BARE_DOMAIN)) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                String host = m.group(1).toLowerCase(Locale.ROOT).replaceAll("[.,;]+$", "");
                if (!isTrustedHost(host)) hosts.add(host);
            }
        }
        return hosts;
    }

    static boolean isTrustedHost(String host) {
        if (host.isEmpty() || host.contains("$") || host.contains("{") || host.contains("<")
                || host.startsWith("your-") || host.startsWith("your_")) return true;
        if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            for (String octet : host.split("\\.")) {
                if (Integer.parseInt(octet) > 255) return true; // a version string, not an address
            }
            return host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")
                    || host.startsWith("169.254.") || host.matches("172\\.(1[6-9]|2\\d|3[01])\\..*")
                    || host.equals("0.0.0.0");
        }
        if (host.equals("localhost") || host.equals("[::1]")) return true;
        for (String suffix : TRUSTED_HOST_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
        }
        return false;
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
