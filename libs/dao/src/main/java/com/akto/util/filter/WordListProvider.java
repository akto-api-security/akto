package com.akto.util.filter;

import java.util.Arrays;
import java.util.List;

public class WordListProvider {

    // Method to return a list of two letters words
    public static List<String> getTwoLettersWords() {
        return Arrays.asList(
                "am", "an", "as", "at", "be", "by", "do",
                "go", "he", "if", "in", "is", "it", "me",
                "my", "no", "of", "on", "or", "so", "to",
                "up", "us", "we", "ok"
        );
    }

    // Method to return a list of noun words
    public static List<String> getNouns() {
        return Arrays.asList(
                "cat", "dog", "car", "tree", "house",
                "computer", "city", "river", "bird", "mountain"
        );
    }

    // Method to return a list of verb words
    public static List<String> getVerbs() {
        return Arrays.asList(
                "run", "jump", "swim", "read", "write",
                "fly", "eat", "sleep", "walk", "drive"
        );
    }

    // Method to return a list of adverb words
    public static List<String> getAdverbs() {
        return Arrays.asList(
                "quickly", "slowly", "carefully", "easily", "happily",
                "sadly", "loudly", "silently", "brightly", "angrily"
        );
    }

    // Method to return a list of adjective words
    public static List<String> getAdjectives() {
        return Arrays.asList(
                "happy", "sad", "fast", "slow", "bright",
                "dark", "tall", "short", "new", "old"
        );
    }

    // Method to return a list of article words
    public static List<String> getArticles() {
        return Arrays.asList(
                "a", "an", "the"
        );
    }

    // Method to return a list of pronoun words
    public static List<String> getPronouns() {
        return Arrays.asList(
                "he", "she", "it", "they", "we",
                "you", "I", "me", "us", "them"
        );
    }
}