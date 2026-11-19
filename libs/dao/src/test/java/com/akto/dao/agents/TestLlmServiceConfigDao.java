package com.akto.dao.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import org.bson.Document;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dto.agents.LlmServiceConfig;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.akto.util.DbNames;

public class TestLlmServiceConfigDao extends MongoBasedTest {

    @Test
    public void livesInTheCommonDatabaseNotThePerAccountOne() {
        assertEquals(DbNames.COMMON, LlmServiceConfigDao.instance.getDBName());
        assertEquals("llm_service_config", LlmServiceConfigDao.instance.getCollName());
    }

    @Test
    public void readsADocSeededFromTheMongoShellWithNoDiscriminators() {
        LlmServiceConfigDao.instance.getMCollection().withDocumentClass(Document.class).insertOne(
            new Document("_id", "svc-shell").append("chain", Arrays.asList(
                new Document("name", "haiku").append("type", "ANTHROPIC")
                    .append("params", new Document("model", "claude-haiku-5-5").append("apiKey", "sk-ant-x")),
                new Document("name", "nano").append("type", "AZURE_OPENAI")
                    .append("params", new Document("tier", "GPT_5_NANO")))));

        LlmServiceConfig config = LlmServiceConfigDao.instance.findByService("svc-shell");

        assertEquals("svc-shell", config.getId());
        assertEquals(2, config.getChain().size());
        Model haiku = config.getChain().get(0);
        assertEquals(ModelType.ANTHROPIC, haiku.getType());
        assertEquals("claude-haiku-5-5", haiku.getModelName());
        assertEquals("sk-ant-x", haiku.readApiKey());
        Model nano = config.getChain().get(1);
        assertEquals(ModelType.AZURE_OPENAI, nano.getType());
        assertEquals("GPT_5_NANO", nano.getParams().get(Model.PARAM_TIER));
    }

    @Test
    public void roundTripsADocWrittenThroughTheDao() {
        HashMap<String, String> params = new HashMap<>();
        params.put(Model.PARAM_MODEL, "claude-haiku-5-5");
        params.put(Model.PARAM_API_KEY, "k");
        LlmServiceConfigDao.instance.insertOne(new LlmServiceConfig("svc-dao",
            Collections.singletonList(new Model("haiku", ModelType.ANTHROPIC, params))));

        LlmServiceConfig config = LlmServiceConfigDao.instance.findByService("svc-dao");

        assertEquals(1, config.getChain().size());
        assertEquals("k", config.getChain().get(0).readApiKey());
    }

    @Test
    public void unknownServiceIsNullNotAnError() {
        assertNull(LlmServiceConfigDao.instance.findByService("no-such-service"));
    }
}
