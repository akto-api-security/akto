package com.akto.behaviour_modelling.impl;

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

        List<ApiSequences> sequences = new ArrayList<>();

        for (Map.Entry<TransitionKey, Long> entry : transitionCounts.entrySet()) {
            TransitionKey key = entry.getKey();
            long transitionCount = entry.getValue();

            ApiInfoKey[] seq = key.getSequence();
            ApiInfoKey fromApi = seq[0];

            Long prevStateCount = apiCounts.get(fromApi);
            if (prevStateCount == null || prevStateCount == 0) continue;

            float probability = transitionCount / (float) prevStateCount;

            List<String> paths = new ArrayList<>();
            for (ApiInfoKey apiInfoKey : seq) {
                paths.add(apiInfoKey.toString());
            }

            // id=0: upsert in DbActor matches on (apiCollectionId, paths), id unused for insert key
            ApiSequences apiSequence = new ApiSequences(
                    0,
                    fromApi.getApiCollectionId(),
                    paths,
                    (int) transitionCount,
                    prevStateCount.intValue(),
                    0f,
                    probability
            );
            sequences.add(apiSequence);
        }

        if (sequences.isEmpty()) return;

        try {
            dataActor.writeApiSequences(sequences);
            loggerMaker.infoAndAddToDb("Flushed " + sequences.size() + " api sequences for window ["
                    + snapshot.getWindowStart() + " - " + snapshot.getWindowEnd() + "]");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error flushing api sequences: " + e.getMessage(), LogDb.RUNTIME);
        }
    }
}
