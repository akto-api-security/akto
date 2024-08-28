package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class ContainsEitherFilter extends DataOperandsImpl {
    
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        
        Boolean result = false;
        Boolean res;
        List<String> querySet = new ArrayList<>();
        List<String> notMatchedQuerySet = new ArrayList<>();
        String data;
        try {
            Object querysetObj = dataOperandFilterRequest.getQueryset();
            if (querysetObj instanceof List<?>) {
                querySet = ((List<?>) querysetObj).stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
            } else {
                querySet = new ArrayList<>();
            }
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return new ValidationResult(false, ValidationResult.GET_QUERYSET_CATCH_ERROR);
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
            return new ValidationResult(result, "");
        }
        return new ValidationResult(result, TestEditorEnums.DataOperands.CONTAINS_EITHER.name().toLowerCase() + " failed due to '"+data+"' not matching with :" + notMatchedQuerySet);
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
