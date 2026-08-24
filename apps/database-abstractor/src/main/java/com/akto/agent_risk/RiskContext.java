package com.akto.agent_risk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.akto.utils.elasticsearch.AgentQueryRecord;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RiskContext {

    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT = Pattern.compile("eyj[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_OR_KEY = Pattern.compile("(?:[a-f0-9]{32,}|sk-[a-z0-9]{16,}|akto_[a-z0-9_-]{8,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{8,}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PUNCT = Pattern.compile("[\"'`~^|\\\\]+");
    private static final Pattern ADMIN = Pattern.compile("\\b(admin|root|superuser|iam:?admin)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern USER = Pattern.compile("\\b(user|member|role)\\b", Pattern.CASE_INSENSITIVE);

    private int accountId;
    private String agentKey;
    private String toolFingerprint;
    private String privilegeClass;
    private String normalizedPrompt;
    private String rawText;
    private String traceId;
    private String spanId;
    private Integer apiCollectionId;

    public static RiskContext from(AgentQueryRecord record) {
        RiskContext ctx = new RiskContext();
        if (record == null) {
            return ctx;
        }
        ctx.accountId = record.getAccountId();
        ctx.agentKey = emptyToUnknown(record.getServiceId());
        ctx.traceId = record.getTraceId();
        ctx.spanId = record.getSpanId();
        if (record.getApiCollectionId() != 0) {
            ctx.apiCollectionId = record.getApiCollectionId();
        }
        String combined = nullToEmpty(record.getQueryPayload()) + " " + nullToEmpty(record.getResponsePayload());
        ctx.normalizedPrompt = normalize(record.getQueryPayload());
        ctx.toolFingerprint = ToolRisk.fingerprint(combined);
        ctx.privilegeClass = privilegeClass(combined);
        ctx.rawText = nullToEmpty(record.getQueryPayload()) + "\n" + nullToEmpty(record.getResponsePayload());
        return ctx;
    }

    public String hash() {
        String canonical = accountId + "\n"
                + nullToEmpty(agentKey) + "\n"
                + nullToEmpty(normalizedPrompt);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.toLowerCase(Locale.ROOT);
        s = EMAIL.matcher(s).replaceAll("{email}");
        s = JWT.matcher(s).replaceAll("{secret}");
        s = HEX_OR_KEY.matcher(s).replaceAll("{secret}");
        s = LONG_DIGITS.matcher(s).replaceAll("{num}");
        s = PUNCT.matcher(s).replaceAll(" ");
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }

    static String privilegeClass(String text) {
        if (text != null && ADMIN.matcher(text).find()) {
            return "admin";
        }
        if (text != null && USER.matcher(text).find()) {
            return "user";
        }
        return "none";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToUnknown(String s) {
        return (s == null || s.isEmpty()) ? "unknown" : s.toLowerCase(Locale.ROOT);
    }
}
