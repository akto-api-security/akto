package com.akto.threat.detection.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.ahocorasick.trie.Trie;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.monitoring.FilterConfig;

public class ThreatDetector {

    private static final String LFI_OS_FILES_DATA = "/lfi-os-files.data";
    private static final String LFI_FILTER_ID = "LocalFileInclusionLFIRFI";
    private Trie lfiTrie;

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

    public boolean applyFilter(FilterConfig threatFilter, HttpResponseParams httpResponseParams) {
        if (threatFilter.getId().equals(LFI_FILTER_ID)){
            return isLFiThreat(httpResponseParams);
        }
        return false;
    }

    public boolean isLFiThreat(HttpResponseParams httpResponseParams) {
        // TOOD: .get() is expensive , optimize it
        return lfiTrie.containsMatch(httpResponseParams.getOriginalMsg().get());
    }
}
