package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;


public class ConformSchemaFilter extends DataOperandsImpl {

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = false;

        return new ValidationResult(result, result ? "Validation succeeded" : TestEditorEnums.DataOperands.EQ.name().toLowerCase() + " validation failed due to schema mismatch");
    }
}