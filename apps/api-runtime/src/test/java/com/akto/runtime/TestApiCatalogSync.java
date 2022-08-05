package com.akto.runtime;

import com.akto.dto.type.*;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class TestApiCatalogSync {

    @Test
    public void testFillUrlParams() {
        RequestTemplate requestTemplate1 = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder());
        validateSubTypeAndMinMax(requestTemplate1, "/api/books/378282246310005", "/api/books/STRING",
                SingleTypeInfo.CREDIT_CARD, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE, 1,1);
        validateSubTypeAndMinMax(requestTemplate1, "api/books/378282246310005", "/api/books/STRING",
                SingleTypeInfo.CREDIT_CARD, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE, 2, 1);
        validateSubTypeAndMinMax(requestTemplate1, "api/books/4111111111111111/", "/api/books/STRING",
                SingleTypeInfo.CREDIT_CARD, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE, 3, 2);

        RequestTemplate requestTemplate2 = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder());
        validateSubTypeAndMinMax(requestTemplate2, "/api/books/234", "/api/books/INTEGER",
                SingleTypeInfo.INTEGER_32, 234, 234, 1, 1);
        validateSubTypeAndMinMax(requestTemplate2, "api/books/999", "/api/books/INTEGER",
                SingleTypeInfo.INTEGER_32, 234,999, 2, 2);
    }

    private void validateSubTypeAndMinMax(RequestTemplate requestTemplate, String url, String templateUrl,
                                          SingleTypeInfo.SubType subType, long minValue, long maxValue, int count,
                                          int valuesCount) {

        String[] tokenizedUrl = APICatalogSync.tokenize(url);
        URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(templateUrl, URLMethods.Method.GET);

        requestTemplate.fillUrlParams(tokenizedUrl, urlTemplate, 0);

        assertEquals(1, requestTemplate.getUrlParams().size());
        KeyTypes keyTypes = requestTemplate.getUrlParams().get(2);
        SingleTypeInfo singleTypeInfo= keyTypes.getOccurrences().get(subType);
        assertEquals(subType, singleTypeInfo.getSubType());
        assertEquals(maxValue, singleTypeInfo.getMaxValue());
        assertEquals(minValue, singleTypeInfo.getMinValue());
        assertEquals(count, singleTypeInfo.getCount());
        assertEquals(valuesCount, singleTypeInfo.getValues().getElements().size());
    }

}
