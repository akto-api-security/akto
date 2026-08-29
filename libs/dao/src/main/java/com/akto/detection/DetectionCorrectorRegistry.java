package com.akto.detection;

/**
 * Process-wide holder for the active {@link DetectionCorrector}.
 *
 * Follows the same shape as the DataActor registry: libs/dao declares the capability, and the
 * process that actually wants it installs an implementation at startup. Anything that never calls
 * {@link #install} keeps the no-op and makes no outbound calls.
 */
public class DetectionCorrectorRegistry {

    /**
     * Verbose per-value logging. Off by default because it names the JSON paths being classified
     * and makes one log line per value; turn it on to work out why a value is not being labelled.
     * Set from account settings by the installer.
     */
    private static volatile DetectionCorrector corrector = DetectionCorrector.NO_OP;
    private static volatile boolean debugEnabled = false;

    private DetectionCorrectorRegistry() {
    }

    public static void install(DetectionCorrector detectionCorrector) {
        corrector = detectionCorrector == null ? DetectionCorrector.NO_OP : detectionCorrector;
    }

    public static DetectionCorrector get() {
        return corrector;
    }

    public static boolean isActive() {
        return corrector != DetectionCorrector.NO_OP;
    }

    public static void reset() {
        corrector = DetectionCorrector.NO_OP;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void setDebugEnabled(boolean value) {
        debugEnabled = value;
    }
}
