package com.akto.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.Test;

import com.akto.dao.context.Context;

public class TestDateUtils {

    @Test
    public void testPrettifyDelta() {
        String result = DateUtils.prettifyDelta(Context.now() - 20);
        assertEquals("1 minute ago", result);

        result = DateUtils.prettifyDelta(Context.now() - 200);
        assertEquals("3 minutes ago", result);

        result = DateUtils.prettifyDelta(Context.now() - 3000);
        assertEquals("50 minutes ago", result);

        result = DateUtils.prettifyDelta(Context.now()- 3700);
        assertEquals("1 hour ago", result);

        result = DateUtils.prettifyDelta(Context.now()-7400);
        assertEquals("2 hours ago", result);

        result = DateUtils.prettifyDelta(Context.now()-94000);
        assertEquals("1 day ago", result);
    }
    
}
