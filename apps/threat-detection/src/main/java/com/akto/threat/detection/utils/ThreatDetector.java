package com.akto.threat.detection.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
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
    public static final String LFI_FILTER_ID = "LocalFileInclusionLFIRFI";
    public static final String SQL_INJECTION_FILTER_ID = "SQLInjection";
    private static Map<String, Object> varMap = new HashMap<>();
    private Trie lfiTrie;
    private static final LoggerMaker logger = new LoggerMaker(ThreatDetector.class, LogDb.THREAT_DETECTION);

    public ThreatDetector() throws Exception {
        Trie.TrieBuilder builder = Trie.builder();

        try (InputStream is = ThreatDetector.class.getResourceAsStream(LFI_OS_FILES_DATA);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                builder.addKeyword(line);
            }
        }

        lfiTrie = builder.build();

    }

    public boolean applyFilter(FilterConfig threatFilter, HttpResponseParams httpResponseParams, RawApi rawApi,
            ApiInfoKey apiInfoKey) {
        if (threatFilter.getId().equals(LFI_FILTER_ID)) {
            return isLFiThreat(httpResponseParams);
        }
        if (threatFilter.getId().equals(SQL_INJECTION_FILTER_ID)) {
            return isSqliThreat(httpResponseParams);
        }
        return validateFilterForRequest(threatFilter, rawApi, apiInfoKey);
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
        
        if(SQLParse.isSQLi(httpResponseParams.getRequestParams().getURL())){
            return true;
        }

        if(SQLParse.isSQLi(httpResponseParams.getRequestParams().getPayload())){
            return true;
        }

        return SQLParse.isSQLi(httpResponseParams.getRequestParams().getHeaders().toString());
    }

    public boolean isLFiThreat(HttpResponseParams httpResponseParams) {
        // TODO: .get() is expensive, optimize it
        return lfiTrie.containsMatch(httpResponseParams.getOriginalMsg().get());
    }
}
