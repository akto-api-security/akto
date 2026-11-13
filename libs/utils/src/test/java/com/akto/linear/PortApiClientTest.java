package com.akto.linear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akto.linear.LinearApiClient.LinearIssue;
import com.mongodb.BasicDBObject;
import org.junit.jupiter.api.Test;

public class PortApiClientTest {

    private static final String SUCCESS_RUN = "{\"nodeRuns\":["
        + "{\"output\":{\"title\":\"t\",\"teamId\":\"x\",\"priority\":0},\"node\":{\"identifier\":\"trigger\"}},"
        + "{\"output\":{\"response\":{\"status\":200,\"data\":{\"data\":{\"issueCreate\":{\"success\":true,"
        + "\"issue\":{\"id\":\"dc6163e4\",\"identifier\":\"AKT-8\",\"title\":\"t\","
        + "\"url\":\"https://linear.app/akto-testing/issue/AKT-8/t\"}}}}}},"
        + "\"node\":{\"identifier\":\"create_linear_issue\"}}]}";

    private static final String GRAPHQL_ERROR_RUN = "{\"nodeRuns\":["
        + "{\"output\":{\"title\":\"t\"},\"node\":{\"identifier\":\"trigger\"}},"
        + "{\"output\":{\"response\":{\"status\":200,\"data\":{\"errors\":["
        + "{\"message\":\"Authentication required\"}]}}},\"node\":{\"identifier\":\"create_linear_issue\"}}]}";

    @Test
    public void extractsIssueFromWebhookNodeOutput() throws Exception {
        LinearIssue issue = PortApiClient.extractIssue(BasicDBObject.parse(SUCCESS_RUN));

        assertEquals("dc6163e4", issue.getId());
        assertEquals("AKT-8", issue.getIdentifier());
        assertEquals("https://linear.app/akto-testing/issue/AKT-8/t", issue.getUrl());
    }

    @Test
    public void reportsLinearErrorWhenNoIssueIsReturned() {
        Exception e = assertThrows(Exception.class,
            () -> PortApiClient.extractIssue(BasicDBObject.parse(GRAPHQL_ERROR_RUN)));
        assertTrue(e.getMessage().contains("Authentication required"));
    }

    @Test
    public void failureMessageFallsBackWhenNodesHaveNoErrors() {
        assertEquals("no error details from Port.",
            PortApiClient.failureMessage(BasicDBObject.parse("{\"nodeRuns\":[]}")));
    }
}
