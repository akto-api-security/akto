package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akto.dto.type.KeyTypes;
import com.akto.util.modifier.AddJkuJWTModifier;
import com.akto.util.modifier.InvalidSignatureJWTModifier;
import com.akto.util.modifier.JwtKvModifier;
import com.akto.util.modifier.NoneAlgoJWTModifier;
import com.mongodb.BasicDBObject;

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

    public static Object resolveContextVariable(Map<String, Object> varMap, String expression) {

        Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            try {

                // split with '.', check if length is 2 and second element should be key/value

                String match = matcher.group(0);
                match = match.substring(2, match.length());
                match = match.substring(0, match.length() - 1);

                String[] params = match.split("\\.");
                if (params.length < 2) {
                    return expression;
                }
                String firstParam = params[0];
                String secondParam = params[1];
                Object val = getValue(varMap, "context_" + firstParam);
                if (val == null) {
                    return expression;
                }
                ArrayList<String> listVal = new ArrayList<>();

                if (!(val instanceof ArrayList)) {
                    return expression;
                }
                ArrayList<BasicDBObject> contextListVal = (ArrayList<BasicDBObject>) val;
                for (BasicDBObject obj: contextListVal) {
                    if (secondParam.equalsIgnoreCase("key")) {
                        listVal.add(obj.get("key").toString());
                    } else if (secondParam.equalsIgnoreCase("value")) {
                        listVal.add(obj.get("value").toString());
                    }
                } 

                return listVal;
            } catch (Exception e) {
                return expression;
            }
        }
        return null;
    }

    public static Object resolveContextKey(Map<String, Object> varMap, String expression) {
        String[] params = expression.split("\\.");
        if (params.length < 2) {
            return expression;
        }
        String firstParam = params[0];
        String secondParam = params[1];
        Object val = getValue(varMap, "context_" + firstParam);
        if (val == null) {
            return expression;
        }
        ArrayList<String> listVal = new ArrayList<>();

        if (!(val instanceof ArrayList)) {
            return expression;
        }
        ArrayList<BasicDBObject> contextListVal = (ArrayList<BasicDBObject>) val;
        for (BasicDBObject obj: contextListVal) {
            if (secondParam.equalsIgnoreCase("key")) {
                listVal.add(obj.get("key").toString());
            } else if (secondParam.equalsIgnoreCase("value")) {
                listVal.add(obj.get("value").toString());
            }
        }
        return listVal;
    }

    public static Boolean isAuthContext(Object val) {
        String expression = "";
        if (!(val instanceof String)) {
            if (val instanceof Map) {
                Map<String, Object> valMap = (Map) val;
                if (valMap.size() != 1) return false;

                expression = valMap.keySet().iterator().next();
                
            } else {
                return false;                
            }

        } else {
            expression = val.toString();
        }

        Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            try {

                // split with '.', check if length is 2 and second element should be key/value

                String match = matcher.group(0);
                match = match.substring(2, match.length());
                match = match.substring(0, match.length() - 1);

                String[] params = match.split("\\.");
                if (params.length < 2) {
                    return false;
                }
                String firstParam = params[0];
                String secondParam = params[1];

                if (!firstParam.equalsIgnoreCase("auth_context")) {
                    return false;
                }

                if (secondParam.equalsIgnoreCase("none_algo_token") || secondParam.equalsIgnoreCase("invalid_signature_token") 
                    || secondParam.equalsIgnoreCase("jku_added_token") || secondParam.startsWith("modify_jwt") ) {
                        return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;

    }

    public static String resolveAuthContext(Object resolveObj, Map<String, List<String>> headers, String headerKey) {

        String origExpression = null;
        if (!(resolveObj instanceof String)) {
            if (resolveObj instanceof Map) {
                Map<String, Object> resolveMap = (Map) resolveObj;
                if (resolveMap.size() != 1) return null;

                origExpression = resolveMap.keySet().iterator().next();
                
            } else {
                return null;
            }

        } else {
            origExpression = resolveObj.toString();
        }
        
        String expression = origExpression;
        expression = expression.substring(2, expression.length());
        expression = expression.substring(0, expression.length() - 1);

        String authContextConstant = "auth_context.";
        String secondParam = expression.substring(authContextConstant.length());// params[1];

        if (!headers.containsKey(headerKey)) {
            return null;
        }

        String headerVal = headers.get(headerKey).get(0);

        String[] splitValue = headerVal.toString().split(" ");
        String modifiedHeaderVal = null;

        List<String> finalValue = new ArrayList<>();

        for (String val: splitValue) {
            if (!KeyTypes.isJWT(val)) {
                finalValue.add(val);
                continue;
            }
            if (secondParam.equalsIgnoreCase("none_algo_token")) {
                NoneAlgoJWTModifier noneAlgoJWTModifier = new NoneAlgoJWTModifier("none");
                try {
                    modifiedHeaderVal = noneAlgoJWTModifier.jwtModify("", val);
                } catch(Exception e) {
                    return null;
                }
            } else if (secondParam.equalsIgnoreCase("invalid_signature_token")) {
                InvalidSignatureJWTModifier invalidSigModified = new InvalidSignatureJWTModifier();
                modifiedHeaderVal = invalidSigModified.jwtModify("", val);
            } else if (secondParam.equalsIgnoreCase("jku_added_token")) {
                AddJkuJWTModifier addJkuJWTModifier = new AddJkuJWTModifier();
                try {
                    modifiedHeaderVal = addJkuJWTModifier.jwtModify("", val);
                } catch(Exception e) {
                    return null;
                }
            } else if (secondParam.equalsIgnoreCase("modify_jwt")) {
                try {
                    Map<String, Object> kvPairMap = (Map) ((Map)resolveObj).get(origExpression);
                    String kvKey = kvPairMap.keySet().iterator().next();
                    JwtKvModifier jwtKvModifier = new JwtKvModifier(kvKey, kvPairMap.get(kvKey).toString());
                    modifiedHeaderVal = jwtKvModifier.jwtModify("", val);
                } catch (Exception e) {
                    return null;
                }
            }

            finalValue.add(modifiedHeaderVal);
        }

        return finalValue.isEmpty() ? null : String.join( " ", finalValue);
    }

    public static Boolean isWordListVariable(Object key, Map<String, Object> varMap) {
        if (key == null || !(key instanceof String)) {
            return false;
        }

        if (key.toString().length() < 3) {
            return false;
        }

        String expression = key.toString();

        expression = expression.substring(2, expression.length());
        expression = expression.substring(0, expression.length() - 1);

        Boolean isWordListVar = varMap.containsKey("wordList_" + expression);
        return isWordListVar;
    }

    public static List<String> resolveWordListVar(String key, Map<String, Object> varMap) {
        String expression = key.toString();

        expression = expression.substring(2, expression.length());
        expression = expression.substring(0, expression.length() - 1);

        return (List<String>) varMap.get("wordList_" + expression);
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
