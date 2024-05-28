package com.akto.test_editor.filter.data_operands_impl;

public class ValidationResult {
    public static final String GET_QUERYSET_CATCH_ERROR = "Error while getting querySet and getting Data";
    Boolean isValid;
    String validationReason;
    public ValidationResult(Boolean isValid, String validationReason) {
        this.isValid = isValid;
        this.validationReason = validationReason;
    }

    Boolean getIsValid() {
        return isValid;
    }

    String getValidationReason() {
        return validationReason;
    }
}
