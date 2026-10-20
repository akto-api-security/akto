package com.akto.utils;

import com.akto.dto.IngestDataBatch;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import software.amazon.awssdk.arns.Arn;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sets the Actor of Bedrock ingest payloads from the caller in
 * {@code bedrock-identity-arn} (header or tag).
 *
 * Guardrail activity's Actor is the ingest {@code ip} field. Model-invocation
 * logs have no client IP: older Bedrock processors send 0.0.0.0, newer ones
 * their own actor. Both are replaced here, so every collector version shows
 * the same Actor without an upgrade. Only payloads carrying
 * {@code bedrock-identity-arn} are touched, and a real client IP is never
 * overwritten.
 *
 * Actor for an assumed role: the person when the session is one (email, SSO
 * user, other human-readable name); for a machine session, its kind and id
 * with the role, e.g. {@code ECS-<task id>-<role>} (see
 * {@link #actorFromAssumedRole}).
 *
 * ARN envelope is parsed by {@link Arn} ({@code software.amazon.awssdk:arns}),
 * the Java counterpart of {@code @aws-sdk/util-arn-parser}. Cases live in
 * {@code identity-actor-cases.json}.
 *
 * Display values must not contain ':' — threat-detection's cleanIp() splits
 * on the first colon. Role and session names cannot contain one.
 */
public final class BedrockIdentityActor {

    static final String PLACEHOLDER_IP = "0.0.0.0";
    private static final String IDENTITY_ARN_KEY = "bedrock-identity-arn";
    /** A UUID with dashes (a random id chosen by the caller). */
    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);
    /** Exactly 32 hex characters: the shape of an ECS task id. */
    private static final Pattern ECS_TASK = Pattern.compile("^[0-9a-f]{32}$", Pattern.CASE_INSENSITIVE);
    /** Longer hex strings (random ids). */
    private static final Pattern LONG_HEX = Pattern.compile("^[0-9a-f]{33,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EC2_INSTANCE = Pattern.compile("^i-[0-9a-f]{8,}$", Pattern.CASE_INSENSITIVE);
    /** Session Bedrock Agents uses when it runs an agent: BedrockAgents-<agent id>-<id>. */
    private static final Pattern BEDROCK_AGENTS = Pattern.compile("^BedrockAgents-[A-Za-z0-9]+-[A-Za-z0-9-]+$");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?$");
    private static final Pattern IPV6 = Pattern.compile("^\\[?[0-9a-f:.]*:[0-9a-f:.]*(%\\w+)?\\]?(:\\d{1,5})?$",
            Pattern.CASE_INSENSITIVE);

    private BedrockIdentityActor() {}

    public static void apply(IngestDataBatch payload) {
        if (payload == null || isRealIp(payload.getIp())) {
            return;
        }
        // Cheap text check first: every ingested payload passes here, only Bedrock ones carry the key.
        if (!containsIgnoreCase(payload.getRequestHeaders(), IDENTITY_ARN_KEY)
                && !containsIgnoreCase(payload.getTag(), IDENTITY_ARN_KEY)) {
            return;
        }
        String actor = actorFromIdentityArn(firstIdentityArn(payload.getRequestHeaders(), payload.getTag()));
        if (!actor.isEmpty()) {
            payload.setIp(actor);
        }
    }

    /** A client IP address (IPv4 or IPv6, optionally with a port), other than the placeholders. */
    static boolean isRealIp(String ip) {
        if (isPlaceholder(ip)) {
            return false;
        }
        String trimmed = ip.trim();
        if (IPV4.matcher(trimmed).matches()) {
            return !trimmed.startsWith(PLACEHOLDER_IP + ":");
        }
        return IPV6.matcher(trimmed).matches();
    }

    private static boolean containsIgnoreCase(String text, String part) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i + part.length() <= text.length(); i++) {
            if (text.regionMatches(true, i, part, 0, part.length())) {
                return true;
            }
        }
        return false;
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

    /**
     * The session name is chosen by whoever assumed the role:
     * <ul>
     *   <li>a person (email, SSO user name, other readable name): that person;</li>
     *   <li>EC2 instance role ({@code i-<id>}): {@code EC2-<instance id>-<role>};</li>
     *   <li>Bedrock Agents running an agent ({@code BedrockAgents-<agent id>-<id>}): {@code <session>-<role>};</li>
     *   <li>32 hex characters (an ECS task id): {@code ECS-<task id>-<role>};</li>
     *   <li>other random ids (UUID, longer hex): {@code SESSION-<id>-<role>};</li>
     *   <li>no session: the role.</li>
     * </ul>
     */
    static String actorFromAssumedRole(String roleName, String session) {
        if (roleName.isEmpty()) {
            return "";
        }
        if (session.contains("@")) {
            return session;
        }
        if (roleName.regionMatches(true, 0, "AWSReservedSSO_", 0, "AWSReservedSSO_".length()) && !session.isEmpty()) {
            return session;
        }
        if (session.isEmpty()) {
            return roleName;
        }
        if (EC2_INSTANCE.matcher(session).matches()) {
            return "EC2-" + session + "-" + roleName;
        }
        if (BEDROCK_AGENTS.matcher(session).matches()) {
            return session + "-" + roleName;
        }
        if (ECS_TASK.matcher(session).matches()) {
            return "ECS-" + session + "-" + roleName;
        }
        if (UUID.matcher(session).matches() || LONG_HEX.matcher(session).matches()) {
            return "SESSION-" + session + "-" + roleName;
        }
        return session;
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

    static String firstIdentityArn(String requestHeaders, String tag) {
        String fromHeaders = lookupIgnoreCase(parseObject(requestHeaders), IDENTITY_ARN_KEY);
        if (!fromHeaders.isEmpty()) {
            return fromHeaders;
        }
        return lookupIgnoreCase(parseObject(tag), IDENTITY_ARN_KEY);
    }

    static BasicDBObject parseObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return BasicDBObject.parse(json);
        } catch (Exception e) {
            return null;
        }
    }

    static String lookupIgnoreCase(BasicDBObject obj, String key) {
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
