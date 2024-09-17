package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class ContainsAllFilter extends DataOperandsImpl {
    
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = true;
        Boolean res;
        List<String> querySet = new ArrayList<>();
        List<String> notMatchedQuerySet = new ArrayList<>();
        String data;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return new ValidationResult(result, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        }
        for (String queryString: querySet) {
            try {
                res = evaluateOnStringQuerySet(data.trim(), queryString.trim());
            } catch (Exception e) {
                res = false;
                notMatchedQuerySet.add(queryString.trim());
            }
            if (!res) {
                notMatchedQuerySet.add(queryString);
            }
            result = result && res;
        }
        if (result) {
            return new ValidationResult(result, "");
        }
        return new ValidationResult(result, TestEditorEnums.DataOperands.CONTAINS_ALL.name().toLowerCase() + " failed due to '"+data+"' not matching with :" + notMatchedQuerySet);
    }

    public Boolean evaluateOnListQuerySet(String data, List<String> querySet) {
        Boolean result = true;
        for (String queryString: querySet) {
            result = result && evaluateOnStringQuerySet(data.trim(), queryString.trim());
        }
        return result;
    }

    public Boolean evaluateOnStringQuerySet(String data, String query) {
        return data.toLowerCase().contains(query.toLowerCase());
    }

}
