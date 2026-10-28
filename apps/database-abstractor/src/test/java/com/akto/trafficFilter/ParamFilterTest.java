package com.akto.trafficFilter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ParamFilterTest {

    private static final int ACCOUNT_ID = 1_000_000;
    private static final String URL = "/testing";
    private static final String METHOD = "GET";

    @Test
    public void testStiParamIsNewThenCached() {
        int apiCollectionId = 1;
        assertTrue(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD, "host"));
        assertFalse(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD, "host"));
    }

    @Test
    public void testSampleDataBloomParamIsNewThenCached() {
        int apiCollectionId = 2;
        assertTrue(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD,
                ParamFilter.BLOOM_PARAM_SAMPLE_DATA));
        assertFalse(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD,
                ParamFilter.BLOOM_PARAM_SAMPLE_DATA));
    }

    @Test
    public void testSensitiveSampleDataBloomParamIsNewThenCached() {
        int apiCollectionId = 3;
        assertTrue(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD,
                ParamFilter.BLOOM_PARAM_SENSITIVE_SAMPLE_DATA));
        assertFalse(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD,
                ParamFilter.BLOOM_PARAM_SENSITIVE_SAMPLE_DATA));
    }

    @Test
    public void testSampleDataBloomDoesNotCollideWithStiParam() {
        int apiCollectionId = 4;
        assertTrue(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD, "email"));
        assertTrue(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD,
                ParamFilter.BLOOM_PARAM_SAMPLE_DATA));
        assertFalse(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD, "email"));
        assertFalse(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD,
                ParamFilter.BLOOM_PARAM_SAMPLE_DATA));
    }

    @Test
    public void testSampleAndSensitiveSampleBloomKeysAreIndependent() {
        int apiCollectionId = 5;
        assertTrue(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD,
                ParamFilter.BLOOM_PARAM_SAMPLE_DATA));
        assertTrue(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD,
                ParamFilter.BLOOM_PARAM_SENSITIVE_SAMPLE_DATA));
        assertFalse(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD,
                ParamFilter.BLOOM_PARAM_SAMPLE_DATA));
        assertFalse(ParamFilter.isNewEntry(ACCOUNT_ID, apiCollectionId, URL, METHOD,
                ParamFilter.BLOOM_PARAM_SENSITIVE_SAMPLE_DATA));
    }
}
