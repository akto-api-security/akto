package com.akto.dto.runtime_filters;

import com.akto.dto.HttpResponseParams;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TestResponseCodeRuntimeFilter {

    @Test
    public void happy() {
        ResponseCodeRuntimeFilter responseCodeRuntimeFilter = new ResponseCodeRuntimeFilter(200,299);

        HttpResponseParams httpResponseParams1 = new HttpResponseParams();
        httpResponseParams1.statusCode = 233;
        boolean result1 = responseCodeRuntimeFilter.process(httpResponseParams1);
        assertTrue(result1);

        HttpResponseParams httpResponseParams2 = new HttpResponseParams();
        httpResponseParams2.statusCode = 200;
        boolean result2 = responseCodeRuntimeFilter.process(httpResponseParams2);
        assertTrue(result2);

        HttpResponseParams httpResponseParams3 = new HttpResponseParams();
        httpResponseParams3.statusCode = 299;
        boolean result3 = responseCodeRuntimeFilter.process(httpResponseParams3);
        assertTrue(result3);

        HttpResponseParams httpResponseParams4 = new HttpResponseParams();
        httpResponseParams4.statusCode = 333;
        boolean result4 = responseCodeRuntimeFilter.process(httpResponseParams4);
        assertFalse(result4);

    }
}
