package com.akto.test_editor.filter.data_operands_impl;

import static org.mockito.Answers.valueOf;

import java.util.List;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public class EqFilter extends DataOperandsImpl {

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = false;
        Object data = dataOperandFilterRequest.getData();
        Object querySet = dataOperandFilterRequest.getQueryset();
        
        try {
            if (data instanceof String) {
                List<String> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return new ValidationResult(false, "eq validation failed because of empty query");
                }
                result = data.toString().trim().toLowerCase().equals(queryList.get(0).trim().toLowerCase());
            }

            if (data instanceof Integer) {
                List<Integer> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    new ValidationResult(false, "eq validation failed because of empty query");
                }
                Integer dataInt = (Integer) data;

                Object query = queryList.get(0);
                if (query instanceof String) {
                    int queryInt = Integer.parseInt((String) query);
                    result = (int) dataInt == queryInt;
                } else {
                    result = ((int) dataInt == (int) queryList.get(0));
                }
                if (result) {
                    return new ValidationResult(result, "eq validation passed ");
                } else {
                    return new ValidationResult(result, "eq validation failed because: "+ dataInt +" != " + query);
                }
            }
            
            if (data instanceof Boolean ) {
                List<Boolean> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    new ValidationResult(false, "eq validation failed because of empty query");
                }
                Boolean dataBool = (Boolean) data;

                Object query = queryList.get(0);
                if (query instanceof String) {
                    Boolean queryBool = Boolean.valueOf((String) query);
                    result = (boolean) dataBool == queryBool;
                } else {
                    result = ((boolean) dataBool == (boolean) queryList.get(0));
                }
                if (result) {
                    return new ValidationResult(result, "eq validation passed ");
                } else {
                    return new ValidationResult(result, "eq validation failed because: boolean query do not match");
                }
            }
            
        } catch (Exception e) {
            return new ValidationResult(false, "eq validation failed because of empty query");
        }
        return new ValidationResult(result, "eq validation failed because of empty query");
    }

}
