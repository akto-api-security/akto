package com.akto.types;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestCappedSet {

    @Test
    public void testAdd() {
        CappedSet<String> values = new CappedSet<>();
        for (int i = 0; i< CappedSet.LIMIT +10; i++) {
            values.add(i+"");
        }

        assertEquals(CappedSet.LIMIT, values.elements.size());

        for (int i = 0; i< CappedSet.LIMIT; i++) {
            assertTrue(values.elements.contains(i+""));
        }

        for (int i = CappedSet.LIMIT; i< CappedSet.LIMIT +10; i++) {
            assertFalse(values.elements.contains(i+""));
        }

    }
}
