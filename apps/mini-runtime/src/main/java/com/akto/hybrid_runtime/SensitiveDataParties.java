package com.akto.hybrid_runtime;

import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.SensitiveDataCount;
import com.akto.hybrid_parsers.HttpCallParser;

import java.util.Map;

/**
 * Identifies the service that SENT a message, as an API collection id.
 *
 * The receiving end needs nothing: a message's own collection is already the callee's, because
 * collection routing is keyed on the Host header for outbound traffic exactly so that it names the
 * service being called. Only the caller is missing, and the runtime already knows how to name it -
 * {@code markAiAgentCaller} attributes outbound calls from the same pod label, through the same
 * hash. Reusing both means one service is one collection whichever end of a call it is on.
 *
 * Only outbound captures can identify a caller. On inbound the pod labels describe the callee, so
 * there is nobody to attribute and the bucket is left without a sender; it still counts in full
 * towards the service that received it.
 *
 * Deliberately NOT derived from {@code apc-source-service-name}. That header is stamped once by
 * whoever started the call chain and then propagates, so it names the ORIGINATOR rather than the
 * immediate caller: in 125 captured samples one value of it appears against four different pod
 * labels. Falling back to it would give the same edge a different identity depending on which
 * signal happened to be present.
 */
public class SensitiveDataParties {

    private SensitiveDataParties() {
    }

    /**
     * The caller's collection id, or {@link SensitiveDataCount#UNKNOWN_COLLECTION_ID}.
     *
     * The id is {@link ApiCollection#generateServiceTagCollectionId}, the same hash the caller's own
     * collection is keyed by, so no collection has to exist yet for the counts to line up once one
     * does.
     */
    public static int senderCollectionId(HttpResponseParams responseParams) {
        if (responseParams == null) return SensitiveDataCount.UNKNOWN_COLLECTION_ID;
        if (!HttpCallParser.DIRECTION_OUTBOUND.equals(responseParams.getDirection())) {
            return SensitiveDataCount.UNKNOWN_COLLECTION_ID;
        }

        Map<String, String> tags = HttpCallParser.parseTagsMap(responseParams.getTags());
        if (tags == null) return SensitiveDataCount.UNKNOWN_COLLECTION_ID;

        String service = tags.get(HttpCallParser.SERVICE_TAG_KEY);
        if (service == null || service.trim().isEmpty()) return SensitiveDataCount.UNKNOWN_COLLECTION_ID;

        return ApiCollection.generateServiceTagCollectionId(service.trim());
    }
}
