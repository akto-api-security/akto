package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dao.test_editor.TestEditorEnums;
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
            validationReson = TestEditorEnums.DataOperands.GT.name().toLowerCase() + " filter passed";
        } else {
            validationReson = TestEditorEnums.DataOperands.GT.name().toLowerCase() + " filter failed: '"+ data +"' <= '" + querySet +"'";
        }

        return new ValidationResult(result, validationReson);
    }
}
