package com.akto.util;

public class DeviceLabelUtil {

    private DeviceLabelUtil() {
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

    /**
     * The device label guardrails-service extracts from a request's Host header
     * (first dot-segment of "{label}.ai-agent.{app}", see
     * ProviderGuardrailAction#buildAgentHost in the data-ingestion-service repo).
     * Given the same email, always returns the same label a live request from
     * that actor would carry.
     */
    public static String fromEmail(String email) {
        String slug = slugify(emailLocalPart(email));
        return slug.isEmpty() ? "unknown" : slug;
    }
}
