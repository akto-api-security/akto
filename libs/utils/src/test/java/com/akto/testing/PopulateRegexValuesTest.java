package com.akto.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class PopulateRegexValuesTest {

    private static final String TOKEN = "dummy-csrf-token_abc123";

    private static final String HTML = "<html><body><form>\n" +
            "<input id=\"requestVerificationToken\"\n" +
            "       name=\"requestVerificationToken\"\n" +
            "       type=\"hidden\"\n" +
            "       value=\"" + TOKEN + "\" />\n" +
            "</form></body></html>";

    @Test
    public void extractsFirstGroupFromHtml() {
        Map<String, Object> valuesMap = new HashMap<>();
        Utils.populateRegexValues(valuesMap, HTML, "x1", "name=\"requestVerificationToken\"[^>]*value=\"([^\"]+)\"");

        assertEquals(TOKEN, valuesMap.get("x1.response.regex"));
        assertEquals(TOKEN, valuesMap.get("x1.response.regex.1"));
    }

    @Test
    public void extractedValueIsUsableInLaterSteps() throws Exception {
        Map<String, Object> valuesMap = new HashMap<>();
        Utils.populateRegexValues(valuesMap, HTML, "x1", "value=\"([^\"]+)\"");

        String body = Utils.replaceVariables("{\"token\": \"${x1.response.regex}\"}", valuesMap, true, true);
        assertEquals("{\"token\": \"" + TOKEN + "\"}", body);
    }

    @Test
    public void storesEveryGroupAndWholeMatchWhenNoGroups() {
        Map<String, Object> valuesMap = new HashMap<>();
        Utils.populateRegexValues(valuesMap, HTML, "x3", "id=\"(\\w+)\"\\s+name=\"(\\w+)\"");
        assertEquals("requestVerificationToken", valuesMap.get("x3.response.regex"));
        assertEquals("requestVerificationToken", valuesMap.get("x3.response.regex.2"));

        valuesMap.clear();
        Utils.populateRegexValues(valuesMap, HTML, "x1", "dummy-\\w+");
        assertEquals("dummy-csrf", valuesMap.get("x1.response.regex"));
    }

    @Test
    public void ignoresNoMatchBlankAndInvalidRegex() {
        Map<String, Object> valuesMap = new HashMap<>();
        Utils.populateRegexValues(valuesMap, HTML, "x1", "nonexistent=\"(.*)\"");
        Utils.populateRegexValues(valuesMap, HTML, "x1", "");
        Utils.populateRegexValues(valuesMap, HTML, "x1", null);
        Utils.populateRegexValues(valuesMap, null, "x1", "(.*)");
        assertTrue(valuesMap.isEmpty());

        Utils.populateRegexValues(valuesMap, HTML, "x1", "([unclosed");
        assertFalse(valuesMap.containsKey("x1.response.regex"));
    }

    @Test
    public void largeBodyMatches() {
        StringBuilder page = new StringBuilder();
        while (page.length() < 4_000_000) page.append("<div class=\"row\">lorem 1234</div>\n");
        page.append(HTML);
        Map<String, Object> valuesMap = new HashMap<>();
        Utils.populateRegexValues(valuesMap, page.toString(), "x1", "name=\"requestVerificationToken\"[^>]*value=\"([^\"]+)\"");
        assertEquals(TOKEN, valuesMap.get("x1.response.regex"));
    }
}
