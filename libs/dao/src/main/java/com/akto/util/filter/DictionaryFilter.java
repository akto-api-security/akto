package com.akto.util.filter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DictionaryFilter {
    private static final Logger logger = LoggerFactory.getLogger(DictionaryFilter.class);
    public static BloomFilter<CharSequence> dictFilter = null;

    public static void readDictionaryBinary() {
        // English Word List Repo: https://github.com/dwyl/english-words
        try (InputStream binary = DictionaryFilter.class.getResourceAsStream("/DictionaryBinary")) {
            logger.info("reading dictionary binary");
            dictFilter = BloomFilter.readFrom(binary, Funnels.stringFunnel(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Error while reading bloom filter binary: " + e.getMessage(), e);
        }
    }

    public static boolean isEnglishWord(String word) {
        if(dictFilter == null || word.trim().isEmpty()) return false;

        String[] symbolWords = word.split("[-_.]");
        if(wordsChecker(symbolWords)) return true;

        boolean flag = true;

        for(String symbolWord : symbolWords) {
            String[] camelCaseWords = StringUtils.splitByCharacterTypeCamelCase(symbolWord);
            if(!wordsChecker(camelCaseWords)) {
                flag = false;
                break;
            }
        }

        if(flag) return true;

        return dictFilter.mightContain(word);
    }

    // Return false if any non-empty word in the array is not contained in the dictionary.
    private static boolean wordsChecker(String[] words) {
        for(String seg : words) {
            if(!seg.isEmpty() && !dictFilter.mightContain(seg.toUpperCase())) return false;
        }

        return true;
    }

}
