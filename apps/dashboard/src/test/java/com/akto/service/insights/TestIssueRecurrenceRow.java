package com.akto.service.insights;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * IssueRecurrenceRow is a plain immutable DTO (Lombok @Getter/@AllArgsConstructor, no
 * equals/hashCode) -- a construction/getter smoke test is all it needs.
 */
public class TestIssueRecurrenceRow {

    @Test
    public void testConstruction_allFieldsReturnedByGetters() {
        IssueRecurrenceRow row = new IssueRecurrenceRow(42, "/api/test", "GET", "SQLI", 3, 1000, 2000);

        assertEquals(42, row.getApiCollectionId());
        assertEquals("/api/test", row.getUrl());
        assertEquals("GET", row.getMethod());
        assertEquals("SQLI", row.getTestSubType());
        assertEquals(3, row.getDistinctRuns());
        assertEquals(1000, row.getFirstSeen());
        assertEquals(2000, row.getLastSeen());
    }
}
