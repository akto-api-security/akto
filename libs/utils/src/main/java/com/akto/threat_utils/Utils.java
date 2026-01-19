package com.akto.threat_utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.ahocorasick.trie.Trie;

import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;

import org.ahocorasick.trie.Emit;

public class Utils {

    public static Trie generateTrie(String fileName) throws Exception {
        Trie.TrieBuilder builder = Trie.builder();
        try (InputStream is = Utils.class.getResourceAsStream(fileName);
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

    private static String removeThreatPatternsFromUrl(String url, Trie lfiTrie, Trie osCommandInjectionTrie, Trie ssrfTrie) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        String cleanedUrl = url;
        
        cleanedUrl = removeMatchesFromText(cleanedUrl, lfiTrie);
        cleanedUrl = removeMatchesFromText(cleanedUrl, osCommandInjectionTrie);
        cleanedUrl = removeMatchesFromText(cleanedUrl, ssrfTrie);
        cleanedUrl = cleanedUrl.replace("//", "/");
        
        
        return cleanedUrl;
    }

    private static String removeMatchesFromText(String text, Trie trie) {
        if (trie == null || text == null || text.isEmpty()) {
            return text;
        }

        List<Emit> matches = new ArrayList<>();
        for (Emit emit : trie.parseText(text)) {
            if (emit != null && emit.getKeyword() != null) {
                matches.add(emit);
            }
        }

        if (matches.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text);
        for (int i = matches.size() - 1; i >= 0; i--) {
            Emit emit = matches.get(i);
            int start = emit.getStart();
            int end = emit.getEnd() + 1;
            if (start >= 0 && end <= result.length() && start < end) {
                result.delete(start, end);
            }
        }

        return result.toString();
    }

    public static String cleanThreatUrl(String urlFromEvent, Trie lfiTrie, Trie osCommandInjectionTrie,
            Trie ssrfTrie) {
        urlFromEvent = ApiInfo.getNormalizedUrl(urlFromEvent);
        urlFromEvent = removeThreatPatternsFromUrl(urlFromEvent, lfiTrie, osCommandInjectionTrie, ssrfTrie);
        return urlFromEvent;
    }

    public static URLTemplate isMatchingUrl(int apiCollectionId, String urlFromEvent, String methodFromEvent,
            Map<Integer, List<URLTemplate>> apiCollectionUrlTemplates, Trie lfiTrie, Trie osCommandInjectionTrie,
            Trie ssrfTrie) {
        urlFromEvent = cleanThreatUrl(urlFromEvent, lfiTrie, osCommandInjectionTrie, ssrfTrie);
        List<URLTemplate> urlTemplates = apiCollectionUrlTemplates.get(apiCollectionId);
        if (urlTemplates == null || urlTemplates.isEmpty()) {
            return null;
        }
        URLMethods.Method method = URLMethods.Method.fromString(methodFromEvent);
        for (URLTemplate urlTemplate : urlTemplates) {
            if (!urlTemplate.matchTemplate(urlFromEvent, method).equals(URLTemplate.MatchResult.NO_MATCH)) {
                return urlTemplate;
            }
        }
        return null;
    }


}

