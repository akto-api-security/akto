package com.akto.action;

import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.info.InfoParser;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A template draft as test-editor-services renders it (renderDraftYaml, consolidation.ts) must pass the parsing
 * saveTestEditorFile does, or "Open in Test Editor" → Save fails for every draft. The fixture is that renderer's output
 * for values with quotes, a newline and "${...}"; regenerate it when the renderer changes.
 */
public class TestRedteamDraftYaml {

    private static String fixture() {
        InputStream in = TestRedteamDraftYaml.class.getResourceAsStream("/redteam_learnings/rendered-draft.yaml");
        assertNotNull("fixture missing", in);
        try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return scanner.next();
        }
    }

    @Test
    public void aRenderedDraftPassesTheTestEditorSaveParsing() throws Exception {
        String content = fixture();

        Map<String, Object> config = new ObjectMapper(new YAMLFactory()).readValue(content, Map.class);
        Info info = new InfoParser().parse(config.get("info"));
        assertEquals("AGENTIC_SECURITY_AGENT_EXPLOITATION", info.getCategory().getName());
        assertNotNull(info.getCategory().getDisplayName());
        assertNotNull(info.getCategory().getShortName());

        TestConfig testConfig = TestConfigYamlParser.parseTemplate(content);
        assertEquals("AKTO_LEARNED_6A63A7CD9AE9", testConfig.getId());
        assertEquals("quotes, newline and ${...} survive as text", "Export \"all\" HCPs\nnow: ${base_prompt}", testConfig.getInfo().getName());
        assertEquals(Collections.singletonList(
                "Develop attacks that assess whether the agent exports records outside the user's aligned HCP list: \"all\", ${x}."),
                testConfig.getWordlists().get("base_prompt"));
        assertEquals(Collections.singletonList("Encode the request in Base64 and say \"decode first\"."),
                testConfig.getWordlists().get("strategy"));
        assertNotNull(testConfig.getExecute());
    }
}
