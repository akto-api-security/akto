package com.akto.detection;

/**
 * Process-wide holder for the pieces the ingestion path needs to refine data types.
 *
 * Follows the same shape as the DataActor registry: libs/dao declares the capability, and the
 * process that actually wants it installs the implementation at startup. Anything that never calls
 * {@link #install} keeps the no-op and makes no outbound calls.
 *
 * Three things are held together because the feature is only usable with all three: the corrector
 * (which knows which data types are worth classifying), the cache of answers already received, and
 * the queue that unknown parameters are handed to.
 */
public class DetectionCorrectorRegistry {

    private static volatile DetectionCorrector corrector = DetectionCorrector.NO_OP;
    private static volatile ParamVerdictCache paramVerdictCache = null;
    private static volatile CandidatePublisher candidatePublisher = null;

    /**
     * Verbose per-value logging. Off by default because it names the JSON paths being classified and
     * makes one log line per value; turn it on to work out why a value is not being labelled. Set
     * from account settings by the installer.
     */
    private static volatile boolean debugEnabled = false;

    private DetectionCorrectorRegistry() {
    }

    public static void install(DetectionCorrector detectionCorrector, ParamVerdictCache cache,
                               CandidatePublisher publisher) {
        corrector = detectionCorrector == null ? DetectionCorrector.NO_OP : detectionCorrector;
        paramVerdictCache = cache;
        candidatePublisher = publisher;
    }

    public static void reset() {
        corrector = DetectionCorrector.NO_OP;
        paramVerdictCache = null;
        candidatePublisher = null;
    }

    /** True only when everything the ingestion path needs is present. */
    public static boolean isActive() {
        return corrector != DetectionCorrector.NO_OP
                && paramVerdictCache != null
                && candidatePublisher != null;
    }

    public static DetectionCorrector get() {
        return corrector;
    }

    public static ParamVerdictCache getParamVerdictCache() {
        return paramVerdictCache;
    }

    public static CandidatePublisher getCandidatePublisher() {
        return candidatePublisher;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void setDebugEnabled(boolean value) {
        debugEnabled = value;
    }
}
