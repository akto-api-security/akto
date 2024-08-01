package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.test_editor.Utils;

public class LesserThanEqFilter extends DataOperandsImpl {
    
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = false;
        Object data = dataOperandFilterRequest.getData();
        Object querySet = dataOperandFilterRequest.getQueryset();

        result = Utils.applyIneqalityOperation(data, querySet, "lte");
        String validationReson = null;
        if (result) {
            validationReson = "'lte' filter passed";
        } else {
            validationReson = "'lte' filter failed: ''"+ data +"' > '" + querySet +"'";
        }

        return new ValidationResult(result, validationReson);
    }
}
