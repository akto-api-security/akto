package com.akto.utils.filter;

import com.akto.util.filter.DictionaryFilter;
import org.junit.Before;
import org.junit.Test;

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
    public void testEmptyString() {
        assertFalse(DictionaryFilter.isEnglishWord(""));
    }

    @Test
    public void testMixedCaseWords() {
        assertTrue(DictionaryFilter.isEnglishWord("Demo"));
        assertTrue(DictionaryFilter.isEnglishWord("CaT"));
        assertTrue(DictionaryFilter.isEnglishWord("YesExist"));
        assertFalse(DictionaryFilter.isEnglishWord("NotExistz"));
    }

    @Test
    public void testEveryCase() {
        assertTrue(DictionaryFilter.isEnglishWord("smallDog_Cute"));
        assertTrue(DictionaryFilter.isEnglishWord("You.and-JohnHappy"));
        assertTrue(DictionaryFilter.isEnglishWord("DEMO_goingWhere"));
        assertFalse(DictionaryFilter.isEnglishWord("DEMOZ_goingHere"));
        assertFalse(DictionaryFilter.isEnglishWord("You.and-XyzabcHappy"));
    }

}
