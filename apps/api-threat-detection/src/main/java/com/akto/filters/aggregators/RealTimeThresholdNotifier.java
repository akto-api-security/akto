package com.akto.filters.aggregators;

import com.akto.dto.HttpResponseParams;

/*
 * RealTimeThresholdNotifier is an abstract class that provides the basic structure for all the notifiers.
 * It provides the basic structure for the notifiers to work with.
 */
public abstract class RealTimeThresholdNotifier {

    protected static final int NOTIFICATION_COOLDOWN_MINUTES = 60;

    /*
     * Check if the aggregator should notify the system.
     * Types of notifiers:
     * Window based Threshold based notifier (for something like X bad requests in
     * last Y minutes)
     */
    public boolean shouldNotify(String aggKey, HttpResponseParams responseParam) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
