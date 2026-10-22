package com.akto.hybrid_runtime.redaction;

/**
 * Redaction outcome for one {@link RedactionItem}. A side that was not sent has a null side
 * result. Use {@link #unavailable(RedactionItem)} for every failure path so a missing answer
 * can never read as "nothing to mask".
 */
public class RedactionResult {

    /** Stored with a record whose payload was not redacted. */
    public static final String MARKER_UNAVAILABLE = "UNAVAILABLE";
    /** Stored with a record whose payload was masked by the regex and secret scrub only. */
    public static final String MARKER_BASIC = "BASIC";

    /**
     * The marker a stored record must carry, given what was sent and what came back: the weaker of
     * the two sides (UNAVAILABLE beats BASIC beats none). A payload that was sent but has no answer
     * for its side counts as UNAVAILABLE; a payload that was not sent says nothing.
     */
    public static String markerFor(RedactionItem item, RedactionResult result) {
        return worse(
                sideMarker(item.getRequestPayload(), result == null ? null : result.getRequest()),
                sideMarker(item.getResponsePayload(), result == null ? null : result.getResponse()));
    }

    private static String sideMarker(String sentPayload, Side side) {
        if (sentPayload == null) {
            return null;
        }
        return side == null ? MARKER_UNAVAILABLE : side.marker();
    }

    private static String worse(String a, String b) {
        if (MARKER_UNAVAILABLE.equals(a) || MARKER_UNAVAILABLE.equals(b)) {
            return MARKER_UNAVAILABLE;
        }
        return a != null ? a : b;
    }

    /**
     * Outcome of redacting one payload side. Mirrors the guardrails-service statuses so the cases
     * stay distinct. The result never depends on a guardrail policy.
     */
    public enum RedactionStatus {
        /** Masked in full and the payload changed. */
        REDACTED,
        /** Masked by the regex and secret scrub only, because the Anonymize scanner did not answer. */
        REDACTED_BASIC,
        /** Masked in full and nothing needed masking. */
        UNCHANGED,
        /** Oversize payload, deadline, error, service down or breaker open. */
        UNAVAILABLE;

        /** Anything this client does not recognise is UNAVAILABLE, never silently UNCHANGED. */
        public static RedactionStatus parse(String value) {
            if (value == null) {
                return UNAVAILABLE;
            }
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                return UNAVAILABLE;
            }
        }
    }

    /** Outcome of one payload side. */
    public static class Side {
        private final RedactionStatus status;
        private final String payload;

        public Side(RedactionStatus status, String payload) {
            this.status = status == null ? RedactionStatus.UNAVAILABLE : status;
            // The masked payload only exists for the two redacted statuses; ignore a stray one otherwise.
            this.payload = carriesPayload(this.status) ? payload : null;
        }

        static boolean carriesPayload(RedactionStatus status) {
            return status == RedactionStatus.REDACTED || status == RedactionStatus.REDACTED_BASIC;
        }

        public RedactionStatus getStatus() { return status; }

        /** The masked payload, non-null only when status is REDACTED or REDACTED_BASIC. */
        public String getPayload() { return payload; }

        /**
         * What the stored record must say about this side: null when it is fully redacted or had
         * nothing to mask, "BASIC" when only the regex scrub ran, "UNAVAILABLE" when it is not redacted.
         */
        public String marker() {
            if (status == RedactionStatus.UNAVAILABLE) {
                return MARKER_UNAVAILABLE;
            }
            return status == RedactionStatus.REDACTED_BASIC ? MARKER_BASIC : null;
        }
    }

    private final String id;
    private final Side request;
    private final Side response;

    public RedactionResult(String id, Side request, Side response) {
        this.id = id;
        this.request = request;
        this.response = response;
    }

    public static RedactionResult unavailable(RedactionItem item) {
        return new RedactionResult(item.getId(),
                item.getRequestPayload() == null ? null : new Side(RedactionStatus.UNAVAILABLE, null),
                item.getResponsePayload() == null ? null : new Side(RedactionStatus.UNAVAILABLE, null));
    }

    public String getId() { return id; }
    public Side getRequest() { return request; }
    public Side getResponse() { return response; }
}
