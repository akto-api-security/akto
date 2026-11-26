package com.akto.utils;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.data_actor.DbLayer;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModuleInfoBulkHeartbeatTest extends MongoBasedTest {

    private static final String POD = "akto-tc:akto-k8s-rkpr4:node-1";
    private static final String MINI_RUNTIME = "mr-1";

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.tokenId.remove();
        ModuleInfoDao.instance.getMCollection().deleteMany(new BasicDBObject());
    }

    // What the mini-runtime builds from a collector heartbeat: _id is the collector's per-process UUID.
    private ModuleInfo collector(String processId, String podName, int ts, String envValue) {
        ModuleInfo m = new ModuleInfo();
        m.setId(processId);
        m.setName(podName);
        m.setModuleType(ModuleType.TRAFFIC_COLLECTOR);
        m.setCurrentVersion("k8s-ebpf");
        m.setStartedTs(ts);
        m.setLastHeartbeatReceived(ts);
        m.setMiniRuntimeName(MINI_RUNTIME);
        Map<String, Object> env = new HashMap<>();
        env.put("AKTO_X", envValue);
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("env", env);
        m.setAdditionalData(additionalData);
        return m;
    }

    private void heartbeat(ModuleInfo... modules) {
        DbLayer.bulkUpdateModuleInfo(Arrays.asList(modules));
    }

    private Document onlyDoc(String podName) {
        List<Document> docs = ModuleInfoDao.instance.getMCollection().withDocumentClass(Document.class)
                .find(Filters.eq(ModuleInfo.NAME, podName)).into(new ArrayList<>());
        assertEquals(1, docs.size());
        return docs.get(0);
    }

    @SuppressWarnings("unchecked")
    private static String env(Document doc) {
        return ((Map<String, Object>) ((Map<String, Object>) doc.get(ModuleInfo.ADDITIONAL_DATA)).get("env")).get("AKTO_X").toString();
    }

    // The dashboard's updateModuleEnvAndReboot: new env into additionalData.env, reboot flag on.
    private void dashboardSetsEnvAndReboot(String moduleId, String value) {
        ModuleInfoDao.instance.updateMany(Filters.eq(ModuleInfoDao.ID, moduleId),
                Updates.combine(Updates.set(ModuleInfo.ADDITIONAL_DATA + ".env.AKTO_X", value), Updates.set(ModuleInfo._REBOOT, true)));
    }

    @Test
    public void collectorRestartsKeepOneDocumentPerPod() {
        heartbeat(collector("uuid-1", POD, 1000, "a"));
        heartbeat(collector("uuid-1", POD, 1060, "a"));
        heartbeat(collector("uuid-2", POD, 1300, "a"));
        heartbeat(collector("uuid-3", POD, 1660, "a"));

        assertEquals(1, ModuleInfoDao.instance.getMCollection().countDocuments());
        Document doc = onlyDoc(POD);
        assertEquals("uuid-1", doc.getString("_id"));   // the id the dashboard targets stays the same
        assertEquals(1660, (int) doc.getInteger(ModuleInfo.LAST_HEARTBEAT_RECEIVED));
        assertFalse(doc.getBoolean(ModuleInfo._REBOOT));
        assertEquals(ModuleType.TRAFFIC_COLLECTOR, ModuleInfoDao.instance.findOne(Filters.eq(ModuleInfoDao.ID, "uuid-1")).getModuleType());
    }

    @Test
    public void pendingEnvUpdateSurvivesHeartbeatsUntilTheRebootPollReadsIt() {
        heartbeat(collector("uuid-1", POD, 1000, "old"));
        dashboardSetsEnvAndReboot("uuid-1", "new");

        // heartbeats from the collector still running with the old env, including after a restart
        heartbeat(collector("uuid-1", POD, 1060, "old"));
        heartbeat(collector("uuid-2", POD, 1300, "old"));
        assertEquals("new", env(onlyDoc(POD)));
        assertTrue(onlyDoc(POD).getBoolean(ModuleInfo._REBOOT));

        // the mini-runtime's ConfigUpdatePoller
        List<ModuleInfo> toReboot = DbLayer.fetchAndUpdateModuleForReboot(ModuleType.TRAFFIC_COLLECTOR, MINI_RUNTIME);
        assertEquals(1, toReboot.size());
        assertEquals(POD, toReboot.get(0).getName());
        assertEquals("new", ((Map<?, ?>) toReboot.get(0).getAdditionalData().get("env")).get("AKTO_X"));

        // the collector restarts with the new env and reports it
        heartbeat(collector("uuid-3", POD, 1400, "new"));
        Document doc = onlyDoc(POD);
        assertFalse(doc.getBoolean(ModuleInfo._REBOOT));
        assertEquals("new", env(doc));
        assertEquals(1400, (int) doc.getInteger(ModuleInfo.LAST_HEARTBEAT_RECEIVED));
    }

    @Test
    public void pendingRebootDoesNotDropTheRestOfTheBatch() {
        heartbeat(collector("a-1", "akto-tc:pod-a:n", 1000, "x"), collector("b-1", "akto-tc:pod-b:n", 1000, "x"));
        dashboardSetsEnvAndReboot("a-1", "y");

        heartbeat(collector("a-1", "akto-tc:pod-a:n", 1060, "x"), collector("b-1", "akto-tc:pod-b:n", 1060, "x"));

        assertEquals("y", env(onlyDoc("akto-tc:pod-a:n")));
        assertEquals(1060, (int) onlyDoc("akto-tc:pod-b:n").getInteger(ModuleInfo.LAST_HEARTBEAT_RECEIVED));
    }

    @Test
    public void documentsWrittenPerRestartBeforeThisChangeAreNotMultiplied() {
        for (int i = 0; i < 3; i++) {
            ModuleInfoDao.instance.getMCollection().withDocumentClass(Document.class).insertOne(new Document("_id", "legacy-" + i)
                    .append("_t", ModuleInfo.class.getName())
                    .append(ModuleInfo.MODULE_TYPE, "TRAFFIC_COLLECTOR")
                    .append(ModuleInfo.NAME, POD)
                    .append(ModuleInfo.LAST_HEARTBEAT_RECEIVED, 40 + i)
                    .append(ModuleInfo._REBOOT, false));
        }

        heartbeat(collector("uuid-new", POD, 1000, "a"));
        heartbeat(collector("uuid-new", POD, 1060, "a"));

        assertEquals(3, ModuleInfoDao.instance.getMCollection().countDocuments());
        assertEquals(1, ModuleInfoDao.instance.getMCollection().countDocuments(Filters.eq(ModuleInfo.LAST_HEARTBEAT_RECEIVED, 1060)));
    }
}
