package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class NotContainsFilter extends DataOperandsImpl {
    
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = true;
        Boolean res;
        List<String> querySet = new ArrayList<>();
        String data;
        String validationString = null;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return new ValidationResult(result, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        }
        String failedQueryString = null;
        for (String queryString: querySet) {
            try {
                res = evaluateOnStringQuerySet(data.trim(), queryString.trim());
            } catch (Exception e) {
                res = false;
            }
            if (!res) {
                failedQueryString = queryString.trim();
            }
            result = result && res;
        }
        if (result) {
            validationString = TestEditorEnums.DataOperands.NOT_CONTAINS.name().toLowerCase() + " filter passed";
        } else {
            validationString = TestEditorEnums.DataOperands.NOT_CONTAINS.name().toLowerCase() + " filter failed due to '" + data + "' not matching with: " + failedQueryString;
        }
        return new ValidationResult(result, validationString);
    }


    public Boolean evaluateOnStringQuerySet(String data, String query) {
        return !data.toLowerCase().contains(query.toLowerCase());
    }
}
