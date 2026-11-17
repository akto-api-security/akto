package com.akto.utils;

import com.akto.dao.context.Context;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KafkaUtilsTopicTest {

    private static final String DEPLOYMENT_TOPIC = System.getenv("AKTO_KAFKA_TOPIC") != null
            && !System.getenv("AKTO_KAFKA_TOPIC").isEmpty() ? System.getenv("AKTO_KAFKA_TOPIC") : "akto.api.logs";

    @After
    public void clear() {
        Context.accountId.remove();
    }

    @Test
    public void mappedAccountGoesToItsOwnTopic() {
        Context.accountId.set(1758787662);
        assertEquals("akto.ai.traffic.1758787662-acorns", KafkaUtils.primaryTopic());
    }

    @Test
    public void otherAccountsUseTheDeploymentTopic() {
        Context.accountId.set(1718012345);
        assertEquals(DEPLOYMENT_TOPIC, KafkaUtils.primaryTopic());
    }

    @Test
    public void noAccountUsesTheDeploymentTopic() {
        assertEquals(DEPLOYMENT_TOPIC, KafkaUtils.primaryTopic());
    }
}
