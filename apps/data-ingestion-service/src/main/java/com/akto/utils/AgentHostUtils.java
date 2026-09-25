package com.akto.utils;

import com.akto.jobs.executors.AIAgentConnectorConstants;

/**
 * Builds Atlas agent collection hostnames server-side: {identity}.ai-agent.{app}.
 * Mirrors the client-side endpoint-shield convention ({deviceLabel}.ai-agent.{agentName},
 * see device.go GetAgentCollectionName) for traffic that carries no device label.
 */
public final class AgentHostUtils {

    private static final String UNKNOWN_IDENTITY = "unknown";

    private AgentHostUtils() {}

    /** {identity}.ai-agent.{app}; the identity is slugified, and "unknown" when that leaves nothing. */
    public static String agentHost(String identity, String app) {
        return identitySlug(identity) + AIAgentConnectorConstants.AI_AGENT_HOST_INFIX + app;
    }

    /** Slugified identity for the first host segment, "unknown" when that leaves nothing. */
    public static String identitySlug(String identity) {
        String slug = slugify(identity);
        return slug.isEmpty() ? UNKNOWN_IDENTITY : slug;
    }

    /** Local part of the email (before "@"); the domain is dropped. */
    public static String emailLocalPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /** Lowercase; each run of non-alphanumerics -> "-"; trim leading/trailing "-". */
    public static String slugify(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }
}
