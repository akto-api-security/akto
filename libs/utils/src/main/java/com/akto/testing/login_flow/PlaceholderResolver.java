package com.akto.testing.login_flow;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Resolves {@code ${KEY(|FILTER)*}} placeholders against a workflow / login-flow values map.
 * Grammar, semantics and the nil/absent/empty table are in CLAUDE.md in this package.
 * Error messages name keys, filters and patterns, never resolved values.
 */
public final class PlaceholderResolver {

    public static final int MAX_REGEX_INPUT_CHARS = 1024 * 1024;
    private static final int MAX_QUOTED_PLACEHOLDER_CHARS = 200;

    private static final Pattern NODE_KEY = Pattern.compile("^(x|step)(\\d+)(\\.[\\w\\-\\[\\].]+)$");
    private static final Pattern AKTO_KEY = Pattern.compile("^AKTO\\.changes_info\\..*$");
    private static final Pattern COOKIE_KEY = Pattern.compile("^cookies\\.([^\\s;,=|{}]+)$");
    private static final Pattern JS_EXPRESSION = Pattern.compile("#\\[[\\s\\S]*?\\]#");
    private static final Pattern REGEX_KEY = Pattern.compile("^(x\\d+)\\.response\\.regex(\\.\\d+)?$");
    private static final Pattern NODE_PREFIX = Pattern.compile("^(x\\d+)\\..*$");

    public static final String JS_EXPRESSIONS_NOT_EVALUATED = "JS expressions (#[ ... ]#) are not evaluated here; "
            + "use filters, e.g. ${x1.response.body.token|regex:<pattern>} or |urlencode";

    private static final String COOKIES = "cookies";
    private static final String FILTER_REGEX = "regex:";
    private static final String FILTER_URLENCODE = "urlencode";
    private static final String FILTER_RAW = "raw";

    private PlaceholderResolver() {}

    public static final class Options {
        private final boolean escapeString;
        private final boolean throwOnMissing;
        private final boolean loginFlow;
        private final String currentNodeId;
        private final String requestUrl;
        private final boolean rejectJsExpressions;
        private final boolean jsStringEscaping;

        private Options(boolean escapeString, boolean throwOnMissing, boolean loginFlow, String currentNodeId,
                        String requestUrl, boolean rejectJsExpressions, boolean jsStringEscaping) {
            this.escapeString = escapeString;
            this.throwOnMissing = throwOnMissing;
            this.loginFlow = loginFlow;
            this.currentNodeId = currentNodeId;
            this.requestUrl = requestUrl;
            this.rejectJsExpressions = rejectJsExpressions;
            this.jsStringEscaping = jsStringEscaping;
        }

        /** Behaviour of the original replaceVariables: x/step keys and AKTO.changes_info, plus filters. */
        public static Options standard(boolean escapeString, boolean throwOnMissing) {
            return new Options(escapeString, throwOnMissing, false, null, null, false, false);
        }

        /**
         * Login-flow mode: stepK alias, ${cookies} / ${cookies.name}, strict about unknown placeholders.
         * Whether #[ ... ]# JS expressions are evaluated is the caller's decision (see withJsExpressionsRejected).
         * @param currentNodeId node being built (its own and later nodes' cookies are excluded); null = all nodes
         * @param requestUrl URL ${cookies} is scoped to; null makes ${cookies} an error (URLs, auth params)
         */
        public static Options loginFlow(String currentNodeId, String requestUrl) {
            return new Options(true, true, true, currentNodeId, requestUrl, false, false);
        }

        /** Same options, but a template containing a #[ ... ]# block is an error (for callers with no JS evaluation). */
        public Options withJsExpressionsRejected() {
            return new Options(escapeString, throwOnMissing, loginFlow, currentNodeId, requestUrl, true,
                    jsStringEscaping);
        }

        /**
         * Same options for placeholders inside a #[ ... ]# block that the caller will evaluate as JS: values are
         * escaped (including ' -> \', as the original replaceVariables did) so they stay inside JS string literals.
         */
        public Options forJsExpression() {
            return new Options(true, throwOnMissing, loginFlow, currentNodeId, requestUrl, false, true);
        }
    }

    private static final class Placeholder {
        final String text;          // "${...}" as written, for error messages
        final String key;
        final List<String> filters;

        Placeholder(String text, String key, List<String> filters) {
            this.text = text;
            this.key = key;
            this.filters = filters;
        }
    }

    /** Lazily built so templates without ${cookies...} never scan the values map. */
    private static final class JarHolder {
        private final Map<String, Object> valuesMap;
        private final int beforeNode;
        private LoginFlowCookieJar jar;

        JarHolder(Map<String, Object> valuesMap, String currentNodeId) {
            this.valuesMap = valuesMap;
            int n = LoginFlowNodes.nodeNumber(currentNodeId);
            this.beforeNode = n > 0 ? n : Integer.MAX_VALUE;
        }

        LoginFlowCookieJar get() {
            if (jar == null) jar = LoginFlowCookieJar.fromValuesMap(valuesMap, beforeNode);
            return jar;
        }
    }

    public static String resolve(String template, Map<String, Object> valuesMap, Options options)
            throws PlaceholderException {
        if (template == null) return null;
        if (options.rejectJsExpressions && JS_EXPRESSION.matcher(template).find()) {
            throw new PlaceholderException(JS_EXPRESSIONS_NOT_EVALUATED);
        }
        JarHolder jar = new JarHolder(valuesMap, options.currentNodeId);
        StringBuilder out = new StringBuilder(template.length());
        int i = 0;
        while (true) {
            int start = template.indexOf("${", i);
            if (start < 0) {
                out.append(template, i, template.length());
                break;
            }
            out.append(template, i, start);
            int end = findClosingBrace(template, start + 2);
            Placeholder ph = end < 0 ? null : parse(template.substring(start + 2, end), options.loginFlow);
            if (ph == null) {
                if (options.loginFlow) {
                    String shown = end < 0 ? template.substring(start) : template.substring(start, end + 1);
                    throw new PlaceholderException("unsupported placeholder " + quote(shown)
                            + "; use ${xN.<path>}, ${stepK.<path>}, ${cookies} or ${cookies.<name>} "
                            + "with optional |regex:<pattern>, |urlencode, |raw");
                }
                // not a placeholder: keep the text and look for one starting after this '$'
                out.append('$');
                i = start + 1;
                continue;
            }
            String value = evaluate(ph, valuesMap, options, jar);
            out.append(value != null ? value : template.substring(start, end + 1));
            i = end + 1;
        }
        return out.toString();
    }

    /** Index of the '}' closing the placeholder whose body starts at {@code from}, or -1. */
    static int findClosingBrace(String s, int from) {
        int depth = 1;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** null when {@code inner} is not a placeholder this mode understands. */
    private static Placeholder parse(String inner, boolean loginFlow) {
        String text = "${" + inner + "}";
        List<String> segments = splitOnUnescapedPipe(inner);
        String key = segments.get(0);
        List<String> filters = new ArrayList<>();
        boolean filtersValid = true;
        for (int i = 1; i < segments.size(); i++) {
            String f = trimLeading(segments.get(i));
            if (!isKnownFilter(f)) filtersValid = false;
            filters.add(f);
        }

        boolean nodeKey = NODE_KEY.matcher(key).matches();
        boolean cookieKey = loginFlow && (key.equals(COOKIES) || COOKIE_KEY.matcher(key).matches());
        if (nodeKey || cookieKey) {
            if (!filtersValid && !loginFlow) return null;   // was never a placeholder before filters existed
            return new Placeholder(text, key, filters);      // login flow: unknown filter reported in evaluate
        }
        if (AKTO_KEY.matcher(key).matches()) {
            if (filtersValid) return new Placeholder(text, key, filters);
            if (!loginFlow && AKTO_KEY.matcher(inner).matches()) {
                return new Placeholder(text, inner, new ArrayList<String>()); // original whole-text key
            }
        }
        return null;
    }

    private static String evaluate(Placeholder ph, Map<String, Object> valuesMap, Options options, JarHolder jar)
            throws PlaceholderException {
        String value;
        String keyLabel = ph.key;
        if (ph.key.equals(COOKIES)) {
            if (options.requestUrl == null) {
                throw new PlaceholderException("${cookies} needs a request URL and can't be used in a URL or an "
                        + "auth param; use ${cookies.<name>}");
            }
            value = jar.get().cookieHeaderFor(options.requestUrl);
        } else if (options.loginFlow && ph.key.startsWith(COOKIES + ".")) {
            String name = ph.key.substring(COOKIES.length() + 1);
            value = jar.get().latestValue(name);
            if (value == null) {
                throw new PlaceholderException("Couldn't find cookie '" + name + "' for " + ph.text + ": "
                        + jar.get().missingReason(name));
            }
        } else {
            String lookupKey = lookupKey(ph.key, options.loginFlow);
            keyLabel = lookupKey;
            Object obj = valuesMap == null ? null : valuesMap.get(lookupKey);
            if (obj == null) {
                if (options.throwOnMissing) {
                    throw new PlaceholderException(missingMessage(lookupKey, valuesMap, options.loginFlow));
                }
                return null;
            }
            value = obj.toString();
        }

        boolean raw = false;
        for (String filter : ph.filters) {
            if (filter.equals(FILTER_RAW)) {
                raw = true;
            } else if (filter.equals(FILTER_URLENCODE)) {
                value = urlEncode(value);
            } else if (filter.startsWith(FILTER_REGEX)) {
                value = applyRegex(value, filter.substring(FILTER_REGEX.length()), keyLabel, ph.text);
            } else {
                throw new PlaceholderException("unknown filter '" + quote(filter) + "' in " + quote(ph.text)
                        + "; supported: regex:<pattern>, urlencode, raw");
            }
        }
        if (!options.escapeString || raw) return value;
        return options.jsStringEscaping ? escapeForJsString(value) : escape(value);
    }

    private static String lookupKey(String key, boolean loginFlow) throws PlaceholderException {
        if (!loginFlow || !key.startsWith("step")) return key;
        Matcher m = NODE_KEY.matcher(key);
        if (!m.matches()) return key;
        int step;
        try {
            step = Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            throw new PlaceholderException("invalid step number in ${" + key + "}");
        }
        if (step < 1) throw new PlaceholderException("step numbers start at 1: ${" + key + "}");
        return LoginFlowNodes.nodeIdForStep(step) + m.group(3);
    }

    private static String missingMessage(String key, Map<String, Object> valuesMap, boolean loginFlow) {
        String base = "Couldn't find " + key;
        Matcher node = NODE_PREFIX.matcher(key);
        if (!node.matches() || valuesMap == null) return base;
        String nodeId = node.group(1);
        String label = loginFlow ? LoginFlowNodes.label(nodeId) : nodeId;
        Object status = valuesMap.get(nodeId + ".response.status_code");
        if (REGEX_KEY.matcher(key).matches()) {
            return status != null
                    ? base + " (the regex of " + label + " did not match its response, status " + status + ")"
                    : base + " (" + label + " has no response" + (loginFlow ? "; step k is node x(2k-1)" : "") + ")";
        }
        boolean nodeRan = false;
        String prefix = nodeId + ".";
        for (String k : valuesMap.keySet()) {
            if (k.startsWith(prefix)) {
                nodeRan = true;
                break;
            }
        }
        if (!nodeRan) {
            return base + " (no values from " + label + (loginFlow ? "; step k is node x(2k-1)" : "") + ")";
        }
        return status != null ? base + " (" + label + " responded with status " + status + ")" : base;
    }

    private static String applyRegex(String value, String pattern, String keyLabel, String text)
            throws PlaceholderException {
        if (pattern.isEmpty()) {
            throw new PlaceholderException("empty regex in " + quote(text));
        }
        if (value.length() > MAX_REGEX_INPUT_CHARS) {
            throw new PlaceholderException("value of " + keyLabel + " is larger than 1MB; regex filter refused in "
                    + quote(text));
        }
        Matcher m;
        try {
            m = Pattern.compile(pattern).matcher(value);
        } catch (PatternSyntaxException e) {
            throw new PlaceholderException("invalid regex '" + quote(pattern) + "' in " + quote(text) + ": "
                    + e.getDescription());
        }
        if (!m.find()) {
            throw new PlaceholderException("regex '" + quote(pattern) + "' did not match the value of "
                    + keyLabel + " in " + quote(text));
        }
        String result = m.groupCount() > 0 ? m.group(1) : m.group();
        if (result == null) {
            throw new PlaceholderException("group 1 of regex '" + quote(pattern) + "' did not participate in "
                    + "the match on " + keyLabel + " in " + quote(text));
        }
        return result;
    }

    static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is always supported", e);
        }
    }

    /** JSON-string escaping used by the original replaceVariables, minus the invalid {@code '} -> {@code \'}. */
    public static String escape(String val) {
        return val.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\f", "\\f")
                .replace("\"", "\\\"");
    }

    /** escape() plus ' -> \' : the full escaping the original replaceVariables applied, needed inside JS strings. */
    public static String escapeForJsString(String val) {
        return escape(val).replace("'", "\\'");
    }

    private static boolean isKnownFilter(String f) {
        return f.equals(FILTER_RAW) || f.equals(FILTER_URLENCODE) || f.startsWith(FILTER_REGEX);
    }

    /** Splits on '|' not preceded by a backslash; {@code \|} becomes a literal '|'. */
    private static List<String> splitOnUnescapedPipe(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == '|') {
                cur.append('|');
                i++;
            } else if (c == '|') {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts;
    }

    private static String trimLeading(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        String t = s.substring(i);
        // trailing whitespace is part of a regex pattern; only trim it for the keyword filters
        String trimmed = t.trim();
        return t.startsWith(FILTER_REGEX) ? t : trimmed;
    }

    private static String quote(String text) {
        return text.length() > MAX_QUOTED_PLACEHOLDER_CHARS
                ? text.substring(0, MAX_QUOTED_PLACEHOLDER_CHARS) + "..." : text;
    }
}
