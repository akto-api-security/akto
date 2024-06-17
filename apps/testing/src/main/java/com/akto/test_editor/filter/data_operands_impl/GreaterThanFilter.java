package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.test_editor.Utils;

public class GreaterThanFilter extends DataOperandsImpl {
    
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = false;
        Object data = dataOperandFilterRequest.getData();
        Object querySet = dataOperandFilterRequest.getQueryset();
        String validationReson = null;
        if (result) {
            validationReson = "Greater than filter passed";
        } else {
            validationReson = "Greater than filter failed: data -"+ data +", querySet" + querySet;
        }

        return new ValidationResult(result, validationReson);
    }
}
