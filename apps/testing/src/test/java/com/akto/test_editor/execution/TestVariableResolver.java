package com.akto.test_editor.execution;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

public class TestVariableResolver {

    // @Test
    // public void testResolveExpression() {
    //     Map<String, Object> varMap = new HashMap<>();
    //     varMap.put("var1", "user1");
    //     varMap.put("var2", "user2");
    //     String result = VariableResolver.resolveExpression(varMap, "${var1}");
    //     assertEquals("user1", result);
    //     result = VariableResolver.resolveExpression(varMap, "var1");
    //     assertEquals("user1", result);

    //     result = VariableResolver.resolveExpression(varMap, "${var1}!!!");
    //     assertEquals("user1!!!", result);
    //     result = VariableResolver.resolveExpression(varMap, "var1!!!");
    //     assertEquals("var1!!!", result);

    //     result = VariableResolver.resolveExpression(varMap, "${var1}${var2}");
    //     assertEquals("user1user2", result);
    //     result = VariableResolver.resolveExpression(varMap, "var1${var2}");
    //     assertEquals("var1user2", result);

    //     result = VariableResolver.resolveExpression(varMap, "${var1}&${var2}");
    //     assertEquals("user1&user2", result);
    //     result = VariableResolver.resolveExpression(varMap, "var1&${var2}");
    //     assertEquals("var1&user2", result);

    //     result = VariableResolver.resolveExpression(varMap, "${var3}");
    //     assertEquals("${var3}", result);

    //     result = VariableResolver.resolveExpression(varMap, "${var1}${var1}");
    //     assertEquals("user1user1", result);

    //     result = VariableResolver.resolveExpression(varMap, "akto");
    //     assertEquals("akto", result);
    // }

    @Test
    public void testResolveWordListVar() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("changed_body_value", "akto");
        varMap.put("randomVar", "random");
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));
        String key = "${changed_body_value}${specialCharacters}${randomVar}";

        List<String> result = VariableResolver.resolveWordListVar(key, varMap);
        assertEquals(Arrays.asList("${changed_body_value}.${randomVar}", "${changed_body_value}$${randomVar}", "${changed_body_value}/${randomVar}"), result);


        varMap = new HashMap<>();
        varMap.put("changed_body_value", "akto");
        varMap.put("randomVar", "random");
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));
        key = "asdf${specialCharacters}xyz";

        result = VariableResolver.resolveWordListVar(key, varMap);
        assertEquals(Arrays.asList("asdf.xyz", "asdf$xyz", "asdf/xyz"), result);

        varMap = new HashMap<>();
        varMap.put("changed_body_value", "akto");
        varMap.put("randomVar", "random");
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));
        key = "${specialCharacters}";

        result = VariableResolver.resolveWordListVar(key, varMap);
        assertEquals(Arrays.asList(".", "$", "/"), result);

        varMap = new HashMap<>();
        varMap.put("changed_body_value", "akto");
        varMap.put("randomVar", "random");
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));
        varMap.put("wordList_names", Arrays.asList(".", "$", "/"));
        key = "${changed_body_value}${specialCharacters}${names}";

        result = VariableResolver.resolveWordListVar(key, varMap);
        assertEquals(Arrays.asList("${changed_body_value}..", "${changed_body_value}.$", "${changed_body_value}./",
                "${changed_body_value}$.", "${changed_body_value}$$", "${changed_body_value}$/",
                "${changed_body_value}/.", "${changed_body_value}/$", "${changed_body_value}//"), result);

        varMap = new HashMap<>();
        varMap.put("changed_body_value", "akto");
        varMap.put("randomVar", "random");
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));
        varMap.put("wordList_names", Arrays.asList(".", "$", "/"));
        key = "${changed_body_value}";

        result = VariableResolver.resolveWordListVar(key, varMap);
        assertEquals(Arrays.asList("${changed_body_value}"), result);

        varMap = new HashMap<>();
        varMap.put("changed_body_value", "akto");
        varMap.put("randomVar", "random");
        varMap.put("wordList_specialCharacters", Arrays.asList(".", "$", "/"));
        varMap.put("wordList_names", Arrays.asList(".", "$", "/"));
        key = "nothing here";

        result = VariableResolver.resolveWordListVar(key, varMap);
        assertEquals(Arrays.asList("nothing here"), result);
    }

}
