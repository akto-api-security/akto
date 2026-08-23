package com.akto.service.insights;

/**
 * Wraps a loaded value so "absent" and "zero" can never collapse into each other. An empty
 * Mongo read is a real answer (ok=true, value=empty list); a failed external call is not
 * (ok=false) and must surface as a DataGap rather than a confident zero.
 */
public class Loaded<T> {

    private final T value;
    private final boolean ok;
    private final String failureReason;

    private Loaded(T value, boolean ok, String failureReason) {
        this.value = value;
        this.ok = ok;
        this.failureReason = failureReason;
    }

    public static <T> Loaded<T> of(T value) {
        return new Loaded<>(value, true, null);
    }

    public static <T> Loaded<T> failed(String reason) {
        return new Loaded<>(null, false, reason);
    }

    public T getValue() {
        return value;
    }

    public boolean isOk() {
        return ok;
    }

    public String getFailureReason() {
        return failureReason;
    }

    /** True only when the load succeeded AND produced a non-null value. */
    public boolean hasValue() {
        return ok && value != null;
    }
}
