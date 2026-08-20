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
     * Device-label slug an Inference Hooks request's Host is built from
     * (see ProviderGuardrailAction.buildAgentHost) — {@code slugify(emailLocalPart(email))}.
     */
    public static String fromEmail(String email) {
        return slugify(emailLocalPart(email));
    }
}
