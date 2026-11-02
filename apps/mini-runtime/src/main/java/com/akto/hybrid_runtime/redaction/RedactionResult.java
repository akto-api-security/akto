package com.akto.hybrid_runtime.redaction;

/** Redaction outcome for one RedactionItem; a side that was not sent is null. */
public class RedactionResult {

    public static final String MARKER_UNAVAILABLE = "UNAVAILABLE";
    public static final String MARKER_BASIC = "BASIC";

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

    public enum RedactionStatus {
        REDACTED,
        REDACTED_BASIC,
        UNCHANGED,
        UNAVAILABLE;

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

    public static class Side {
        private final RedactionStatus status;
        private final String payload;

        public Side(RedactionStatus status, String payload) {
            this.status = status == null ? RedactionStatus.UNAVAILABLE : status;
            // The masked payload only exists for the two redacted statuses.
            this.payload = carriesPayload(this.status) ? payload : null;
        }

        static boolean carriesPayload(RedactionStatus status) {
            return status == RedactionStatus.REDACTED || status == RedactionStatus.REDACTED_BASIC;
        }

        public RedactionStatus getStatus() { return status; }

        public String getPayload() { return payload; }

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
