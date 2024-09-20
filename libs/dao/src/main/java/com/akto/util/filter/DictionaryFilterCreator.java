package com.akto.util.filter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DictionaryFilterCreator {

    public static void main(String[] args) {
        DictionaryFilterCreator.insertDictionary();
//        DictionaryFilterCreator.readDictionary();
    }
    public static BloomFilter<CharSequence> dictFilter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 1_000_000, 0.001);

    public static void insertDictionary() {
        /*
            - English Word List Repo: https://github.com/dwyl/english-words/blob/master/words_alpha.txt
        */
        try(InputStream inputStream = DictionaryFilterCreator.class.getResourceAsStream("/words_alpha.txt")) {
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                if(inputStream == null) {
                    System.err.println("File not found!");
                    return;
                }

                String line;
                while((line = reader.readLine()) != null) {
                    if(line.length() > 2) dictFilter.put(line.toUpperCase());
                }

                insertWords(WordListProvider.getTwoLettersWords());
                insertWords(WordListProvider.getAdjectives());
                insertWords(WordListProvider.getAdverbs());
                insertWords(WordListProvider.getNouns());
                insertWords(WordListProvider.getVerbs());
                insertWords(WordListProvider.getArticles());
                insertWords(WordListProvider.getPronouns());

                try (FileOutputStream fos = new FileOutputStream("libs/dao/src/main/resources/DictionaryBinary")) {
                    dictFilter.writeTo(fos);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insertWords(List<String> wordList) {
        for(String twoLetterWord : wordList) {
            dictFilter.put(twoLetterWord.toUpperCase());
        }
    }

    public static void readDictionary() {
        try(InputStream fis = DictionaryFilterCreator.class.getResourceAsStream("/DictionaryBinary")) {
            BloomFilter<CharSequence> bloomFilter = BloomFilter.readFrom(fis, Funnels.stringFunnel(StandardCharsets.UTF_8));

            System.out.println(bloomFilter.mightContain("aa".toUpperCase()));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
