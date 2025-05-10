package com.akto.utils.filter;

import com.akto.util.filter.DictionaryFilter;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestDictionaryFilter {
    @Before
    public void initMain() {
        DictionaryFilter.readDictionaryBinary();
    }


    @Test
    public void testValidEnglishWord() {
        assertTrue(DictionaryFilter.isEnglishWord("demo"));
        assertTrue(DictionaryFilter.isEnglishWord("cat"));
        assertTrue(DictionaryFilter.isEnglishWord("example"));
    }

    @Test
    public void testInvalidEnglishWord() {
        assertFalse(DictionaryFilter.isEnglishWord("xyzabc"));
        assertFalse(DictionaryFilter.isEnglishWord("nonexistentword"));
    }

    @Test
    public void testHyphenatedWords() {
        assertTrue(DictionaryFilter.isEnglishWord("well-known"));
        assertFalse(DictionaryFilter.isEnglishWord("well-known-xyzabc"));
    }

    @Test
    public void testUnderscoreSeparatedWords() {
        assertTrue(DictionaryFilter.isEnglishWord("black_white"));
        assertTrue(DictionaryFilter.isEnglishWord("red_blue"));
        assertFalse(DictionaryFilter.isEnglishWord("red_blue_xyzabc"));
    }

    @Test
    public void testDotSeparatedWords() {
        assertTrue(DictionaryFilter.isEnglishWord("hello.world"));
        assertTrue(DictionaryFilter.isEnglishWord("good.bye"));
        assertFalse(DictionaryFilter.isEnglishWord("hello.world.xyzabc"));
    }

    @Test
    public void testTwoLetterWords() {
        assertTrue(DictionaryFilter.isEnglishWord("is"));
        assertTrue(DictionaryFilter.isEnglishWord("by"));
        assertTrue(DictionaryFilter.isEnglishWord("of"));
        assertFalse(DictionaryFilter.isEnglishWord("iz"));
    }

    @Test
    public void testEmptyString() {
        assertFalse(DictionaryFilter.isEnglishWord(""));
    }

    @Test
    public void testMixedCaseWords() {
        assertTrue(DictionaryFilter.isEnglishWord("Demo"));
        assertTrue(DictionaryFilter.isEnglishWord("CaT"));
        assertTrue(DictionaryFilter.isEnglishWord("YesExist"));
        assertFalse(DictionaryFilter.isEnglishWord("NotExistz"));
        assertFalse(DictionaryFilter.isEnglishWord("OSHE2CNS"));
    }

    @Test
    public void testEveryCase() {
        assertTrue(DictionaryFilter.isEnglishWord("smallDog_Cute"));
        assertTrue(DictionaryFilter.isEnglishWord("You.and-JohnHappy"));
        assertTrue(DictionaryFilter.isEnglishWord("DEMO_goingWhere"));
        assertTrue(DictionaryFilter.isEnglishWord("DEMO_goingWhereIS"));
        assertFalse(DictionaryFilter.isEnglishWord("DEMOZ_goingHere"));
        assertFalse(DictionaryFilter.isEnglishWord("DEMO_goingHere.iz"));
        assertFalse(DictionaryFilter.isEnglishWord("You.and-XyzabcHappy"));
    }

    @Test
    public void testTwoLettersWords() {
        for(String word : Arrays.asList(
                "am", "an", "as", "at", "be", "by", "do",
                "go", "he", "if", "in", "is", "it", "me",
                "my", "no", "of", "on", "or", "so", "to",
                "up", "us", "we"
        )) {
            assertTrue(DictionaryFilter.isEnglishWord(word));
        }
    }

    @Test
    public void testNouns() {
        for(String word : Arrays.asList(
                "cat", "dog", "car", "tree", "house",
                "computer", "city", "river", "bird", "mountain"
        )) {
            assertTrue(DictionaryFilter.isEnglishWord(word));
        }
    }

    @Test
    public void testVerbs() {
        for(String word : Arrays.asList(
                "run", "jump", "swim", "read", "write",
                "fly", "eat", "sleep", "walk", "drive"
        )) {
            assertTrue(DictionaryFilter.isEnglishWord(word));
        }
    }

    @Test
    public void testAdverbs() {
        for(String word : Arrays.asList(
                "quickly", "slowly", "carefully", "easily", "happily",
                "sadly", "loudly", "silently", "brightly", "angrily"
        )) {
            assertTrue(DictionaryFilter.isEnglishWord(word));
        }
    }

    @Test
    public void testAdjectives() {
        for(String word : Arrays.asList(
                "happy", "sad", "fast", "slow", "bright",
                "dark", "tall", "short", "new", "old"
        )) {
            assertTrue(DictionaryFilter.isEnglishWord(word));
        }
    }

    @Test
    public void testArticles() {
        for(String word : Arrays.asList(
                "a", "an", "the"
        )) {
            assertTrue(DictionaryFilter.isEnglishWord(word));
        }
    }

    @Test
    public void testPronouns() {
        for(String word : Arrays.asList(
                "he", "she", "it", "they", "we",
                "you", "I", "me", "us", "them"
        )) {
            assertTrue(DictionaryFilter.isEnglishWord(word));
        }
    }
}
