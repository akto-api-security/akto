package com.akto.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MaxPollIntervalMsTest {

    @Test
    public void defaultIsKafkaDefaultNotTenSeconds() {
        if (System.getenv("MAX_POLL_INTERVAL_MS") == null) {
            assertEquals(300000, Constants.MAX_POLL_INTERVAL_MS);
        }
        assertTrue("max.poll.interval.ms of 10s is what evicted the consumer group",
                Constants.MAX_POLL_INTERVAL_MS > 10_000);
    }
}
