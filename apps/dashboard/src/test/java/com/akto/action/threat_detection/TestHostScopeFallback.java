package com.akto.action.threat_detection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.After;
import org.junit.Test;

import com.akto.dao.context.Context;

/* An older threat backend rejecting the host scope sends limited users to their own events for a while, then it is tried again. */
public class TestHostScopeFallback {

    static boolean accepts() throws Exception {
        Method method = AbstractThreatDetectionAction.class.getDeclaredMethod("backendAcceptsHostScope");
        method.setAccessible(true);
        return (boolean) method.invoke(null);
    }

    static void rejectedSecondsAgo(int seconds) throws Exception {
        Field field = AbstractThreatDetectionAction.class.getDeclaredField("hostScopeRejectedAt");
        field.setAccessible(true);
        field.setInt(null, Context.now() - seconds);
    }

    @After
    public void reset() throws Exception {
        Field field = AbstractThreatDetectionAction.class.getDeclaredField("hostScopeRejectedAt");
        field.setAccessible(true);
        field.setInt(null, 0);
    }

    @Test
    public void testRetriedAfterAWhile() throws Exception {
        assertTrue(accepts());
        assertFalse(AbstractThreatDetectionAction.hostScopeRejected(null, 400)); // no host scope sent: not about it
        assertFalse(AbstractThreatDetectionAction.hostScopeRejected(Collections.singletonMap("hosts", "a"), 500));
        assertTrue(accepts());

        assertTrue(AbstractThreatDetectionAction.hostScopeRejected(Collections.singletonMap("hosts", "a"), 400));
        assertFalse(accepts());
        rejectedSecondsAgo(11 * 60);
        assertTrue(accepts()); // the backend may have been upgraded since
    }
}
