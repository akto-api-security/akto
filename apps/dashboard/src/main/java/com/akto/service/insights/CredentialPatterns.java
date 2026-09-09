package com.akto.service.insights;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Known credential/secret shapes, checked in order — first match wins. Deliberately limited to
 * well-known prefixes/formats (mirroring the real Secrets scanner's AWS/GitHub/Slack/OpenAI/
 * JWT/PEM-key coverage) rather than a generic high-entropy heuristic, which is much easier to get
 * wrong. Shared by CredentialExposureProvider (item #4) and LikelyFalsePositivesProvider (item #6,
 * for its "reclassify as credential detection" CTA) — extracted once a second provider needed it.
 */
public final class CredentialPatterns {

    private static final List<Map.Entry<String, Pattern>> PATTERNS = Arrays.asList(
            entry("Bearer token", Pattern.compile("(?i)\\bbearer\\s+[a-z0-9\\-_.]{10,}")),
            entry("AWS access key", Pattern.compile("AKIA[0-9A-Z]{16}")),
            entry("GitHub token", Pattern.compile("gh[pousr]_[A-Za-z0-9]{20,}")),
            entry("Slack token", Pattern.compile("xox[baprs]-[A-Za-z0-9-]{10,}")),
            entry("OpenAI-style API key", Pattern.compile("sk-[A-Za-z0-9]{20,}")),
            entry("Private key block", Pattern.compile("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----")),
            entry("JWT", Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}")),
            entry("Generic API key/token", Pattern.compile(
                    "(?i)(api[_-]?key|secret|access[_-]?token|auth[_-]?token)\\s*[:=]\\s*['\"]?[A-Za-z0-9\\-_.]{12,}"))
    );

    private CredentialPatterns() {}

    private static Map.Entry<String, Pattern> entry(String label, Pattern pattern) {
        return new AbstractMap.SimpleEntry<>(label, pattern);
    }

    /** First matching credential shape's label, or null if none match. */
    public static String match(String text) {
        if (text == null) {
            return null;
        }
        for (Map.Entry<String, Pattern> entry : PATTERNS) {
            if (entry.getValue().matcher(text).find()) {
                return entry.getKey();
            }
        }
        return null;
    }
}
