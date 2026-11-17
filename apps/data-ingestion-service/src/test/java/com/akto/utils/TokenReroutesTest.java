package com.akto.utils;

import com.akto.dto.IngestDataBatch;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class TokenReroutesTest {

    @After
    public void clear() {
        TokenReroutes.clearCurrent();
    }

    private static final String REROUTED_TOKEN = "a.b.c-sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw";

    @Test
    public void mappedSuffixResolvesToAccountAndTopic() {
        TokenReroutes.Route route = TokenReroutes.routeFor(REROUTED_TOKEN);
        assertEquals(1758787662, route.accountId);
        assertEquals("akto.ai.traffic.1758787662-acorns", route.kafkaTopic);
        assertEquals(1758787662, TokenReroutes.routeFor(REROUTED_TOKEN + " \n").accountId);
    }

    @Test
    public void otherTokensAreNotRerouted() {
        assertNull(TokenReroutes.routeFor("a.b.someOtherSignature"));
        assertNull(TokenReroutes.routeFor(null));
        assertNull(TokenReroutes.routeFor(""));
    }

    @Test
    public void reroutedRequestOverridesBodyAccount() {
        IngestDataBatch batch = new IngestDataBatch();
        batch.setAkto_account_id("1000000");

        TokenReroutes.setCurrent(TokenReroutes.routeFor(REROUTED_TOKEN));
        assertEquals("1758787662", KafkaUtils.buildMessageObject(batch).get("akto_account_id"));
        assertEquals("akto.ai.traffic.1758787662-acorns", KafkaUtils.primaryTopic());
    }

    @Test
    public void otherRequestsKeepBodyAccount() {
        IngestDataBatch batch = new IngestDataBatch();
        batch.setAkto_account_id("1000000");

        assertEquals("1000000", KafkaUtils.buildMessageObject(batch).get("akto_account_id"));
        assertNotEquals("akto.ai.traffic.1758787662-acorns", KafkaUtils.primaryTopic());
        TokenReroutes.setCurrent(TokenReroutes.routeFor(REROUTED_TOKEN));
        TokenReroutes.clearCurrent();
        assertEquals("1000000", KafkaUtils.buildMessageObject(batch).get("akto_account_id"));
    }
}
