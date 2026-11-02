package com.akto.detection;

import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.SingleTypeInfo.SubType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decides the data type a value is finally recorded under.
 *
 * Local detection can tell that a value looks like an email or a card. It cannot tell whose it is,
 * because that lives in a system outside Akto. This is where the answer to that second question is
 * applied, when we have one.
 *
 * Nothing here waits. An answer we already hold is a map lookup; a parameter we have not heard about
 * is queued for classification and keeps its locally detected type meanwhile. That is the whole
 * trade: the ingestion path never blocks on the classifier, and the first values seen on a parameter
 * carry the unrefined label until the answer catches up.
 *
 * Everything is fail-open. With the feature off, or if anything throws, the value is recorded under
 * the type local detection produced.
 */
public class DataTypeRefiner {

    private static final Logger logger = LoggerFactory.getLogger(DataTypeRefiner.class);

    /** Answers that named a data type which does not exist, so could not be applied. */
    public static final AtomicLong droppedUnknownLabelCount = new AtomicLong();

    private DataTypeRefiner() {
    }

    /**
     * Returns the type this value should be recorded under: the refined one if the classifier has
     * already told us what this parameter carries, otherwise the locally detected one.
     */
    public static SubType refine(ParamLocation location, String value, SubType localSubType) {
        if (localSubType == null || location == null) return localSubType;
        if (!DetectionCorrectorRegistry.isActive()) return localSubType;

        try {
            if (!DetectionCorrectorRegistry.get().isTrigger(localSubType)) return localSubType;

            String known = DetectionCorrectorRegistry.getParamVerdictCache().get(location);

            if (known == null) {
                queue(location, value, localSubType);
                return localSubType;
            }
            if (ParamVerdictCache.NO_CORRECTION.equals(known)) return localSubType;
            if (known.equals(localSubType.getName())) return localSubType;

            SubType refined = resolveLabel(known);
            if (refined == null) {
                droppedUnknownLabelCount.incrementAndGet();
                logger.warn("[detection-corrector] answer named data type '" + known + "' for " + location
                        + " but no active data type exists by that name, keeping " + localSubType.getName()
                        + ". Create an active data type with that name for it to take effect.");
                return localSubType;
            }

            if (DetectionCorrectorRegistry.isDebugEnabled()) {
                logger.info("[detection-corrector] " + location + " " + localSubType.getName() + " -> " + known);
            }
            return refined;
        } catch (Exception e) {
            logger.error("[detection-corrector] could not refine " + location + ", keeping "
                    + localSubType.getName() + ": " + e.getMessage());
            return localSubType;
        }
    }

    /**
     * Asks for this parameter to be classified. Best effort: if the queue is unavailable the value is
     * still recorded under its local type, and the parameter is offered again next time it is seen.
     */
    private static void queue(ParamLocation location, String value, SubType localSubType) {
        CandidatePublisher publisher = DetectionCorrectorRegistry.getCandidatePublisher();
        if (publisher == null) return;
        try {
            publisher.publish(Collections.singletonList(
                    new DetectionCandidate(location, location.getParam(), value, localSubType.getName())));
        } catch (Exception e) {
            logger.error("[detection-corrector] could not queue " + location + ": " + e.getMessage());
        }
    }

    /**
     * Maps a data type name onto a real SubType.
     *
     * Returns null for anything not registered. That matters: ParamId.setSubTypeString falls back to
     * GENERIC for names it cannot resolve, so recording an unregistered label would quietly downgrade
     * the value on the next read. Keeping the local type is the safer failure.
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
}
