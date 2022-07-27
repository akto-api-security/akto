package com.akto.types;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestCappedSet {

    @Test
    public void testAdd() {
        CappedSet<String> values = new CappedSet<>();
        for (int i=0; i< CappedSet.limit+10; i++) {
            values.add(i+"");
        }

        assertEquals(CappedSet.limit, values.elements.size());

        for (int i=0; i< CappedSet.limit; i++) {
            assertTrue(values.elements.contains(i+""));
        }

        for (int i=CappedSet.limit; i< CappedSet.limit+10; i++) {
            assertFalse(values.elements.contains(i+""));
        }

    }
}
