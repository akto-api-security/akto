package com.akto.test_editor.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class VariableResolverWordListTest {

    @AfterEach
    public void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    public void substitutesSingleWordListInTemplate() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));

        List<String> result = VariableResolver.resolveWordListVar("asdf${specialCharacters}xyz", varMap);

        assertEquals(Arrays.asList("asdf.xyz", "asdf$xyz", "asdf/xyz"), result);
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    public void substitutesBareWordList() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));

        List<String> result = VariableResolver.resolveWordListVar("${specialCharacters}", varMap);

        assertEquals(Arrays.asList(".", "$", "/"), result);
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    public void leavesNonWordListPlaceholdersUntouched() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));

        List<String> result = VariableResolver.resolveWordListVar(
                "${changed_body_value}${specialCharacters}${randomVar}", varMap);

        assertEquals(Arrays.asList(
                "${changed_body_value}.${randomVar}",
                "${changed_body_value}$${randomVar}",
                "${changed_body_value}/${randomVar}"), result);
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    public void cartesianProductOfTwoWordLists() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));
        varMap.put("wordList_names", Arrays.asList(".", "$", "/"));

        List<String> result = VariableResolver.resolveWordListVar(
                "${changed_body_value}${specialCharacters}${names}", varMap);

        assertEquals(Arrays.asList(
                "${changed_body_value}..", "${changed_body_value}.$", "${changed_body_value}./",
                "${changed_body_value}$.", "${changed_body_value}$$", "${changed_body_value}$/",
                "${changed_body_value}/.", "${changed_body_value}/$", "${changed_body_value}//"), result);
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    public void returnsExpressionWhenNoWordListPlaceholder() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));

        assertEquals(Arrays.asList("${changed_body_value}"),
                VariableResolver.resolveWordListVar("${changed_body_value}", varMap));
        assertEquals(Arrays.asList("nothing here"),
                VariableResolver.resolveWordListVar("nothing here", varMap));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    public void isWordListVariableDetectsOnlyWordListKeys() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("wordList_payloads", Arrays.asList("a", "b"));

        assertTrue(VariableResolver.isWordListVariable("${payloads}", varMap));
        assertTrue(VariableResolver.isWordListVariable("prefix${payloads}suffix", varMap));
        assertFalse(VariableResolver.isWordListVariable("${other}", varMap));
        assertFalse(VariableResolver.isWordListVariable("no placeholders", varMap));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    public void resolveExpressionExpandsWordListWithoutRaisingInterrupt() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("wordList_ids", Arrays.asList("1", "2", "3"));

        List<Object> result = VariableResolver.resolveExpression(varMap, (Object) "${ids}");

        assertEquals(Arrays.asList(1, 2, 3), result);
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
