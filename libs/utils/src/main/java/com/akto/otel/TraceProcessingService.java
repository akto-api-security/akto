package com.akto.otel;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;

import java.util.List;

public interface TraceProcessingService {

    void processTraces(List<HttpResponseParams> traces, int accountId) throws Exception;

    class Holder {
        private static TraceProcessingService instance;
        private static final LoggerMaker logger = new LoggerMaker(TraceProcessingService.class, LoggerMaker.LogDb.DASHBOARD);

        public static void setInstance(TraceProcessingService service) {
            instance = service;
        }

        public static TraceProcessingService getInstance() {
            if (instance == null) {
                logger.error("TraceProcessingService instance not set. Please set it before using.");
                throw new IllegalStateException("TraceProcessingService instance not set");
            }
            return instance;
        }
    }
}
