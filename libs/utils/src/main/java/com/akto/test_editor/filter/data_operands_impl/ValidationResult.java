package com.akto.test_editor.filter.data_operands_impl;

public class ValidationResult {

    private static ValidationResult instance;

    public static ValidationResult getInstance(){
        if(instance == null){
            instance = new ValidationResult();
        }
        return instance;
    }

    public static final String GET_QUERYSET_CATCH_ERROR = "Error while parsing data";
    Boolean isValid;
    String validationReason;

    private ValidationResult() {
    }

    private ValidationResult(Boolean isValid, String validationReason) {
        this.isValid = isValid;
        this.validationReason = validationReason;
    }

    public Boolean getIsValid() {
        return isValid;
    }

    public String getValidationReason() {
        return validationReason;
    }

    public ValidationResult resetValues(Boolean isValid, String validationReason) {
        this.isValid = isValid;
        this.validationReason = validationReason;
        return this;
    }

}
