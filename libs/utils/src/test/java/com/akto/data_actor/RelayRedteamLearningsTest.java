package com.akto.data_actor;

import com.akto.dto.OriginalHttpRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelayRedteamLearningsTest {

    @Test
    public void eachApiHitsTheAbstractorEndpointOfTheSameNameWithTheBodyUntouched() {
        // A large collection id must keep its exact value; a parse into a Gson map would turn it into a double.
        String body = "{\"apiCollectionId\":1779231193,\"url\":\"https://a/chat\",\"method\":\"POST\"}";
        for (String api : DataActor.REDTEAM_LEARNING_APIS) {
            OriginalHttpRequest request = ClientActor.buildRelayRedteamLearningsRequest(api, body);
            assertTrue(request.getUrl().endsWith("/api/" + api), request.getUrl());
            assertEquals("POST", request.getMethod());
            assertEquals(body, request.getBody());
            assertTrue(request.getHeaders().containsKey(ClientActor.AUTHORIZATION));
        }
    }

    @Test
    public void onlyTheRedteamLearningApisCanBeRelayed() {
        assertEquals(5, DataActor.REDTEAM_LEARNING_APIS.size());
        assertThrows(IllegalArgumentException.class, () -> ClientActor.buildRelayRedteamLearningsRequest("deleteAll", "{}"));
        assertThrows(IllegalArgumentException.class, () -> ClientActor.buildRelayRedteamLearningsRequest("../fetchAgentModels", "{}"));
    }

    @Test
    public void dbActorHasNoLocalStoreSoItReportsUnreachableNotEmpty() {
        assertNull(new DbActor().relayRedteamLearnings("fetchLatestRedteamProfile", "{}"));
    }

    @Test
    public void onlyTransientFailuresAreRetried() {
        for (int status : new int[]{429, 500, 502, 503}) assertTrue(ClientActor.isRetryableRelayStatus(status), "status " + status);
        // A refusal (validation 422, auth 401/403) is answered the same way each time; it goes back to the caller as is.
        for (int status : new int[]{200, 202, 400, 401, 403, 404, 422}) assertFalse(ClientActor.isRetryableRelayStatus(status), "status " + status);
    }
}
