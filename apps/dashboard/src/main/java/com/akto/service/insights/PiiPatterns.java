package com.akto.service.insights;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mirrors of the real PII detection regexes from the Go validator's PIIRegexPatterns map (the
 * runtime enforcement layer, compiled into apps/guardrails-service via the pinned
 * akto-endpoint-shield dependency), used by LikelyFalsePositivesProvider (item #6) to check whether
 * a violation's evidence text actually contains what its declared "PII-&lt;type&gt;" subCategory
 * claims.
 *
 * Deliberately a SUBSET of the ~29 types the real validator supports. Excluded, on purpose:
 * - "password": detected by an LLM classifier at runtime, not a regex — nothing to faithfully mirror.
 * - "phone"/"phone_number", "address", "database": the real regexes are long country/format
 *   alternations this research could not confirm in full (partially elided) — approximating them
 *   risks a check that disagrees with the real detector, which is worse than not checking at all.
 * - "driver_id", "license_plate": declared but dead in the real validator (regex entry removed from
 *   its compiled map), so a violation can never actually carry these subCategories.
 * Credit-card patterns are reconstructed from well-known public card-number formats (not
 * Akto-specific, so safe to approximate) rather than the real regex's exact text, since Luhn
 * validation (also mirrored here, matching the real validator's own extra check) is what actually
 * does the discriminating work for that type.
 */
public final class PiiPatterns {

    private static final Map<String, Pattern> PATTERNS = new HashMap<>();
    static {
        PATTERNS.put("email", Pattern.compile(
                "(?i)\\b[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}\\b"));
        PATTERNS.put("ssn", Pattern.compile(
                "(?i)(?:(?:ssn|social\\s+security(?:\\s+number)?).{0,20}\\b\\d{3}-\\d{2}-\\d{4}\\b|\\b\\d{3}-\\d{2}-\\d{4}\\b.{0,20}(?:ssn|social\\s+security(?:\\s+number)?))"));
        PATTERNS.put("vin", Pattern.compile(
                "[A-HJ-NPR-Z\\d]{3}[A-HJ-NPR-Z\\d]{5}[\\dX][A-HJ-NPR-Z\\d][A-HJ-NPR-Z\\d][A-HJ-NPR-Z\\d]{6}"));
        PATTERNS.put("cvv", Pattern.compile(
                "(?i)(?:cvv|cvc|cid|cvv2|csc).{0,10}(?:code|number|no)?.{0,10}\\b([0-9]{3,4})\\b"));
        PATTERNS.put("ip_address", Pattern.compile(
                "(?i)(?:server|host|internal|private|vpn|database|admin|management).{0,20}(?:IP|address).{0,10}\\b((?:10|172\\.(?:1[6-9]|2[0-9]|3[01])|192\\.168|[0-9]{1,3})\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})\\b"));
        PATTERNS.put("uuid", Pattern.compile(
                "(?i)(?:id|uuid|guid|identifier|instance|resource|reference|user|session|request|transaction).{0,20}\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"));
        PATTERNS.put("token", Pattern.compile(
                "(?i)(?:\\b(eyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{3,})\\b|bearer\\s+([A-Za-z0-9\\-._~+/]{20,}=*)|\"(?:access_token|refresh_token|auth_token|id_token|token)\"\\s*:\\s*\"([A-Za-z0-9\\-._~+/=]{20,})\")"));
        PATTERNS.put("secret", Pattern.compile(
                "(?i)(?:(?:sk|pk)_(?:live|test|prod|dev)_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|gh[pors]_[A-Za-z0-9]{36,}|xox[bpars]-[0-9A-Za-z\\-]{10,}|(?:api[_\\-]?key|secret[_\\-]?key)\\s*[:=]\\s*['\"]?[A-Za-z0-9+/=_\\-]{20,}['\"]?)"));
        PATTERNS.put("aws_access_key_id", Pattern.compile("\\b(AKIA[0-9A-Z]{16})\\b"));
        PATTERNS.put("gcp_credential", Pattern.compile("AIza[0-9A-Za-z_\\-]{35}"));
        PATTERNS.put("ssh_private_key", Pattern.compile(
                "-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"));
        PATTERNS.put("webhook_url", Pattern.compile(
                "(?i)https://(?:hooks\\.slack\\.com/services/[A-Z0-9/]+|discord(?:app)?\\.com/api/webhooks/\\d+/[A-Za-z0-9_\\-]+)"));
        PATTERNS.put("otp", Pattern.compile(
                "(?i)(?:otp|one[\\s_]?time[\\s_]?(?:password|code|pin)|2fa[\\s_]?(?:code|token)).{0,15}\\b(\\d{4,8})\\b"));
        PATTERNS.put("passport", Pattern.compile(
                "(?i)passport[\\s_]?(?:no|number|id|num)?.{0,10}\\b([A-Z]{1,2}[0-9]{6,9}[A-Z]?)\\b"));
        PATTERNS.put("username", Pattern.compile(
                "(?i)(?:\"(?:username|user_name|login(?:_?id)?|uname)\"\\s*:\\s*\"([A-Za-z0-9_@.\\-]{3,64})\"|(?:username|uname)=([A-Za-z0-9_@.\\-]{3,64})(?:[&\\s'\"]|$))"));
        PATTERNS.put("userid", Pattern.compile(
                "(?i)\"(?:user_?id|userid|user-id|uid|account_?id|accountId|account-id)\"\\s*:\\s*\"([A-Za-z0-9_@.\\-]{3,64})\""));
        PATTERNS.put("aws_dynamodb_url", Pattern.compile(
                "(?i)https?://dynamodb\\.[a-z]{2}-[a-z]+-\\d+\\.amazonaws\\.com(?:/[^\\s\"']*)?"));
        PATTERNS.put("aws_s3_bucket_url", Pattern.compile(
                "(?i)[a-zA-Z0-9][a-zA-Z0-9\\-]{1,61}[a-zA-Z0-9]\\.s3(?:\\.[a-z]{2}-[a-z]+-\\d+)?\\.amazonaws\\.com"));
        PATTERNS.put("aws_arn_connect_instance", Pattern.compile(
                "arn:aws:connect:[a-z]{2}-[a-z]+-\\d+:\\d{12}:instance/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        PATTERNS.put("aws_rds_url", Pattern.compile(
                "(?i)[a-zA-Z0-9-]+\\.[a-zA-Z0-9-]+\\.[a-z]{2}-[a-z]+-\\d+\\.rds\\.amazonaws\\.com(?::\\d+)?"));
        PATTERNS.put("azure_credential", Pattern.compile(
                "(?i)(?:DefaultEndpointsProtocol=https?;AccountName=[^;]+;AccountKey=[A-Za-z0-9+/=]{20,}|AccountKey=[A-Za-z0-9+/=]{20,})"));
        // Public, industry-standard card-number shapes (Visa/Mastercard/Amex/Discover) — not
        // Akto-specific, so approximating is safe. Luhn (below) does the real discriminating work.
        PATTERNS.put("credit_card", Pattern.compile(
                "\\b(?:4[0-9]{3}|5[1-5][0-9]{2}|3[47][0-9]{2}|6(?:011|5[0-9]{2}))[\\s-]?[0-9]{2,4}[\\s-]?[0-9]{2,4}[\\s-]?[0-9]{1,4}\\b"));

        PATTERNS.put("credit_card_cap", PATTERNS.get("credit_card"));
        PATTERNS.put("raw_pan", PATTERNS.get("credit_card"));
    }

    /** A crude proxy for "this text has already been redacted", not tied to any one detector's
     *  exact tag format (Presidio's own format is external/unconfirmed) — a bracketed all-caps
     *  token like "[EMAIL_REDACTED]" or "[PII_REDACTED]". Real prose/code is very unlikely to
     *  contain one by coincidence. */
    private static final Pattern REDACTION_MARKER = Pattern.compile("\\[[A-Z_]{4,}\\]");

    private PiiPatterns() {}

    /** Null if this PII type has no mirrored pattern (unsupported/excluded — see class doc), not
     *  "never matches" — callers must treat those as not independently checkable. */
    public static boolean isCheckable(String piiType) {
        return piiType != null && PATTERNS.get(piiType.toLowerCase(java.util.Locale.US)) != null;
    }

    public static boolean rawPatternPresent(String piiType, String evidenceText) {
        if (evidenceText == null || !isCheckable(piiType)) {
            return false;
        }
        Pattern p = PATTERNS.get(piiType.toLowerCase(java.util.Locale.US));
        if ("credit_card".equals(piiType) || "credit_card_cap".equals(piiType) || "raw_pan".equals(piiType)) {
            java.util.regex.Matcher m = p.matcher(evidenceText);
            while (m.find()) {
                if (isValidLuhn(m.group().replaceAll("[\\s-]", ""))) {
                    return true;
                }
            }
            return false;
        }
        return p.matcher(evidenceText).find();
    }

    public static boolean looksRedacted(String evidenceText) {
        return evidenceText != null && REDACTION_MARKER.matcher(evidenceText).find();
    }

    private static boolean isValidLuhn(String digits) {
        if (digits == null || !digits.matches("\\d{12,19}")) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
