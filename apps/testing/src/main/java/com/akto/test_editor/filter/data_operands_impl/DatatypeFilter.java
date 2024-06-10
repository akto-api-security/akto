package com.akto.test_editor.filter.data_operands_impl;

import java.util.List;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public class DatatypeFilter extends DataOperandsImpl {

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        Object data = dataOperandFilterRequest.getData();
        Object querySet = dataOperandFilterRequest.getQueryset();        
        try {
            List<String> queryList = (List) querySet;
            if (queryList == null || queryList.size() == 0) {
                return new ValidationResult(false, "datatype validation is passed without any query");
            }

            if (data instanceof String && queryList.get(0).equalsIgnoreCase("string")) {
                return new ValidationResult(true, "datatype: string validation is passed because: "+ data + " is string type");
            }
            if (data instanceof Integer && queryList.get(0).equalsIgnoreCase("number")) {
                return new ValidationResult(true, "datatype: number validation is passed because: "+ data + " is number type");
            }
            if (data instanceof Boolean && queryList.get(0).equalsIgnoreCase("boolean")) {
                return new ValidationResult(true, "datatype: boolean validation is passed");
            }
            return new ValidationResult(false, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        } catch (Exception e) {
            return new ValidationResult(false, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        }
        
    }

}
