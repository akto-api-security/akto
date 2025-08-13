package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public class NotMagicValidateFilter extends MagicValidateFilter {

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        // Inverse the result of MagicValidateFilter
        ValidationResult result = super.isValid(dataOperandFilterRequest);
        return new ValidationResult(!result.getIsValid(), result.getValidationReason());
    }
}
