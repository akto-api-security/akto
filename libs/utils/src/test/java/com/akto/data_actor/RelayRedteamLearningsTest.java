package com.akto.data_actor;

import com.akto.dto.OriginalHttpRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(4, DataActor.REDTEAM_LEARNING_APIS.size());
        assertThrows(IllegalArgumentException.class, () -> ClientActor.buildRelayRedteamLearningsRequest("deleteAll", "{}"));
        assertThrows(IllegalArgumentException.class, () -> ClientActor.buildRelayRedteamLearningsRequest("../fetchAgentModels", "{}"));
    }

    @Test
    public void dbActorHasNoLocalStoreSoItReportsUnreachableNotEmpty() {
        assertNull(new DbActor().relayRedteamLearnings("fetchLatestRedteamProfile", "{}"));
    }
}
