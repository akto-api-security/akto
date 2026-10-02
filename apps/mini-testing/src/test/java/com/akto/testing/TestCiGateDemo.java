package com.akto.testing;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TestCiGateDemo {

    @Test
    public void testPassBranch() {
        assertEquals("pass", CiGateDemo.classify(60));
    }
}
