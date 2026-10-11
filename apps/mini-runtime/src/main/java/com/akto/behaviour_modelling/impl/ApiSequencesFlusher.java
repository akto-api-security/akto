package com.akto.behaviour_modelling.impl;

import com.akto.behaviour_modelling.core.MarkovModelBuilder;
import com.akto.behaviour_modelling.core.WindowFlusher;
import com.akto.behaviour_modelling.model.TransitionKey;
import com.akto.behaviour_modelling.model.WindowSnapshot;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiSequences;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ApiSequencesFlusher implements WindowFlusher {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiSequencesFlusher.class, LogDb.RUNTIME);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    @Override
    public void flush(WindowSnapshot snapshot) {
        if (snapshot.isEmpty()) return;

        Map<ApiInfoKey, Long> apiCounts = snapshot.getApiCounts();
        Map<TransitionKey, Long> transitionCounts = snapshot.getTransitionCounts();

        // VOMC pruning: remove redundant higher-order contexts
        Map<TransitionKey, Long> prunedTransitions = MarkovModelBuilder.prune(transitionCounts);

        List<ApiSequences> sequences = new ArrayList<>();

        for (Map.Entry<TransitionKey, Long> entry : prunedTransitions.entrySet()) {
            TransitionKey key = entry.getKey();
            long transitionCount = entry.getValue();

            ApiInfoKey[] seq = key.getSequence();
            ApiInfoKey fromApi = seq[0];
            ApiInfoKey lastApi = seq[seq.length - 1];

            Long prevStateCount = apiCounts.get(fromApi);
            if (prevStateCount == null || prevStateCount == 0) continue;

            Long lastApiCount = apiCounts.get(lastApi);
            int lastStateCount = (lastApiCount != null) ? lastApiCount.intValue() : 0;

            List<String> paths = new ArrayList<>();
            for (ApiInfoKey apiInfoKey : seq) {
                paths.add(apiInfoKey.toString());
            }

            // probability and precedenceScore are computed in the DB from cumulative counts
            ApiSequences apiSequence = new ApiSequences(
                    fromApi.getApiCollectionId(),
                    paths,
                    (int) transitionCount,
                    prevStateCount.intValue(),
                    lastStateCount,
                    0f,
                    0f
            );
            sequences.add(apiSequence);
        }

        if (sequences.isEmpty()) return;

        // Keep only top 1000 sequences by transition count to cap DB writes and filter noise
        int maxSequences = 1000;
        if (sequences.size() > maxSequences) {
            Collections.sort(sequences, Comparator.comparingInt(ApiSequences::getTransitionCount).reversed());
            sequences = sequences.subList(0, maxSequences);
        }

        try {
            dataActor.writeApiSequences(sequences);
            loggerMaker.infoAndAddToDb("Flushed " + sequences.size() + " api sequences (pruned from "
                    + transitionCounts.size() + ") for window ["
                    + snapshot.getWindowStart() + " - " + snapshot.getWindowEnd() + "]");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error flushing api sequences: " + e.getMessage(), LogDb.RUNTIME);
        }
    }
}
