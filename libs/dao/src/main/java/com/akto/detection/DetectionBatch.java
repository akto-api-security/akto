package com.akto.detection;

import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.SingleTypeInfo.SubType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects values that need external refinement across a whole processing batch, resolves them in
 * one call, and only then lets them be recorded.
 *
 * Why deferral rather than an inline lookup: detection runs per value, so calling out from there
 * would mean one network round trip per field. Instead a value whose local type is a trigger has
 * its recording deferred; when the batch closes, every deferred value is resolved together and
 * recorded under its final type. Detection still runs exactly once per value, and nothing has been
 * written to the database yet at that point, so no correction of already-stored data is needed.
 *
 * Scope is held in a ThreadLocal because runtime processing is single-threaded per instance and
 * threading an accumulator through every call site would touch a lot of shared signatures.
 *
 * Everything here is fail-open. If no batch is open, if no corrector is installed, or if anything
 * throws, values are recorded under the type local detection produced.
 */
public class DetectionBatch implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DetectionBatch.class);

    /** Safety valve: flush early rather than hold an unbounded number of deferred values. */
    static final int MAX_DEFERRED = 10_000;

    private static final ThreadLocal<DetectionBatch> CURRENT = new ThreadLocal<>();

    public static final AtomicLong droppedUnknownLabelCount = new AtomicLong();

    /** Records a value once its final subtype is known. */
    public interface DeferredRecord {
        void record(SubType subType);
    }

    private static class Pending {
        final String jsonPath;
        final String value;
        final SubType localSubType;
        final DeferredRecord recorder;
        final int apiCollectionId;
        final String url;
        final String method;
        final String param;

        Pending(String jsonPath, String value, SubType localSubType, DeferredRecord recorder,
                int apiCollectionId, String url, String method, String param) {
            this.jsonPath = jsonPath;
            this.value = value;
            this.localSubType = localSubType;
            this.recorder = recorder;
            this.apiCollectionId = apiCollectionId;
            this.url = url;
            this.method = method;
            this.param = param;
        }
    }

    private final DetectionCorrector corrector;
    private final List<Pending> pending = new ArrayList<>();

    private DetectionBatch(DetectionCorrector corrector) {
        this.corrector = corrector;
    }

    /**
     * Opens a batch for the current thread. Returns null when no corrector is installed, so callers
     * pay nothing for the feature when it is switched off.
     */
    public static DetectionBatch open() {
        if (!DetectionCorrectorRegistry.isActive()) return null;
        DetectionBatch batch = new DetectionBatch(DetectionCorrectorRegistry.get());
        CURRENT.set(batch);
        return batch;
    }

    public static DetectionBatch current() {
        return CURRENT.get();
    }

    /**
     * Resolves and records everything deferred so far on this thread, if a batch is open.
     *
     * Must be called at the end of a detection phase and before any template merging: mergeFrom()
     * deep-copies KeyTypes, so a deferred record applied after a merge would land on an orphaned
     * object and be silently dropped.
     */
    public static void flushCurrent() {
        DetectionBatch batch = CURRENT.get();
        if (batch != null) batch.flush();
    }

    /**
     * Whether this value should have its recording deferred. Cheap - it is called for every value
     * that flows through detection.
     */
    public boolean shouldDefer(SubType localSubType) {
        if (localSubType == null) return false;
        try {
            return corrector.isTrigger(localSubType);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Defers recording until the batch is flushed. If the corrector places this value, the recorder
     * runs with the corrected subtype; otherwise it runs with localSubType.
     */
    public void defer(String jsonPath, String value, SubType localSubType, DeferredRecord recorder,
                     int apiCollectionId, String url, String method, String param) {
        pending.add(new Pending(jsonPath, value, localSubType, recorder, apiCollectionId, url, method, param));
        if (pending.size() >= MAX_DEFERRED) {
            flush();
        }
    }

    /**
     * Resolves everything deferred so far and records it. Safe to call repeatedly.
     */
    public void flush() {
        if (pending.isEmpty()) return;

        long startedAtMs = System.currentTimeMillis();
        List<Pending> batch = new ArrayList<>(pending);
        pending.clear();

        Map<Integer, String> corrections = Collections.emptyMap();
        try {
            List<DetectionCandidate> candidates = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                Pending p = batch.get(i);
                DetectionCandidate c = new DetectionCandidate(i, p.jsonPath, p.value, p.localSubType.getName());
                c.setApiCollectionId(p.apiCollectionId);
                c.setUrl(p.url);
                c.setMethod(p.method);
                c.setParam(p.param);
                candidates.add(c);
            }
            if (DetectionCorrectorRegistry.isDebugEnabled()) {
                for (DetectionCandidate candidate : candidates) {
                    logger.info("[detection-corrector] asking about idx=" + candidate.getIdx()
                            + " path=" + candidate.getJsonPath() + " localType=" + candidate.getType());
                }
            }

            Map<Integer, String> result = corrector.correct(candidates);
            if (result != null) corrections = result;
        } catch (Exception e) {
            // Fail open: every value below falls back to its locally detected type.
            logger.error("[detection-corrector] failed for " + batch.size()
                    + " candidates, keeping locally detected types: " + e.getMessage());
        }

        int correctedInThisBatch = 0;
        int droppedInThisBatch = 0;
        int unchangedInThisBatch = 0;

        for (int i = 0; i < batch.size(); i++) {
            Pending p = batch.get(i);
            SubType finalSubType = p.localSubType;

            String label = corrections.get(i);
            if (label != null && !label.equals(p.localSubType.getName())) {
                SubType resolved = resolveLabel(label);
                if (resolved != null) {
                    finalSubType = resolved;
                    correctedInThisBatch++;
                    if (DetectionCorrectorRegistry.isDebugEnabled()) {
                        logger.info("[detection-corrector] corrected path=" + p.jsonPath + " "
                                + p.localSubType.getName() + " -> " + label);
                    }
                } else {
                    droppedUnknownLabelCount.incrementAndGet();
                    droppedInThisBatch++;
                    logger.warn("[detection-corrector] returned data type '" + label + "' for path="
                            + p.jsonPath + " but no active data type by that name exists, keeping "
                            + p.localSubType.getName()
                            + ". Create an active data type with that name for it to take effect.");
                }
            } else {
                unchangedInThisBatch++;
                if (DetectionCorrectorRegistry.isDebugEnabled()) {
                    logger.info("[detection-corrector] no correction for path=" + p.jsonPath
                            + ", keeping " + p.localSubType.getName());
                }
            }

            try {
                p.recorder.record(finalSubType);
            } catch (Exception e) {
                logger.error("[detection-corrector] failed recording data type for path="
                        + p.jsonPath + ": " + e.getMessage());
            }
        }

        logger.info("[detection-corrector] batch flushed: candidates=" + batch.size()
                + " corrected=" + correctedInThisBatch
                + " unchanged=" + unchangedInThisBatch
                + " droppedUnknownLabel=" + droppedInThisBatch
                + " tookMs=" + (System.currentTimeMillis() - startedAtMs));
    }

    /**
     * Maps a data type name returned by the corrector onto a real SubType.
     *
     * Returns null for anything not already registered. That matters: ParamId.setSubTypeString
     * falls back to GENERIC for names it cannot resolve, so writing an unregistered label would
     * silently lose the value on the next read. Dropping it keeps the local type instead.
     */
    private static SubType resolveLabel(String label) {
        if (label == null || label.isEmpty()) return null;

        SubType builtIn = SingleTypeInfo.subTypeMap.get(label);
        if (builtIn != null) return builtIn;

        Map<String, CustomDataType> customDataTypeMap =
                SingleTypeInfo.getCustomDataTypeMap(Context.getActualAccountId());
        if (customDataTypeMap == null) return null;

        CustomDataType customDataType = customDataTypeMap.get(label);
        if (customDataType == null || !customDataType.isActive()) return null;

        return customDataType.toSubType();
    }

    @Override
    public void close() {
        try {
            flush();
        } finally {
            CURRENT.remove();
        }
    }
}
