package com.akto.trafficFilter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


public class ParamFilterTest {
    
 @Test
    public void testFilterEntry() {

        int accountId = 1_000_000;
        int apiCollectionId = 123;
        String url = "/testing";
        String method = "GET";
        String param = "host";

        boolean firstTime = ParamFilter.isNewEntry(accountId, apiCollectionId, url, method, param);
        assertTrue(firstTime);
        boolean secondTime = ParamFilter.isNewEntry(accountId, apiCollectionId, url, method, param);
        assertFalse(secondTime);
    }

}
