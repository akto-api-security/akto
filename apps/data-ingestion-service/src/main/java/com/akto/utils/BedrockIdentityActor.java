package com.akto.utils;

import com.akto.dto.IngestDataBatch;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import software.amazon.awssdk.arns.Arn;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Back-compat rewrite for Bedrock ingest payloads that still send ip=0.0.0.0
 * while carrying the caller in {@code bedrock-identity-arn} (header or tag).
 *
 * Guardrail activity's Actor is the ingest {@code ip} field. Older Bedrock
 * processors hardcode 0.0.0.0 because model-invocation logs have no client IP.
 *
 * ARN envelope is parsed by {@link Arn} ({@code software.amazon.awssdk:arns}),
 * the Java counterpart of {@code @aws-sdk/util-arn-parser}. Actor selection
 * from the resource string must stay aligned with identityActor.js; shared
 * cases live in {@code identity-actor-cases.json}.
 *
 * Display values must not contain ':' — threat-detection's cleanIp() splits
 * on the first colon.
 */
public final class BedrockIdentityActor {

    static final String PLACEHOLDER_IP = "0.0.0.0";
    private static final String IDENTITY_ARN_KEY = "bedrock-identity-arn";
    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX32 = Pattern.compile("^[0-9a-f]{32,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EC2_INSTANCE = Pattern.compile("^i-[0-9a-f]{8,}$", Pattern.CASE_INSENSITIVE);

    private BedrockIdentityActor() {}

    public static void apply(IngestDataBatch payload) {
        if (payload == null || !isPlaceholder(payload.getIp())) {
            return;
        }
        String actor = actorFromIdentityArn(firstIdentityArn(payload.getRequestHeaders(), payload.getTag()));
        if (!actor.isEmpty()) {
            payload.setIp(actor);
        }
    }

    static boolean isPlaceholder(String ip) {
        if (ip == null) {
            return true;
        }
        String trimmed = ip.trim();
        return trimmed.isEmpty()
                || PLACEHOLDER_IP.equals(trimmed)
                || "::".equals(trimmed)
                || "null".equalsIgnoreCase(trimmed);
    }

    static String actorFromIdentityArn(String arn) {
        if (arn == null) {
            return "";
        }
        String trimmed = arn.trim();
        ParsedArn parsed = parseArn(trimmed);
        if (parsed == null) {
            return "";
        }
        if (!"sts".equals(parsed.service) && !"iam".equals(parsed.service)) {
            return "";
        }
        return actorFromResource(parsed.resource);
    }

    /**
     * Envelope parse via {@link Arn#fromString(String)} — same six-field split as
     * {@code @aws-sdk/util-arn-parser}. Returns null when the string is not an ARN.
     */
    static ParsedArn parseArn(String arn) {
        if (arn == null || arn.isEmpty()) {
            return null;
        }
        try {
            Arn parsed = Arn.fromString(arn);
            return new ParsedArn(parsed.service(), parsed.resourceAsString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String actorFromResource(String resource) {
        List<String> parts = splitResource(resource);
        if (parts.isEmpty()) {
            return "";
        }
        String type = parts.get(0);
        switch (type) {
            case "assumed-role":
                return actorFromAssumedRole(
                        parts.size() > 1 ? parts.get(1) : "",
                        joinFrom(parts, 2));
            case "federated-user":
            case "user":
            case "role":
                return lastSegment(parts.subList(1, parts.size()));
            case "root":
                return "root";
            default:
                return parts.size() > 1 ? lastSegment(parts) : "";
        }
    }

    private static String actorFromAssumedRole(String roleName, String session) {
        if (roleName.isEmpty()) {
            return "";
        }
        if (session.contains("@")) {
            return session;
        }
        if (roleName.regionMatches(true, 0, "AWSReservedSSO_", 0, "AWSReservedSSO_".length()) && !session.isEmpty()) {
            return session;
        }
        if (!isMachineSession(session)) {
            return session;
        }
        return roleName;
    }

    private static boolean isMachineSession(String session) {
        if (session == null || session.isEmpty()) {
            return true;
        }
        return UUID.matcher(session).matches()
                || HEX32.matcher(session).matches()
                || EC2_INSTANCE.matcher(session).matches();
    }

    private static List<String> splitResource(String resource) {
        List<String> parts = new ArrayList<>();
        if (resource == null || resource.isEmpty()) {
            return parts;
        }
        for (String part : resource.split("/")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static String joinFrom(List<String> parts, int start) {
        if (start >= parts.size()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(parts.get(start));
        for (int i = start + 1; i < parts.size(); i++) {
            sb.append('/').append(parts.get(i));
        }
        return sb.toString();
    }

    private static String lastSegment(List<String> parts) {
        return parts.isEmpty() ? "" : parts.get(parts.size() - 1);
    }

    private static String firstIdentityArn(String requestHeaders, String tag) {
        String fromHeaders = lookupIgnoreCase(parseObject(requestHeaders), IDENTITY_ARN_KEY);
        if (!fromHeaders.isEmpty()) {
            return fromHeaders;
        }
        return lookupIgnoreCase(parseObject(tag), IDENTITY_ARN_KEY);
    }

    private static BasicDBObject parseObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return BasicDBObject.parse(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static String lookupIgnoreCase(BasicDBObject obj, String key) {
        if (obj == null || key == null) {
            return "";
        }
        for (String candidate : obj.keySet()) {
            if (candidate != null && candidate.equalsIgnoreCase(key)) {
                return stringify(obj.get(candidate));
            }
        }
        return "";
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BasicDBList) {
            BasicDBList list = (BasicDBList) value;
            return list.isEmpty() || list.get(0) == null ? "" : list.get(0).toString().trim();
        }
        return value.toString().trim();
    }

    static final class ParsedArn {
        final String service;
        final String resource;

        ParsedArn(String service, String resource) {
            this.service = service;
            this.resource = resource;
        }
    }
}
