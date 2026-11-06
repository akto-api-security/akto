package com.akto.behaviour_modelling.impl;

import com.akto.data_actor.DataActorFactory;
import com.akto.dto.SequenceCount;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.List;
import java.util.function.Consumer;

public class SequenceCountFlusher implements Consumer<List<SequenceCount>> {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SequenceCountFlusher.class, LogDb.RUNTIME);
    public static final int BATCH_SIZE = 1000;

    @Override
    public void accept(List<SequenceCount> counts) {
        if (counts == null || counts.isEmpty()) return;
        for (int from = 0; from < counts.size(); from += BATCH_SIZE) {
            List<SequenceCount> batch = counts.subList(from, Math.min(from + BATCH_SIZE, counts.size()));
            try {
                DataActorFactory.fetchInstance().writeSequenceCounts(batch);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error flushing sequence counts: " + e.getMessage(), LogDb.RUNTIME);
            }
        }
        loggerMaker.infoAndAddToDb("Flushed " + counts.size() + " sequence counts");
    }
}
