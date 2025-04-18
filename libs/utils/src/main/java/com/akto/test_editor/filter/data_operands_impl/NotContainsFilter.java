package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class NotContainsFilter extends DataOperandsImpl {
    
    Boolean res;
    List<String> querySet;
    String data;
    String failedQueryString;

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        result = true;
        validationString = null;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return ValidationResult.getInstance().resetValues(result, "");
        }
        failedQueryString = null;
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

        return ValidationResult.getInstance().resetValues(result, "");
    }


    public Boolean evaluateOnStringQuerySet(String data, String query) {
        return !data.toLowerCase().contains(query.toLowerCase());
    }
}
