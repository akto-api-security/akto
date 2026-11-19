package com.akto.service.scripts.validation;

import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dto.test_editor.TestConfig;

/**
 * The test template a validation runs: the captured request, sent unchanged (an empty modification
 * list), so only the selected pre/post scripts change it. Validate accepts any response; a validation
 * never produces a vulnerability, and its result is never written as a test result or issue.
 */
public final class ScriptValidationReplayTemplate {

    public static final String ID = "AKTO_SCRIPT_VALIDATION_REPLAY";

    public static final String YAML =
        "id: " + ID + "\n"
        + "info:\n"
        + "  name: \"Script validation replay\"\n"
        + "  description: \"Replays a captured request unchanged so its pre/post-request scripts can be checked.\"\n"
        + "  details: \"Replays a captured request unchanged.\"\n"
        + "  impact: \"None\"\n"
        + "  category:\n"
        + "    name: SCRIPT_VALIDATION\n"
        + "    shortName: Script validation\n"
        + "    displayName: Script validation\n"
        + "  subCategory: " + ID + "\n"
        + "  severity: LOW\n"
        // required by the engine; matches every captured response
        + "api_selection_filters:\n"
        + "  response_code:\n"
        + "    gte: 0\n"
        + "execute:\n"
        + "  type: single\n"
        + "  requests:\n"
        + "    - req: []\n"
        + "validate:\n"
        + "  response_code:\n"
        + "    gte: 0\n";

    private ScriptValidationReplayTemplate() {}

    public static TestConfig parse() throws Exception {
        return TestConfigYamlParser.parseTemplate(YAML);
    }
}
