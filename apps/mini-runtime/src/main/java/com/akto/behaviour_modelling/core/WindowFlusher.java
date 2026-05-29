package com.akto.behaviour_modelling.core;

import com.akto.behaviour_modelling.model.WindowSnapshot;

/**
 * Sends a completed window snapshot to the backend.
 * Called asynchronously after each window ends, so implementations
 * do not need to be fast but must handle their own errors.
 */
public interface WindowFlusher {
    void flush(WindowSnapshot snapshot);
}
