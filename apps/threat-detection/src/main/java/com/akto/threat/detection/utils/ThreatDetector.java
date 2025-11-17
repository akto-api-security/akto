package com.akto.threat.detection.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ahocorasick.trie.Trie;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApi;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.client9.libinjection.SQLParse;

public class ThreatDetector {

    private static final String LFI_OS_FILES_DATA = "/lfi-os-files.data";
    private static final String OS_COMMAND_INJECTION_DATA = "/os-command-injection.data";
    private static final String SSRF_DATA = "/ssrf.data";
    public static final String LFI_FILTER_ID = "LocalFileInclusionLFIRFI";
    public static final String SQL_INJECTION_FILTER_ID = "SQLInjection";
    public static final String OS_COMMAND_INJECTION_FILTER_ID = "OSCommandInjection";
    public static final String SSRF_FILTER_ID = "SSRF";
    private static Map<String, Object> varMap = new HashMap<>();
    private Trie lfiTrie;
    private Trie osCommandInjectionTrie;
    private Trie ssrfTrie;
    private static final LoggerMaker logger = new LoggerMaker(ThreatDetector.class, LogDb.THREAT_DETECTION);

    public ThreatDetector() throws Exception {
        lfiTrie = generateTrie(LFI_OS_FILES_DATA);
        osCommandInjectionTrie = generateTrie(OS_COMMAND_INJECTION_DATA);
        ssrfTrie = generateTrie(SSRF_DATA);
    }

    private Trie generateTrie(String fileName) throws Exception {
        Trie.TrieBuilder builder = Trie.builder();
        try (InputStream is = ThreatDetector.class.getResourceAsStream(fileName);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                builder.addKeyword(line);
            }
        }

        return builder.build();
    }

    public boolean applyFilter(FilterConfig threatFilter, HttpResponseParams httpResponseParams, RawApi rawApi,
            ApiInfoKey apiInfoKey) {
        try {
            if (threatFilter.getId().equals(LFI_FILTER_ID)) {
                return isLFiThreat(httpResponseParams);
            }
            // if (threatFilter.getId().equals(SQL_INJECTION_FILTER_ID)) {
            //     return isSqliThreat(httpResponseParams);
            // }
            if (threatFilter.getId().equals(OS_COMMAND_INJECTION_FILTER_ID)) {
                return isOsCommandInjectionThreat(httpResponseParams); 
            }
            if (threatFilter.getId().equals(SSRF_FILTER_ID)) {
                return isSSRFThreat(httpResponseParams); 
            }
            return validateFilterForRequest(threatFilter, rawApi, apiInfoKey);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error in applyFilter " + e.getMessage());
            return false;
        }

    }

    public boolean isSuccessfulExploit(List<FilterConfig> successfulExploitFilters,
            RawApi rawApi, ApiInfoKey apiInfoKey) {
        for (FilterConfig filter : successfulExploitFilters) {
            if (validateFilterForRequest(filter, rawApi, apiInfoKey)) {
                logger.debug("Exploit successful for ApiInfo {}, filterId {}", apiInfoKey.toString(), filter.getId());
                return true;
            }
        }
        return false;
    }

    public boolean isIgnoredEvent(List<FilterConfig> ignoredEventFilters,
            RawApi rawApi, ApiInfoKey apiInfoKey) {
        for (FilterConfig filter : ignoredEventFilters) {
            if (validateFilterForRequest(filter, rawApi, apiInfoKey)) {
                logger.debug("Event should be ignored for ApiInfo {}, filterId {}", apiInfoKey.toString(), filter.getId());
                return true;
            }
        }
        return false;
    }


    public boolean shouldIgnoreApi(FilterConfig apiFilter, RawApi rawApi, ApiInfoKey apiInfoKey) {
        if (apiFilter.getIgnore() == null) {
            return false; // No ignore section, don't ignore
        }
        
        try {
            // Create a temporary FilterConfig with just the ignore condition as the filter
            FilterConfig tempFilter = new FilterConfig();
            tempFilter.setId(apiFilter.getId() + "_ignore");
            tempFilter.setFilter(apiFilter.getIgnore());
            tempFilter.setWordLists(apiFilter.getWordLists());
            
            // If the ignore condition matches, we should ignore this API
            boolean matchesIgnore = validateFilterForRequest(tempFilter, rawApi, apiInfoKey);
            if (matchesIgnore) {
                logger.debug("API matches ignore condition for filterId {}, apiInfoKey {}", 
                    apiFilter.getId(), apiInfoKey.toString());
            }
            return matchesIgnore;
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error checking ignore condition for filterId " + apiFilter.getId());
            return false; // On error, don't ignore
        }
    }

    

    private boolean validateFilterForRequest(
            FilterConfig apiFilter, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {
        try {
            varMap.clear();
            String filterExecutionLogId = "";
            ValidationResult res = TestPlugin.validateFilter(
                    apiFilter.getFilter().getNode(), rawApi, apiInfoKey, varMap, filterExecutionLogId);

            return res.getIsValid();
        } catch (Exception e) {
            logger.errorAndAddToDb("Error in validateFilterForRequest " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public boolean isSqliThreat(HttpResponseParams httpResponseParams) {

        if (SQLParse.isSQLi(httpResponseParams.getRequestParams().getURL())) {
            return true;
        }

        if (SQLParse.isSQLi(httpResponseParams.getRequestParams().getHeaders().toString())) {
            return true;
        }

        return SQLParse.isSQLi(httpResponseParams.getRequestParams().getPayload());
    }

    public boolean isLFiThreat(HttpResponseParams httpResponseParams) {
        if (lfiTrie.containsMatch(httpResponseParams.getRequestParams().getURL())) {
            return true;
        }

        if (lfiTrie.containsMatch(httpResponseParams.getRequestParams().getHeaders().toString())) {
            return true;
        }

        return lfiTrie.containsMatch(httpResponseParams.getRequestParams().getPayload());
    }

    public boolean isOsCommandInjectionThreat(HttpResponseParams httpResponseParams) {
        if (osCommandInjectionTrie.containsMatch(httpResponseParams.getRequestParams().getURL())) {
            return true;
        }

        if (osCommandInjectionTrie.containsMatch(httpResponseParams.getRequestParams().getHeaders().toString())) {
            return true;
        }

        return osCommandInjectionTrie.containsMatch(httpResponseParams.getRequestParams().getPayload());
    }

    public boolean isSSRFThreat(HttpResponseParams httpResponseParams) {
        if (ssrfTrie.containsMatch(httpResponseParams.getRequestParams().getURL())) {
            return true;
        }

        if (ssrfTrie.containsMatch(httpResponseParams.getRequestParams().getHeaders().toString())) {
            return true;
        }

        return ssrfTrie.containsMatch(httpResponseParams.getRequestParams().getPayload());
    }

    /**
     * Get exact character positions where threats were detected
     * Returns a list of SchemaConformanceError with location, positions, and matched keyword
     */
    public List<com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError> getThreatPositions(String filterId, HttpResponseParams httpResponseParams) {
        List<com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError> errors = new ArrayList<>();
        
        if (httpResponseParams == null || httpResponseParams.getRequestParams() == null) {
            return errors;
        }

        Trie trieToUse = null;
        if (LFI_FILTER_ID.equals(filterId)) {
            trieToUse = lfiTrie;
        } else if (OS_COMMAND_INJECTION_FILTER_ID.equals(filterId)) {
            trieToUse = osCommandInjectionTrie;
        } else if (SSRF_FILTER_ID.equals(filterId)) {
            trieToUse = ssrfTrie;
        }

        if (trieToUse == null) {
            return errors;
        }

        addThreatMatches(errors, trieToUse, httpResponseParams.getRequestParams().getURL(),
                "url",
                com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError.Location.LOCATION_URL,
                filterId);

        addThreatMatches(errors, trieToUse, String.valueOf(httpResponseParams.getRequestParams().getHeaders()),
                "headers",
                com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError.Location.LOCATION_HEADER,
                filterId);

        addThreatMatches(errors, trieToUse, httpResponseParams.getRequestParams().getPayload(),
                "payload",
                com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError.Location.LOCATION_BODY,
                filterId);

        return errors;
    }

    private void addThreatMatches(List<com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError> results,
            Trie trie,
            String text,
            String instancePath,
            com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError.Location location,
            String filterId) {
        if (trie == null || text == null) {
            return;
        }

        // Stop after first detection per location to avoid duplicate reports
        for (org.ahocorasick.trie.Emit emit : trie.parseText(text)) {
            if (emit == null || emit.getKeyword() == null) {
                continue;
            }

            com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError error =
                com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError.newBuilder()
                    .setMessage(String.format("%s [chars %d-%d]", emit.getKeyword(), emit.getStart(), emit.getEnd() + 1))
                    .setSchemaPath(filterId)
                    .setInstancePath(instancePath)
                    .setAttribute("threat_detected")
                    .setLocation(location)
                    .setStart(emit.getStart())
                    .setEnd(emit.getEnd() + 1)
                    .setPhrase(emit.getKeyword())
                    .build();
            results.add(error);
            // Stop after first detection in this location
            break;
        }
    }

}
