package com.akto.filters.aggregators.notifier;

import java.util.Optional;

import com.akto.dto.HttpResponseParams;

/*
 * RealTimeThresholdNotifier is an abstract class that provides the basic structure for all the notifiers.
 * It provides the basic structure for the notifiers to work with.
 */
public abstract class RealTimeThresholdNotifier {
    public Optional<String> generateKey(HttpResponseParams responseParams) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /*
     * Check if the aggregator should qualify for notification.
     */
    public boolean qualifyForNotification(HttpResponseParams responseParam) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /*
     * Check if the aggregator should notify the system.
     * Types of notifiers:
     * Window based Threshold based notifier (for something like X bad requests in
     * last Y minutes)
     */
    public boolean shouldNotify(HttpResponseParams responseParam) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public int notificationCooldown() {
        return 60 * 60; // Default is 1 hour
    }

    public void close() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
