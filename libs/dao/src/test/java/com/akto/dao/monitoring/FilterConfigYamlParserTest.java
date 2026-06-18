package com.akto.dao.monitoring;

import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.ExecutorConfigParserResult;
import org.junit.Test;

import static org.junit.Assert.*;

public class FilterConfigYamlParserTest {

    private static final String REGEX_REPLACE_YAML =
            "id: MERGE_ENV_API_HOSTS\n" +
            "filter:\n" +
            "  request_headers:\n" +
            "    for_one:\n" +
            "      key:\n" +
            "        eq: host\n" +
            "      value:\n" +
            "        regex: \"\\\\.(spi)?(dev|prod|prd)-api\\\\.tapestry\\\\.com$\"\n" +
            "execute:\n" +
            "  type: single\n" +
            "  requests:\n" +
            "    - req:\n" +
            "        - modify_header:\n" +
            "            host:\n" +
            "              regex_replace:\n" +
            "                regex: \"(dev|prod|prd)-api\\\\.\"\n" +
            "                replace_with: \"qa-api.\"\n";

    private static final String STATIC_HEADER_YAML =
            "id: STATIC_HOST_FILTER\n" +
            "filter:\n" +
            "  request_headers:\n" +
            "    for_one:\n" +
            "      key:\n" +
            "        eq: host\n" +
            "      value:\n" +
            "        regex: \"^old\\\\.example\\\\.com$\"\n" +
            "execute:\n" +
            "  type: single\n" +
            "  requests:\n" +
            "    - req:\n" +
            "        - modify_header:\n" +
            "            host: new.example.com\n";

    private static final String SUFFIX_REGEX_REPLACE_YAML =
            "id: MERGE_ENV_SUFFIX_HOSTS\n" +
            "filter:\n" +
            "  request_headers:\n" +
            "    for_one:\n" +
            "      key:\n" +
            "        eq: host\n" +
            "      value:\n" +
            "        regex: \"-(dev|prd|prod)([.-])\"\n" +
            "execute:\n" +
            "  type: single\n" +
            "  requests:\n" +
            "    - req:\n" +
            "        - modify_header:\n" +
            "            host:\n" +
            "              regex_replace:\n" +
            "                regex: \"-(dev|prd|prod)([.-])\"\n" +
            "                replace_with: \"-qa$2\"\n";

    @Test
    public void testRegexReplaceYamlParsesSuccessfully() throws Exception {
        FilterConfig config = FilterConfigYamlParser.parseTemplate(REGEX_REPLACE_YAML, true);

        assertNotNull("FilterConfig should not be null", config);
        assertEquals("MERGE_ENV_API_HOSTS", config.getId());
        assertNotNull("Filter should not be null", config.getFilter());

        ExecutorConfigParserResult executor = config.getExecutor();
        assertNotNull("Executor should not be null", executor);
        assertTrue("Executor should be valid", executor.getIsValid());
        assertNotNull("Executor node should not be null", executor.getNode());
    }

    @Test
    public void testStaticHeaderYamlStillParsesSuccessfully() throws Exception {
        FilterConfig config = FilterConfigYamlParser.parseTemplate(STATIC_HEADER_YAML, true);

        assertNotNull("FilterConfig should not be null", config);
        assertEquals("STATIC_HOST_FILTER", config.getId());

        ExecutorConfigParserResult executor = config.getExecutor();
        assertNotNull("Executor should not be null", executor);
        assertTrue("Executor should be valid", executor.getIsValid());
    }

    @Test
    public void testSuffixRegexReplaceYamlParsesSuccessfully() throws Exception {
        FilterConfig config = FilterConfigYamlParser.parseTemplate(SUFFIX_REGEX_REPLACE_YAML, true);

        assertNotNull("FilterConfig should not be null", config);
        assertEquals("MERGE_ENV_SUFFIX_HOSTS", config.getId());

        ExecutorConfigParserResult executor = config.getExecutor();
        assertNotNull("Executor should not be null", executor);
        assertTrue("Executor should be valid", executor.getIsValid());
        assertNotNull("Executor node should not be null", executor.getNode());
    }

    @Test
    public void testFilterWithoutExecutorParsesSuccessfully() throws Exception {
        String yaml =
                "id: SIMPLE_FILTER\n" +
                "filter:\n" +
                "  url:\n" +
                "    regex: \".*\"\n";

        FilterConfig config = FilterConfigYamlParser.parseTemplate(yaml, true);

        assertNotNull("FilterConfig should not be null", config);
        assertEquals("SIMPLE_FILTER", config.getId());
        assertNull("Executor should be null when no execute section", config.getExecutor());
    }
}
