package com.akto.util.filter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DictionaryFilter {
    private static final Logger logger = LoggerFactory.getLogger(DictionaryFilter.class);
    public static BloomFilter<CharSequence> dictFilter = null;

    public static void readDictionaryBinary() {
        try (InputStream binary = DictionaryFilter.class.getResourceAsStream("/DictionaryBinary")) {
            logger.info("reading dictionary binary");
            dictFilter = BloomFilter.readFrom(binary, Funnels.stringFunnel(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Error while reading bloom filter binary: " + e.getMessage(), e);
        }
    }

    public static boolean isEnglishWord(String word) {
        if(dictFilter == null || word.trim().isEmpty()) return false;

        String[] wordSegments = word.split("[-_.]");

        for(String seg : wordSegments) {
            if(!seg.isEmpty() && !dictFilter.mightContain(seg.toUpperCase())) return false;
        }

        return true;
    }

}
