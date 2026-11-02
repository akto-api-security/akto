package com.akto.dao.agents;

import com.akto.dto.agents.LlmServiceConfig;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestLlmServiceConfigDao extends MongoBasedTest {

    @Test
    public void testFindByServiceRoundTrip() {
        LlmServiceConfigDao.instance.getMCollection().drop();

        Model anthropicEntry = new Model("anthropic-primary", ModelType.ANTHROPIC,
                Collections.singletonMap(Model.PARAM_MODEL, "claude-haiku-5-5"));
        LlmServiceConfig config = new LlmServiceConfig("skill_validation", Collections.singletonList(anthropicEntry));
        LlmServiceConfigDao.instance.insertOne(config);

        LlmServiceConfig found = LlmServiceConfigDao.instance.findByService("skill_validation");
        assertEquals("skill_validation", found.getId());
        assertEquals(1, found.getChain().size());
        assertEquals(ModelType.ANTHROPIC, found.getChain().get(0).getType());
    }

    @Test
    public void testFindByServiceMissing() {
        LlmServiceConfigDao.instance.getMCollection().drop();
        assertNull(LlmServiceConfigDao.instance.findByService("does_not_exist"));
    }
}
