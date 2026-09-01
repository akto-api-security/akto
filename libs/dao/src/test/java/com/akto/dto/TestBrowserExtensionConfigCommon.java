package com.akto.dto;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TestBrowserExtensionConfigCommon {

    @Test
    public void testHttpConfigWithMethodAndStringPath() {
        Document doc = Document.parse("{" +
                "'host': 'chatgpt.com'," +
                "'active': true," +
                "'paths': ['/backend-api/conversation', '/backend-api/f/conversation']," +
                "'transport': 'http'," +
                "'method': 'POST'," +
                "'format': 'json'," +
                "'path': 'messages[-1].content.parts'" +
                "}");

        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);

        assertNotNull(config);
        assertEquals("chatgpt.com", config.getHost());
        assertTrue(config.isActive());
        assertEquals(Arrays.asList("/backend-api/conversation", "/backend-api/f/conversation"), config.getPaths());
        assertEquals("http", config.getTransport());
        assertEquals("POST", config.getMethod());
        assertEquals("json", config.getFormat());
        assertEquals("messages[-1].content.parts", config.getPath());
    }

    @Test
    public void testGraphqlConfigWithOperations() {
        Document doc = Document.parse("{" +
                "'host': 'poe.com'," +
                "'active': true," +
                "'paths': ['/api/gql_POST']," +
                "'transport': 'graphql'," +
                "'operations': ['sendMessageMutation']," +
                "'format': 'json'," +
                "'path': 'variables.query'" +
                "}");

        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);

        assertNotNull(config);
        assertEquals("graphql", config.getTransport());
        assertEquals(Arrays.asList("sendMessageMutation"), config.getOperations());
        assertEquals("variables.query", config.getPath());
        assertNull(config.getMethod());
        assertNull(config.getFrameMatch());
    }

    @Test
    public void testWebsocketConfigWithFrameMatch() {
        Document doc = Document.parse("{" +
                "'host': 'copilot.microsoft.com'," +
                "'active': true," +
                "'paths': ['/c/api/chat']," +
                "'transport': 'websocket'," +
                "'frameMatch': { 'event': 'send' }," +
                "'format': 'ws-frame'," +
                "'path': 'content[?type=text][*].text'" +
                "}");

        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);

        assertNotNull(config);
        assertEquals("websocket", config.getTransport());
        assertEquals("ws-frame", config.getFormat());
        assertNotNull(config.getFrameMatch());
        assertEquals("send", config.getFrameMatch().get("event"));
    }

    @Test
    public void testPathAsListOfCandidates() {
        Document doc = Document.parse("{" +
                "'host': 'gemini.google.com'," +
                "'active': true," +
                "'paths': ['_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate']," +
                "'transport': 'http'," +
                "'method': 'POST'," +
                "'format': 'form'," +
                "'path': [\"['f.req'][0][0][2]\", \"['f.req'][1][0][0]\", \"['f.req'][1][0][0][2]\"]" +
                "}");

        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);

        assertNotNull(config);
        assertTrue(config.getPath() instanceof List);
        assertEquals(Arrays.asList("['f.req'][0][0][2]", "['f.req'][1][0][0]", "['f.req'][1][0][0][2]"),
                config.getPath());
    }

    @Test
    public void testHostAndPathsOnlyConfig() {
        Document doc = Document.parse("{" +
                "'host': 'grok.com'," +
                "'active': true," +
                "'paths': ['/rest/app-chat/conversations/']" +
                "}");

        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);

        assertNotNull(config);
        assertEquals("grok.com", config.getHost());
        assertEquals(Arrays.asList("/rest/app-chat/conversations/"), config.getPaths());
        assertNull(config.getTransport());
        assertNull(config.getMethod());
        assertNull(config.getFormat());
        assertNull(config.getPath());
        assertNull(config.getOperations());
    }

    @Test
    public void testActiveDefaultsToTrueWhenAbsent() {
        Document doc = Document.parse("{ 'host': 'github.com', 'paths': ['/github/chat/threads/*/messages'] }");

        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);

        assertNotNull(config);
        assertTrue(config.isActive());
    }

    @Test
    public void testInactiveConfigIsMappedAsInactive() {
        Document doc = Document.parse("{ 'host': 'you.com', 'active': false }");

        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);

        assertNotNull(config);
        assertFalse(config.isActive());
    }

    @Test
    public void testIdIsExposedAsHexString() {
        ObjectId id = new ObjectId();
        Document doc = new Document("_id", id).append("host", "duck.ai");

        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);

        assertNotNull(config);
        assertEquals(id.toHexString(), config.get_id());
    }

    @Test
    public void testDocumentWithoutHostIsSkipped() {
        assertNull(BrowserExtensionConfigCommon.fromDocument(new Document("paths", Arrays.asList("/api/chat"))));
        assertNull(BrowserExtensionConfigCommon.fromDocument(new Document("host", "   ")));
        assertNull(BrowserExtensionConfigCommon.fromDocument(null));
    }

    @Test
    public void testUnexpectedFieldTypesAreIgnoredInsteadOfFailing() {
        Document doc = new Document("host", "pi.ai")
                .append("paths", "/api/v2/chat")   // single string instead of a list
                .append("method", 42)              // wrong type
                .append("frameMatch", "send");     // wrong type

        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);

        assertNotNull(config);
        assertEquals(Arrays.asList("/api/v2/chat"), config.getPaths());
        assertNull(config.getMethod());
        assertNull(config.getFrameMatch());
    }

    @Test
    public void testFileUploadDescriptorsPreserved() {
        Document doc = Document.parse("{" +
                "'host': 'copilot.microsoft.com'," +
                "'active': true," +
                "'paths': ['/c/api/chat']," +
                "'transport': 'websocket'," +
                "'format': 'ws-frame'," +
                "'path': 'content[?type=text][*].text'," +
                "'fileUpload': [" +
                "  { 'path': '/c/api/attachments', 'method': 'POST', 'transport': 'http', 'encoding': 'multipart' }" +
                "]" +
                "}");

        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);

        assertNotNull(config);
        List<Object> fileUpload = config.getFileUpload();
        assertNotNull(fileUpload);
        assertEquals(1, fileUpload.size());

        // Descriptor is mirrored as stored (a nested Document), so every field survives the round trip.
        Document descriptor = (Document) fileUpload.get(0);
        assertEquals("/c/api/attachments", descriptor.getString("path"));
        assertEquals("POST", descriptor.getString("method"));
        assertEquals("http", descriptor.getString("transport"));
        assertEquals("multipart", descriptor.getString("encoding"));
    }

    @Test
    public void testFileUploadAbsentIsNull() {
        Document doc = Document.parse("{ 'host': 'chatgpt.com', 'active': true, 'paths': ['/x'] }");
        BrowserExtensionConfigCommon config = BrowserExtensionConfigCommon.fromDocument(doc);
        assertNotNull(config);
        assertNull(config.getFileUpload());
    }

    // ---- merge(common, account) ----

    private static BrowserExtensionConfigCommon cfg(String host, boolean active) {
        return BrowserExtensionConfigCommon.fromDocument(
                new Document("host", host).append("active", active));
    }

    private static List<String> hosts(List<BrowserExtensionConfigCommon> configs) {
        List<String> out = new java.util.ArrayList<>();
        for (BrowserExtensionConfigCommon c : configs) out.add(c.getHost());
        return out;
    }

    @Test
    public void testMergeCommonOnly() {
        List<BrowserExtensionConfigCommon> common = Arrays.asList(cfg("chatgpt.com", true), cfg("claude.ai", true));
        List<BrowserExtensionConfigCommon> merged = BrowserExtensionConfigCommon.merge(common, Arrays.asList());
        assertEquals(Arrays.asList("chatgpt.com", "claude.ai"), hosts(merged));
    }

    @Test
    public void testMergeOptOutDropsCommonHost() {
        List<BrowserExtensionConfigCommon> common = Arrays.asList(cfg("chatgpt.com", true), cfg("claude.ai", true));
        List<BrowserExtensionConfigCommon> account = Arrays.asList(cfg("chatgpt.com", false)); // opt-out
        List<BrowserExtensionConfigCommon> merged = BrowserExtensionConfigCommon.merge(common, account);
        assertEquals(Arrays.asList("claude.ai"), hosts(merged));
    }

    @Test
    public void testMergeAddsAccountCustomHost() {
        List<BrowserExtensionConfigCommon> common = Arrays.asList(cfg("chatgpt.com", true));
        List<BrowserExtensionConfigCommon> account = Arrays.asList(cfg("my-internal.ai", true)); // custom
        List<BrowserExtensionConfigCommon> merged = BrowserExtensionConfigCommon.merge(common, account);
        assertEquals(Arrays.asList("chatgpt.com", "my-internal.ai"), hosts(merged));
    }

    @Test
    public void testMergeSameHostActiveDoesNotDuplicateOrOverride() {
        BrowserExtensionConfigCommon commonEntry = cfg("chatgpt.com", true);
        List<BrowserExtensionConfigCommon> merged = BrowserExtensionConfigCommon.merge(
                Arrays.asList(commonEntry), Arrays.asList(cfg("chatgpt.com", true)));
        assertEquals(Arrays.asList("chatgpt.com"), hosts(merged));
        assertSame(commonEntry, merged.get(0)); // catalogue entry wins
    }

    @Test
    public void testMergeOptOutOfUnknownHostIsNoOp() {
        List<BrowserExtensionConfigCommon> common = Arrays.asList(cfg("chatgpt.com", true));
        List<BrowserExtensionConfigCommon> account = Arrays.asList(cfg("not-in-common.ai", false));
        List<BrowserExtensionConfigCommon> merged = BrowserExtensionConfigCommon.merge(common, account);
        assertEquals(Arrays.asList("chatgpt.com"), hosts(merged));
    }
}
