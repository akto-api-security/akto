package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class ContainsEitherFilter extends DataOperandsImpl {
    
    private static List<String> querySet = new ArrayList<>();
    private static List<String> notMatchedQuerySet = new ArrayList<>();
    private static Boolean result = false;
    private static Boolean res;

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        
        result = false;
        querySet.clear();
        notMatchedQuerySet.clear();
        String data;
        try {
            Object querysetObj = dataOperandFilterRequest.getQueryset();
            if (querysetObj instanceof List<?>) {
                querySet = ((List<?>) querysetObj).stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
            }
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return ValidationResult.getInstance().resetValues(false, "");
        }
        for (String queryString: querySet) {
            try {
                res = evaluateOnStringQuerySet(data.trim(), queryString.trim());
            } catch (Exception e) {
                res = false;
            }
            if (!res) {
                notMatchedQuerySet.add(queryString);
            }
            result = result || res;
        }
        if (result) {
            return ValidationResult.getInstance().resetValues(result, "");
        }
        return ValidationResult.getInstance().resetValues(result, "");
    }

    public Boolean evaluateOnListQuerySet(String data, List<String> querySet) {
        Boolean result = false;
        Boolean res;
        for (String queryString: querySet) {
            res = evaluateOnStringQuerySet(data.trim(), queryString.trim());
            result = result || res;
        }
        return result;
    }

    public Boolean evaluateOnStringQuerySet(String data, String query) {
        return data.toLowerCase().contains(query.toLowerCase());
    }

}
