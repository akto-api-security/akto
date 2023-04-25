package com.akto.test_editor.execution;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableResolver {
    
    public static Object getValue(Map<String, Object> varMap, String key) {
        if (!varMap.containsKey(key)) {
            return null;
        }
        Object obj = varMap.get(key);
        return obj;
    }

    public static String resolveExpression(Map<String, Object> varMap, String expression) {

        Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            try {
                String match = matcher.group(0);
                match = match.substring(2, match.length());
                match = match.substring(0, match.length() - 1);
                Object val = getValue(varMap, match);
                String valString = val.toString();
                expression = expression.replaceAll("(\\$\\{[^}]*\\})", valString);
            } catch (Exception e) {
                return expression;
            }
        } else {
            Object val = getValue(varMap, expression);
            if (val == null) {
                return expression;
            } else {
                return val.toString();
            }
        }
        return expression;

    }

    // public Object resolveExpression(Map<String, Object> varMap, String expression) {

    //     Object val = null;

    //     Pattern pattern = Pattern.compile("(\\S+)\\s?[\\+\\-\\*\\/]\\s?(\\S+)");
    //     Matcher matcher = pattern.matcher(expression);

    //     if (matcher.find()) {
    //         try {
    //             String operand1 = (String) resolveVariable(varMap, matcher.group(1));
    //             String operator = (String) resolveVariable(varMap, matcher.group(2));
    //             String operand2 = (String) resolveVariable(varMap, matcher.group(3));
    //             val = evaluateExpressionValue(operand1, operator, operand2);

    //         } catch(Exception e) {
    //             return expression;
    //         }
            
    //     }

    //     return val;

    // }

    // public Object evaluateExpressionValue(String operand1, String operator, String operand2) {

    //     switch(operator) {
    //         case "+":
    //             add(operand1, operator, operand2);
    //         case "-":
    //             subtract(operand1, operator, operand2);
    //         case "*":
    //             multiply(operand1, operator, operand2);
    //         case "/":
    //             divide(operand1, operator, operand2);
    //         default:
    //             // throw exception
    //     }

    //     return null;

    // }

    // public Object multiply(String operand1, String operator, String operand2) {
    //     try {
    //         int op1 = Integer.parseInt(operand1);
    //         int op2 = Integer.parseInt(operand2);
    //         return op1 * op2;
    //     } catch (Exception e) {
    //         return null;
    //     }

    // }

    // public Object divide(String operand1, String operator, String operand2) {
    //     try {
    //         int op1 = Integer.parseInt(operand1);
    //         int op2 = Integer.parseInt(operand2);
    //         if (op2 == 0) {
    //             throw new Exception("invalid operand2");
    //         }
    //         return op1 / op2;
    //     } catch (Exception e) {
    //         return null;
    //     }

    // }

    // public Object subtract(String operand1, String operator, String operand2) {
    //     try {
    //         int op1 = Integer.parseInt(operand1);
    //         int op2 = Integer.parseInt(operand2);
    //         return op1 - op2;
    //     } catch (Exception e) {
    //         return null;
    //     }

    // }

    // public Object add(String operand1, String operator, String operand2) {

    //     try {
    //         int op1 = Integer.parseInt(operand1);
    //         int op2 = Integer.parseInt(operand2);
    //         return op1 + op2;
    //     } catch (Exception e) {
    //         //return null;
    //     }

    //     try {
    //         String op1 = (String) operand1;
    //         String op2 = (String) operand2;
    //         return op1 + op2;
    //     } catch (Exception e) {
    //         //return null;
    //     }

    //     return null;

    // }

}
