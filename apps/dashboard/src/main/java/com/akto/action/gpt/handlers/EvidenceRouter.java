package com.akto.action.gpt.handlers;

import java.util.Arrays;
import java.util.List;

/**
 * Single source of truth for WHERE vulnerability evidence lives per test
 * category. Drives (a) which deterministic detectors to run and (b) the
 * location hint given to the LLM, so evidence is selected by category rather
 * than guessed.
 */
public class EvidenceRouter {

    public enum Detector {
        RESPONSE_HEADERS,       // CORS / security misconfig: a precise response header
        RESPONSE_STATUS,        // broken auth / no-auth: 2xx returned after token removed
        CROSS_ATTEMPT,          // rate-limit / multi-account: proof spans attempts
        LLM_SEMANTIC            // everything else: LLM selects evidence, FE verifies it
    }

    public static class Route {
        public final List<String> locations;   // REQUEST / RESPONSE
        public final List<Detector> detectors;
        public final String hint;               // prompt location hint

        public Route(List<String> locations, List<Detector> detectors, String hint) {
            this.locations = locations;
            this.detectors = detectors;
            this.hint = hint;
        }
    }

    public static final String EDE_PREFIX = "SENSITIVE_DATA_EXPOSURE_";

    @SafeVarargs
    private static <T> List<T> list(T... items) {
        return Arrays.asList(items);
    }

    public static Route route(String category) {
        String c = category == null ? "" : category.toUpperCase();

        // Sensitive-data exposure: the data type is already known from the test
        // sub-type (see edeDataType) and may surface in EITHER the response or the
        // request (the test's own conditions decide where). We do not re-scan/re-
        // classify here - the LLM is told the exact data type and asked to copy the
        // value verbatim; the frontend then verifies it is actually highlightable.
        if (c.startsWith(EDE_PREFIX) || c.contains("EXCESSIVE_DATA") || c.equals("EDE")) {
            return new Route(
                list("RESPONSE", "REQUEST"),
                list(Detector.LLM_SEMANTIC),
                "Evidence is the exposed value of the data type named by this test, shown verbatim in the RESPONSE (or the REQUEST if that is where the sensitive value appears).");
        }
        // Header-based misconfig ONLY (CORS / security headers). Keep this first
        // check precise: SSRF is NOT a response-header finding (its proof is the
        // injected URL in the request + the fetched content in the response), so it
        // must not be routed here - it falls through to the request/response path.
        if (c.contains("CORS") || c.contains("SECURITY_HEADER")) {
            return new Route(
                list("RESPONSE"),
                list(Detector.RESPONSE_HEADERS, Detector.LLM_SEMANTIC),
                "Evidence is usually a RESPONSE header (e.g. access-control-allow-origin: *).");
        }
        if (c.contains("RATE") || c.contains("LIMIT")) {
            return new Route(
                list("REQUEST", "RESPONSE"),
                list(Detector.CROSS_ATTEMPT, Detector.LLM_SEMANTIC),
                "Evidence spans attempts; cite the RESPONSE status that shows the limit was not enforced.");
        }
        // Injection / fuzzing FIRST: a broad bucket like NO_AUTH can also contain
        // SQLi / command-injection / SSTI / CRLF tests (especially custom ones).
        // For those the proof is the injected REQUEST payload + the RESPONSE effect,
        // NOT a "token was removed" 2xx - so match them before the broken-auth branch.
        if (c.contains("INJECTION") || c.contains("SQL") || c.contains("SSTI")
                || c.contains("CRLF") || c.contains("XSS") || c.contains("XXE")
                || c.contains("TEMPLATE") || c.contains("LDAP") || c.contains("FUZZ")
                || c.contains("SSRF")) {
            return new Route(
                list("REQUEST", "RESPONSE"),
                list(Detector.LLM_SEMANTIC),
                "Evidence is the injected REQUEST payload (the value the test added, listed in the test definition) and the RESPONSE proof that it executed/was reflected.");
        }
        // Broken authentication / no-auth: the test removes or breaks the auth token
        // and the server still returns 2xx, so the evidence is response-side (the
        // status + any data returned). Injection tests that can also be filed under
        // this broad bucket are already caught by the injection branch above; if one
        // still lands here, LLM_SEMANTIC (told via the hint) picks the injected value.
        if (c.contains("NO_AUTH") || c.contains("ANY_USER") || c.contains("REMOVE_TOKEN")
                || c.contains("REPLACE_AUTH") || c.contains("ADD_USER_TOKEN") || c.contains("AUTH_BYPASS")
                || c.contains("BROKEN_AUTHENTICATION")) {
            return new Route(
                list("REQUEST", "RESPONSE"),
                list(Detector.RESPONSE_STATUS, Detector.LLM_SEMANTIC),
                "Evidence is usually the 2xx RESPONSE status returned after the auth token was removed/invalidated, plus any data the response returned. If the test instead injected a payload (this broad bucket can include injection tests), the injected REQUEST value listed in the test definition is the evidence. Do NOT cite the caller's own auth token or transport headers.");
        }
        if (c.contains("BOLA") || c.contains("BFLA") || c.contains("IDOR") || c.contains("AUTH")
                || c.contains("ACCESS") || c.contains("PRIVILEGE") || c.contains("ACCOUNT")) {
            return new Route(
                list("REQUEST", "RESPONSE"),
                list(Detector.LLM_SEMANTIC),
                "Evidence is the changed request object id/identity in the REQUEST, and the RESPONSE returning another user's/role's resource (its body closely matches the victim's by schema/percentage) when access should have been denied. Do NOT cite the caller's auth token.");
        }
        // default: injection / fuzzing / semantic
        return new Route(
            list("REQUEST", "RESPONSE"),
            list(Detector.LLM_SEMANTIC),
            "Evidence is the injected REQUEST value and/or the RESPONSE proof.");
    }

    /**
     * Cheap pre-filter for header-presence (MHH) tests. This ONLY decides whether
     * it is worth loading + parsing the test template to look for header key
     * conditions; it does NOT by itself decide to skip the LLM. The authoritative
     * gate is whether HeaderEvidenceDetector actually extracts header conditions.
     */
    public static boolean isHeaderCategory(String category) {
        if (category == null) {
            return false;
        }
        String c = category.toUpperCase();
        return c.contains("HEADER") || c.contains("MHH") || c.contains("CORS")
                || c.contains("HSTS") || c.contains("CSP") || c.contains("X_FRAME")
                || c.contains("CONTENT_TYPE");
    }

    /**
     * The sensitive data type a sensitive-data-exposure test targets (e.g.
     * SENSITIVE_DATA_EXPOSURE_EMAIL -> EMAIL), or null if this is not an EDE
     * category. Lets the prompt name the exact data type instead of re-scanning.
     */
    public static String edeDataType(String category) {
        if (category == null) {
            return null;
        }
        String c = category.toUpperCase();
        if (c.startsWith(EDE_PREFIX) && c.length() > EDE_PREFIX.length()) {
            return c.substring(EDE_PREFIX.length());
        }
        return null;
    }
}
