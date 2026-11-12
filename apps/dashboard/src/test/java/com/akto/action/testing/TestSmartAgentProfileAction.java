package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.SmartAgentProfileDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dto.User;
import com.akto.dto.testing.CollectionWiseTestingEndpoints;
import com.akto.dto.testing.SmartAgentConfig;
import com.akto.dto.testing.SmartAgentKnowledgeFile;
import com.akto.dto.testing.SmartAgentProfile;
import com.akto.dto.testing.TestingRun;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class TestSmartAgentProfileAction extends MongoBasedTest {

    @Before
    public void clean() {
        SmartAgentProfileDao.instance.getMCollection().drop();
        TestingRunDao.instance.getMCollection().drop();
    }

    private static SmartAgentProfileAction action() {
        SmartAgentProfileAction action = new SmartAgentProfileAction();
        Map<String, Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("tester@akto.io");
        session.put("user", user);
        action.setSession(session);
        return action;
    }

    private static Map<String, Object> named(String name, String content) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("content", content);
        return item;
    }

    private static SmartAgentProfileAction valid(String name) {
        SmartAgentProfileAction action = action();
        action.setName(name);
        action.setSkills(Arrays.asList("object_ownership", "TENANT_ISOLATION", "OBJECT_OWNERSHIP"));
        action.setCustomSkills(Collections.singletonList(named("Refunds", "Try refunding an order twice.")));
        action.setInstructions("Order 7 is mine.");
        action.setKnowledgeFiles(Collections.singletonList(named("flows.md", "checkout then pay")));
        action.setKnowledgeLinks(Arrays.asList("https://docs.shop.example.com/openapi.json", "https://wiki.example.com/flows"));
        return action;
    }

    private static TestingRun run(String name) {
        return new TestingRun(Context.now(), "tester@akto.io", new CollectionWiseTestingEndpoints(1000), 0,
                TestingRun.State.SCHEDULED, 0, name, "", false);
    }

    @Test
    public void aSmartAgentIsSavedCleanedAndListed() {
        SmartAgentProfileAction save = valid("Shop agent");
        assertEquals("SUCCESS", save.saveSmartAgentProfile());
        SmartAgentProfile saved = save.getSmartAgentProfile();
        assertNotNull(saved.getHexId());
        assertEquals(Arrays.asList("OBJECT_OWNERSHIP", "TENANT_ISOLATION"), saved.getSkills());
        assertEquals("Refunds", saved.getCustomSkills().get(0).getName());
        assertEquals("flows.md", saved.getKnowledgeFiles().get(0).getName());
        assertEquals(2, saved.getKnowledgeLinks().size());
        assertEquals("tester@akto.io", saved.getCreatedBy());

        SmartAgentProfileAction list = action();
        assertEquals("SUCCESS", list.fetchSmartAgentProfiles());
        assertEquals(1, list.getSmartAgentProfiles().size());
    }

    @Test
    public void anUpdateKeepsTheIdAndNamesStayUnique() {
        SmartAgentProfileAction first = valid("Shop agent");
        first.saveSmartAgentProfile();
        String id = first.getSmartAgentProfile().getHexId();
        assertEquals("SUCCESS", valid("Other agent").saveSmartAgentProfile());

        SmartAgentProfileAction rename = valid("Other agent");
        rename.setHexId(id);
        assertEquals("ERROR", rename.saveSmartAgentProfile());
        assertTrue(rename.getActionErrors().iterator().next().contains("already exists"));

        SmartAgentProfileAction update = valid("Shop agent v2");
        update.setHexId(id);
        update.setSkills(Collections.singletonList("BUSINESS_LIMITS"));
        assertEquals("SUCCESS", update.saveSmartAgentProfile());
        SmartAgentProfile updated = SmartAgentProfileAction.findProfile(id);
        assertEquals("Shop agent v2", updated.getName());
        assertEquals(Collections.singletonList("BUSINESS_LIMITS"), updated.getSkills());
        assertEquals(2, SmartAgentProfileDao.instance.count(new BasicDBObject()));
    }

    @Test
    public void invalidInputIsRefusedWithAReadableReason() {
        String[][] cases = {
                {"name", "Name is required"},
                {"skill", "Unknown skill"},
                {"link", "Not a web link"},
                {"file", "is too long"},
                {"customSkill", "is too long"},
        };
        for (String[] c : cases) {
            SmartAgentProfileAction action = valid("Agent " + c[0]);
            switch (c[0]) {
                case "name": action.setName("  "); break;
                case "skill": action.setSkills(Collections.singletonList("bad skill!")); break;
                case "link": action.setKnowledgeLinks(Collections.singletonList("file:///etc/passwd")); break;
                case "file":
                    action.setKnowledgeFiles(Collections.singletonList(named("big.txt",
                            new String(new char[SmartAgentConfig.MAX_KNOWLEDGE_FILE_CHARS + 1]).replace('\0', 'x'))));
                    break;
                case "customSkill":
                    action.setCustomSkills(Collections.singletonList(named("Long",
                            new String(new char[SmartAgentConfig.MAX_CUSTOM_SKILL_CHARS + 1]).replace('\0', 'x'))));
                    break;
                default: break;
            }
            assertEquals(c[0], "ERROR", action.saveSmartAgentProfile());
            String error = action.getActionErrors().iterator().next();
            assertTrue(c[0] + ": " + error, error.contains(c[1]));
        }
        assertEquals(0, SmartAgentProfileDao.instance.count(new BasicDBObject()));
    }

    @Test
    public void aRunWithASmartAgentGetsACopyOfItAndSmartTestingOn() {
        SmartAgentProfileAction save = valid("Shop agent");
        save.saveSmartAgentProfile();
        String id = save.getSmartAgentProfile().getHexId();

        StartTestAction start = new StartTestAction();
        start.setSmartAgentProfileId(id);
        TestingRun run = run("smart agent run");
        assertTrue(start.applySmartAgentProfile(run));
        assertTrue(run.getRunAutomatedTests());
        SmartAgentConfig config = run.getSmartAgentConfig();
        assertEquals(id, config.getProfileId());
        assertEquals(Arrays.asList("OBJECT_OWNERSHIP", "TENANT_ISOLATION"), config.getSkills());
        assertEquals("Order 7 is mine.", config.getInstructions());

        // stored and read back with the run (what cyborg serves to mini-testing)
        TestingRunDao.instance.insertOne(run);
        TestingRun stored = TestingRunDao.instance.findOne(Filters.eq("_id", run.getId()));
        assertEquals("Shop agent", stored.getSmartAgentConfig().getProfileName());
        assertEquals("Try refunding an order twice.", stored.getSmartAgentConfig().getCustomSkills().get(0).getContent());
        assertEquals("checkout then pay", stored.getSmartAgentConfig().getKnowledgeFiles().get(0).getContent());

        // editing the smart agent later never changes the run
        SmartAgentProfileAction edit = valid("Shop agent renamed");
        edit.setHexId(id);
        edit.saveSmartAgentProfile();
        assertEquals("Shop agent", TestingRunDao.instance.findOne(Filters.eq("_id", run.getId())).getSmartAgentConfig().getProfileName());
    }

    @Test
    public void aRunWithoutASmartAgentIsUnchangedAndAnUnknownOneIsRefused() {
        TestingRun run = run("plain run");
        assertTrue(new StartTestAction().applySmartAgentProfile(run));
        assertNull(run.getSmartAgentConfig());
        assertFalse(run.getRunAutomatedTests());

        StartTestAction unknown = new StartTestAction();
        unknown.setSmartAgentProfileId(new ObjectId().toHexString());
        assertFalse(unknown.applySmartAgentProfile(run));
    }

    @Test
    public void deleteRemovesTheSmartAgent() {
        SmartAgentProfileAction save = valid("Shop agent");
        save.saveSmartAgentProfile();
        SmartAgentProfileAction delete = action();
        delete.setHexId(save.getSmartAgentProfile().getHexId());
        assertEquals("SUCCESS", delete.deleteSmartAgentProfile());
        assertEquals(0, SmartAgentProfileDao.instance.count(new BasicDBObject()));
        assertEquals("ERROR", delete.deleteSmartAgentProfile());
    }

    @Test
    public void knowledgeFilesGoToBlobStorageAndOnlyTheirReferenceIsKept() {
        Map<String, byte[]> blobs = new HashMap<>();
        int[] uploads = {0};
        Supplier<SmartAgentProfileAction.BlobStore> original = SmartAgentProfileAction.blobStore;
        SmartAgentProfileAction.blobStore = () -> new SmartAgentProfileAction.BlobStore() {
            public boolean exists(String blobName) { return blobs.containsKey(blobName); }
            public void upload(String blobName, byte[] data) { uploads[0]++; blobs.put(blobName, data); }
        };
        try {
            SmartAgentProfileAction save = valid("Blob agent");
            assertEquals("SUCCESS", save.saveSmartAgentProfile());
            SmartAgentProfile saved = save.getSmartAgentProfile();
            SmartAgentKnowledgeFile file = saved.getKnowledgeFiles().get(0);
            String sha = SmartAgentProfileAction.sha256Hex("checkout then pay".getBytes(StandardCharsets.UTF_8));
            assertNull(file.getContent());                       // not in Mongo
            assertEquals(sha, file.getSha256());
            assertEquals("checkout then pay".length(), file.getSize());
            String blobName = ACCOUNT_ID + "/" + sha.substring(0, 2) + "/" + sha;  // same layout as file inspection
            assertEquals("checkout then pay", new String(blobs.get(blobName), StandardCharsets.UTF_8));
            // the knowledge file and the custom skill file both go to blob storage
            assertEquals(2, uploads[0]);
            String skillSha = SmartAgentProfileAction.sha256Hex("Try refunding an order twice.".getBytes(StandardCharsets.UTF_8));
            assertNull(saved.getCustomSkills().get(0).getContent());
            assertEquals(skillSha, saved.getCustomSkills().get(0).getSha256());

            // the same content again is not uploaded twice; a kept file comes back as its reference only
            valid("Blob agent 2").saveSmartAgentProfile();
            assertEquals(2, uploads[0]);
            SmartAgentProfileAction keep = valid("Blob agent");
            keep.setHexId(saved.getHexId());
            Map<String, Object> kept = new HashMap<>();
            kept.put("name", "flows.md");
            kept.put("sha256", sha);
            kept.put("size", 17);
            keep.setKnowledgeFiles(Collections.singletonList(kept));
            assertEquals("SUCCESS", keep.saveSmartAgentProfile());
            assertEquals(sha, SmartAgentProfileAction.findProfile(saved.getHexId()).getKnowledgeFiles().get(0).getSha256());

            // the run gets the reference too, not the content
            StartTestAction start = new StartTestAction();
            start.setSmartAgentProfileId(saved.getHexId());
            TestingRun run = run("run");
            start.applySmartAgentProfile(run);
            assertNull(run.getSmartAgentConfig().getKnowledgeFiles().get(0).getContent());
            assertEquals(sha, run.getSmartAgentConfig().getKnowledgeFiles().get(0).getSha256());

            // a reference that is not a sha256 is refused
            SmartAgentProfileAction bad = valid("Bad ref");
            Map<String, Object> badRef = new HashMap<>();
            badRef.put("name", "x.md");
            badRef.put("sha256", "../../other-account/file");
            bad.setKnowledgeFiles(Collections.singletonList(badRef));
            assertEquals("ERROR", bad.saveSmartAgentProfile());
        } finally {
            SmartAgentProfileAction.blobStore = original;
        }
    }
}
