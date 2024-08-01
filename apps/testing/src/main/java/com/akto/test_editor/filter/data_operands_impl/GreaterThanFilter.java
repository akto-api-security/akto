package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.test_editor.Utils;

public class GreaterThanFilter extends DataOperandsImpl {
    
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = false;
        Object data = dataOperandFilterRequest.getData();
        Object querySet = dataOperandFilterRequest.getQueryset();
        result = Utils.applyIneqalityOperation(data, querySet, "gt");
        String validationReson = null;
        if (result) {
            validationReson = "'gt' filter passed";
        } else {
            validationReson = "'gt' filter failed: '"+ data +"' <= '" + querySet +"'";
        }

        return new ValidationResult(result, validationReson);
    }
}
