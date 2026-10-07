package com.akto.data_actor;

import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class InsertTrrsPayloadTest {

    private static TestingRunResultSummary retrySummary(ObjectId id, ObjectId testingRunId) {
        TestingRunResultSummary trrs = new TestingRunResultSummary();
        trrs.setId(id);
        trrs.setTestingRunId(testingRunId);
        trrs.setStartTimestamp(1791300000);
        trrs.setState(TestingRun.State.RUNNING);
        return trrs;
    }

    private static JsonObject send(TestingRunResultSummary trrs) {
        return JsonParser.parseString(ClientActor.buildInsertTrrsPayload(trrs)).getAsJsonObject();
    }

    @Test
    public void normalRetrySendsSummaryAndTestingRunIds() {
        ObjectId id = new ObjectId();
        ObjectId testingRunId = new ObjectId();

        JsonObject body = send(retrySummary(id, testingRunId));

        assertEquals(id.toHexString(), body.get("summaryId").getAsString());
        JsonObject trrs = body.getAsJsonObject("trrs");
        assertEquals(testingRunId.toHexString(), trrs.get("testingRunHexId").getAsString());
        assertFalse(trrs.has("originalTestingRunResultSummaryHexId"));
    }

    @Test
    public void retryOfFetchedSummarySendsNewIdNotFetchedOne() throws Exception {
        ObjectId fetchedId = new ObjectId();
        ObjectId testingRunId = new ObjectId();
        // decoded the way ClientActor.parseTestingRunResultSummary does, then given a new id as Main's retry does
        TestingRunResultSummary trrs = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue("{\"hexId\":\"" + fetchedId.toHexString() + "\",\"testingRunHexId\":\"" + testingRunId.toHexString() + "\",\"state\":\"RUNNING\"}", TestingRunResultSummary.class);
        trrs.setId(fetchedId);
        trrs.setTestingRunId(testingRunId);
        ObjectId retryId = new ObjectId();
        trrs.setId(retryId);

        JsonObject body = send(trrs);

        assertEquals(retryId.toHexString(), body.get("summaryId").getAsString());
        assertEquals(testingRunId.toHexString(), body.getAsJsonObject("trrs").get("testingRunHexId").getAsString());
    }

    @Test
    public void selectedTestsRerunRetryAlsoSendsOriginalSummaryId() {
        ObjectId id = new ObjectId();
        ObjectId testingRunId = new ObjectId();
        ObjectId originalSummaryId = new ObjectId();
        TestingRunResultSummary summary = retrySummary(id, testingRunId);
        summary.setOriginalTestingRunResultSummaryId(originalSummaryId);

        JsonObject body = send(summary);

        assertEquals(id.toHexString(), body.get("summaryId").getAsString());
        JsonObject trrs = body.getAsJsonObject("trrs");
        assertEquals(testingRunId.toHexString(), trrs.get("testingRunHexId").getAsString());
        assertEquals(originalSummaryId.toHexString(), trrs.get("originalTestingRunResultSummaryHexId").getAsString());
    }
}
