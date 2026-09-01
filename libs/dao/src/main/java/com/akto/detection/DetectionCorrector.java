package com.akto.detection;

import com.akto.dto.type.SingleTypeInfo.SubType;

import java.util.List;
import java.util.Map;

/**
 * Refines locally-detected data types using knowledge Akto does not have.
 *
 * Local detection can tell that a value is shaped like an email; it cannot tell whether that email
 * belongs to a customer, an employee or a partner, because that fact lives in a system outside
 * Akto. A corrector bridges that gap: it is handed the values whose local type is configured as a
 * trigger, and returns more specific data type names for the ones it recognises.
 *
 * Contract, deliberately fail-open:
 * - a candidate the corrector cannot place is simply omitted from the result and keeps its local type
 * - any failure, timeout or misconfiguration must degrade to "no corrections", never to an exception
 *
 * Implementations live outside libs/dao so this module stays free of network concerns.
 */
public interface DetectionCorrector {

    /**
     * Whether a locally-detected subtype is worth sending for refinement. Called on the detection
     * hot path for every value, so it must be cheap and allocation-free.
     */
    boolean isTrigger(SubType localSubType);

    /**
     * @param candidates values whose local subtype passed {@link #isTrigger}
     * @return candidate idx -> corrected data type name, containing only the entries the corrector
     *         could actually place. Never null.
     */
    Map<Integer, String> correct(List<DetectionCandidate> candidates);

    /**
     * Does nothing and triggers on nothing. The default everywhere the feature is not configured,
     * which keeps dashboard, testing and analyser processes from ever making an outbound call.
     */
    DetectionCorrector NO_OP = new DetectionCorrector() {
        @Override
        public boolean isTrigger(SubType localSubType) {
            return false;
        }

        @Override
        public Map<Integer, String> correct(List<DetectionCandidate> candidates) {
            return java.util.Collections.emptyMap();
        }
    };
}
