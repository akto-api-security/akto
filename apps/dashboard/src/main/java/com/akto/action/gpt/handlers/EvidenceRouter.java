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
        DATATYPE_RESPONSE_SCAN, // sensitive-data exposure: scan response for the data type
        REQUEST_DIFF,           // injection/fuzzing/authz: the values the test changed
        RESPONSE_HEADERS,       // CORS / security misconfig: a response header
        RESPONSE_STATUS,        // broken auth / no-auth: 2xx returned after token removed
        CROSS_ATTEMPT,          // rate-limit / multi-account: proof spans attempts
        LLM_SEMANTIC            // error/stack-trace disclosure, business logic
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

        if (c.startsWith(EDE_PREFIX) || c.contains("EXCESSIVE_DATA") || c.equals("EDE")) {
            return new Route(
                list("RESPONSE"),
                list(Detector.DATATYPE_RESPONSE_SCAN),
                "Evidence is the exposed value in the RESPONSE body or headers.");
        }
        if (c.contains("CORS") || c.contains("SECURITY_HEADER") || c.contains("MISCONFIG") || c.contains("SSRF")) {
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
        // Broken authentication / no-auth: the test removes or breaks the auth token
        // and the server still returns 2xx. The evidence is response-side (the status
        // + any data returned), NOT the request headers, so do not run request-diff.
        if (c.contains("NO_AUTH") || c.contains("ANY_USER") || c.contains("REMOVE_TOKEN")
                || c.contains("REPLACE_AUTH") || c.contains("ADD_USER_TOKEN") || c.contains("AUTH_BYPASS")
                || c.contains("BROKEN_AUTHENTICATION") || c.contains("AUTHENTICATION")) {
            return new Route(
                list("RESPONSE"),
                list(Detector.RESPONSE_STATUS, Detector.LLM_SEMANTIC),
                "The auth token was removed/invalidated; evidence is the 2xx RESPONSE status and any data the response returned. Do NOT cite the request's own auth token or headers.");
        }
        if (c.contains("BOLA") || c.contains("BFLA") || c.contains("IDOR") || c.contains("AUTH")
                || c.contains("ACCESS") || c.contains("PRIVILEGE") || c.contains("ACCOUNT")) {
            return new Route(
                list("REQUEST", "RESPONSE"),
                list(Detector.REQUEST_DIFF, Detector.LLM_SEMANTIC),
                "Evidence is the changed request object id/identity AND the RESPONSE data wrongly returned. Do NOT cite the caller's auth token.");
        }
        // default: injection / fuzzing / semantic
        return new Route(
            list("REQUEST", "RESPONSE"),
            list(Detector.REQUEST_DIFF, Detector.LLM_SEMANTIC),
            "Evidence is the injected REQUEST value and/or the RESPONSE proof.");
    }

    /**
     * True when the category has a deterministic detector strong enough that we
     * can skip the LLM entirely when that detector produced evidence.
     */
    public static boolean isDeterministicStrong(String category) {
        return route(category).detectors.contains(Detector.DATATYPE_RESPONSE_SCAN);
    }
}
