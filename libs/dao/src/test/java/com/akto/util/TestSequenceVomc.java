package com.akto.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestSequenceVomc {

    private static Map<String, Long> counts(long a, long b, long c) {
        Map<String, Long> m = new HashMap<>();
        m.put("a", a);
        m.put("b", b);
        m.put("c", c);
        return m;
    }

    @Test
    public void testCredibleIntervalKnownValues() {
        double[] ci = SequenceVomc.credibleInterval(169, 15442);
        assertEquals(0.0096634, ci[0], 1e-5);
        assertEquals(0.0124236, ci[1], 1e-5);
        assertTrue(ci[1] - ci[0] < 0.003);

        double[] uniform = SequenceVomc.credibleInterval(0, 0);
        assertEquals(0.05, uniform[0], 1e-9);
        assertEquals(0.95, uniform[1], 1e-9);

        double[] zero = SequenceVomc.credibleInterval(0, 10);
        assertEquals(1 - Math.pow(0.95, 1.0 / 11), zero[0], 1e-9);
        assertEquals(1 - Math.pow(0.05, 1.0 / 11), zero[1], 1e-9);

        double[] all = SequenceVomc.credibleInterval(5, 5);
        assertEquals(Math.pow(0.05, 1.0 / 6), all[0], 1e-9);
        assertEquals(Math.pow(0.95, 1.0 / 6), all[1], 1e-9);
    }

    @Test
    public void testCredibleIntervalLargeCounts() {
        double[] ci = SequenceVomc.credibleInterval(500000, 1000000);
        assertTrue(ci[0] < 0.5 && ci[1] > 0.5);
        assertEquals(0.5 - 1.6449 * Math.sqrt(0.25 / 1000002), ci[0], 1e-5);
    }

    @Test
    public void testCollapseCloudflareExample() {
        Map<String, Long> parent = counts(1555, 13718, 169);
        Map<String, Long> child = counts(173, 1367, 13);
        assertTrue(SequenceVomc.isCollapsed(2, child, parent));
    }

    @Test
    public void testNoCollapseForDifferentDistribution() {
        Map<String, Long> parent = counts(1555, 13718, 169);
        Map<String, Long> child = counts(1500, 50, 3);
        assertFalse(SequenceVomc.isCollapsed(2, child, parent));
    }

    @Test
    public void testNextOnlyInParentCountsAsZero() {
        Map<String, Long> parent = counts(1555, 13718, 5000);
        Map<String, Long> child = new HashMap<>();
        child.put("a", 173L);
        child.put("b", 1367L);
        assertFalse(SequenceVomc.isCollapsed(2, child, parent));
    }

    @Test
    public void testOrderZeroAndMissingParentNeverCollapse() {
        Map<String, Long> m = counts(1555, 13718, 169);
        assertFalse(SequenceVomc.isCollapsed(0, m, m));
        assertFalse(SequenceVomc.isCollapsed(1, m, null));
        assertFalse(SequenceVomc.isCollapsed(1, m, new HashMap<String, Long>()));
    }

    @Test
    public void testPrecedence() {
        assertEquals(0.845, SequenceVomc.precedence(169, 200), 1e-12);
        assertEquals(1.0, SequenceVomc.precedence(200, 200), 1e-12);
        assertEquals(0.0, SequenceVomc.precedence(10, 0), 1e-12);
    }

    @Test
    public void testContextKeys() {
        assertEquals("", SequenceVomc.contextKey(new ArrayList<String>()));
        assertEquals("", SequenceVomc.contextKey(null));
        assertNull(SequenceVomc.parentContextKey(new ArrayList<String>()));
        assertEquals("", SequenceVomc.parentContextKey(Arrays.asList("x")));
        assertEquals("y|z", SequenceVomc.contextKey(Arrays.asList("y", "z")));
        assertEquals("y|z", SequenceVomc.parentContextKey(Arrays.asList("x", "y", "z")));
    }
}
