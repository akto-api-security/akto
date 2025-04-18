package com.akto.test_editor.filter.data_operands_impl;

import java.util.List;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class EqFilter extends DataOperandsImpl {

    private static Boolean result = false;
    private static Object querySet;

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        result = false;
        data = dataOperandFilterRequest.getData();
        querySet = dataOperandFilterRequest.getQueryset();
        
        try {
            if (data instanceof String) {
                List<String> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return ValidationResult.getInstance().resetValues(false, "");
                }
                result = data.toString().trim().toLowerCase().equals(queryList.get(0).trim().toLowerCase());
                if (result) {
                    return ValidationResult.getInstance().resetValues(result, "");
                } else {
                    return ValidationResult.getInstance().resetValues(result, "");
                }
            }

            if (data instanceof Integer) {
                List<Integer> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return ValidationResult.getInstance().resetValues(false, TestEditorEnums.DataOperands.EQ.name().toLowerCase() + " validation failed because of empty query");
                }
                dataInt = (Integer) data;

                query = queryList.get(0);
                if (query instanceof String) {
                    int queryInt = Integer.parseInt((String) query);
                    result = (int) dataInt == queryInt;
                } else {
                    result = ((int) dataInt == (int) queryList.get(0));
                }
                if (result) {
                    return ValidationResult.getInstance().resetValues(result, "");
                } else {
                    return ValidationResult.getInstance().resetValues(result, "");
                }
            }
            
            if (data instanceof Boolean ) {
                List<Boolean> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return ValidationResult.getInstance().resetValues(false, "");
                }
                dataBool = (Boolean) data;

                query = queryList.get(0);
                if (query instanceof String) {
                    queryBool = Boolean.valueOf((String) query);
                    result = (boolean) dataBool == queryBool;
                } else {
                    result = ((boolean) dataBool == (boolean) queryList.get(0));
                }
                if (result) {
                    return ValidationResult.getInstance().resetValues(result, "");
                } else {
                    return ValidationResult.getInstance().resetValues(result, "");
                }
            }
            
        } catch (Exception e) {
            return ValidationResult.getInstance().resetValues(false, "");
        }
        return ValidationResult.getInstance().resetValues(result, "");
    }

}
