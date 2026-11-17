package com.akto.utils;

import com.akto.dao.context.Context;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class KafkaUtilsTopicTest {

    @After
    public void clear() {
        Context.accountId.remove();
    }

    @Test
    public void reroutedAccountGoesToItsOwnTopic() {
        Context.accountId.set(1758787662);
        assertEquals("akto.ai.traffic.1758787662-acorns", KafkaUtils.primaryTopic());
    }

    @Test
    public void otherRequestsUseTheDeploymentTopic() {
        assertNotEquals("akto.ai.traffic.1758787662-acorns", KafkaUtils.primaryTopic());
        Context.accountId.set(1718012345);
        assertNotEquals("akto.ai.traffic.1758787662-acorns", KafkaUtils.primaryTopic());
    }
}
