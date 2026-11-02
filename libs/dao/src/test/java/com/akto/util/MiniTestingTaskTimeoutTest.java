package com.akto.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MiniTestingTaskTimeoutTest {

    @Test
    public void defaultPerTestTimeoutIsFiveMinutesWhenUnset() {
        if (System.getenv("MINI_TESTING_TASK_TIMEOUT_SECONDS") == null) {
            assertEquals(300, Constants.MINI_TESTING_TASK_TIMEOUT_SECONDS);
        }
        assertTrue(Constants.MINI_TESTING_TASK_TIMEOUT_SECONDS > 0);
    }
}
