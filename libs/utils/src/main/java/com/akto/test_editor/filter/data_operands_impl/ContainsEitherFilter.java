package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class ContainsEitherFilter extends DataOperandsImpl {
    
    private static List<String> querySet = new ArrayList<>();
    private static Boolean result = false;
    private static Boolean res;

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        
        result = false;
        querySet.clear();
        String data;
        try {
            Object querysetObj = dataOperandFilterRequest.getQueryset();
            if (querysetObj instanceof List<?>) {
                querySet = (List<String>) querysetObj; 
                // querySet = ((List<?>) querysetObj).stream()
                //         .map(String::valueOf)
                //         .collect(Collectors.toList());
            }
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return ValidationResult.getInstance().resetValues(false, "");
        }
        for (String queryString: querySet) {
            try {
                res = evaluateOnStringQuerySet(data, queryString);
            } catch (Exception e) {
                res = false;
            }
            result = result || res;
            if (result == true) {
                break;
            }
        }
        if (result) {
            return ValidationResult.getInstance().resetValues(result, "");
        }
        return ValidationResult.getInstance().resetValues(result, "");
    }

    public Boolean evaluateOnStringQuerySet(String data, String query) {
        if (data.length() < query.length()) {
            return false;
        }
        return data.contains(query);
    }

}
