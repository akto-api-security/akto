package com.akto.dao;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class LogsMongoTest {

    @Test
    public void emptyListMeansNoAccount() {
        assertFalse(LogsMongo.parseAll(""));
        assertTrue(LogsMongo.parseIds("").isEmpty());
    }

    @Test
    public void listMeansOnlyThoseAccounts() {
        assertFalse(LogsMongo.parseAll("1000000,1758595089"));
        assertTrue(LogsMongo.parseIds("1000000, 1758595089,nginx").size() == 2);
        assertTrue(LogsMongo.parseIds("1000000, 1758595089,nginx").containsAll(Arrays.asList(1000000, 1758595089)));
    }

    @Test
    public void starMeansEveryAccount() {
        assertTrue(LogsMongo.parseAll("*"));
        assertTrue(LogsMongo.parseAll("1000000, *"));
    }

    /** Default env (nothing set): no account is enabled, including a missing account context. */
    @Test
    public void nothingEnabledByDefault() {
        assertFalse(LogsMongo.isAccountEnabled(1000000));
        assertFalse(LogsMongo.isAccountEnabled(null));
    }
}
