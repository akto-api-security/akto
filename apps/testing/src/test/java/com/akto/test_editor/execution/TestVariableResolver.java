package com.akto.test_editor.execution;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

public class TestVariableResolver {

    @Test
    public void testResolveExpression() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("var1", "user1");
        varMap.put("var2", "user2");
        String result = VariableResolver.resolveExpression(varMap, "${var1}");
        assertEquals("user1", result);
        result = VariableResolver.resolveExpression(varMap, "var1");
        assertEquals("user1", result);

        result = VariableResolver.resolveExpression(varMap, "${var1}!!!");
        assertEquals("user1!!!", result);
        result = VariableResolver.resolveExpression(varMap, "var1!!!");
        assertEquals("var1!!!", result);

        result = VariableResolver.resolveExpression(varMap, "${var1}${var2}");
        assertEquals("user1user2", result);
        result = VariableResolver.resolveExpression(varMap, "var1${var2}");
        assertEquals("var1user2", result);

        result = VariableResolver.resolveExpression(varMap, "${var1}&${var2}");
        assertEquals("user1&user2", result);
        result = VariableResolver.resolveExpression(varMap, "var1&${var2}");
        assertEquals("var1&user2", result);

        result = VariableResolver.resolveExpression(varMap, "${var3}");
        assertEquals("${var3}", result);

        result = VariableResolver.resolveExpression(varMap, "${var1}${var1}");
        assertEquals("user1user1", result);
    }

}
