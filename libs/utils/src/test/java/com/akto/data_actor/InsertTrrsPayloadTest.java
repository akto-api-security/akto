package com.akto.data_actor;

import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
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
