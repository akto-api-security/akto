package com.akto.testing;

import com.akto.runtime.URLAggregator;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ApiWorkflowExecutorTest {

    @Test
    public void testCombineQueryParams() {
        String query1 = "user=avneesh&age=99&favColour=blue&status=all_is_well";
        String query2 = "status=blah%20blah&age=101";
        String combinedQuery = new ApiWorkflowExecutor().combineQueryParams(query1, query2);
        assertTrue(combinedQuery.contains("status=blah%20blah"));

        BasicDBObject combinedQueryObject = URLAggregator.getQueryJSON("google.com?"+combinedQuery);

        assertEquals("avneesh", combinedQueryObject.get("user"));
        assertEquals("101", combinedQueryObject.get("age"));
        assertEquals("blue", combinedQueryObject.get("favColour"));
        assertEquals("blah blah", combinedQueryObject.get("status"));
    }
}
