package com.akto.test_editor.filter.data_operands_impl;

import java.util.List;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class NeqFilter extends DataOperandsImpl {

    private static Object querySet;

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        result = false;
        data = dataOperandFilterRequest.getData();
        querySet = dataOperandFilterRequest.getQueryset();
        validationString = null;
        try {
            if (data instanceof String) {
                List<String> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return ValidationResult.getInstance().resetValues(false, TestEditorEnums.DataOperands.NEQ.name().toLowerCase() + " filter failed because empty queryset");
                }
                result = !data.toString().toLowerCase().equals(queryList.get(0).toLowerCase());
                if (result) {
                    validationString = TestEditorEnums.DataOperands.NEQ.name().toLowerCase() + " validation passed";
                } else {
                    validationString = TestEditorEnums.DataOperands.NEQ.name().toLowerCase() + " validation failed: data - "+ data.toString().toLowerCase() + "query - " + queryList.get(0).toLowerCase();
                }
            }

            if (data instanceof Integer) {
                List<Integer> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return ValidationResult.getInstance().resetValues(false, TestEditorEnums.DataOperands.NEQ.name().toLowerCase() + " filter failed because empty queryset");
                }
                Integer dataInt = (Integer) data;

                query = queryList.get(0);
                if (query instanceof String) {
                    int queryInt = Integer.parseInt((String) query);
                    result = (int) dataInt != queryInt;
                } else {
                    result = ((int) dataInt != (int) queryList.get(0));
                }
                if (result) {
                    validationString = TestEditorEnums.DataOperands.NEQ.name().toLowerCase() + " filter passed";
                } else {
                    validationString = TestEditorEnums.DataOperands.NEQ.name().toLowerCase() + " filter failed: data - "+ data + ", query - " + queryList.get(0);
                }
            }
            
            if (data instanceof Boolean && querySet instanceof Boolean) {
                List<Boolean> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return ValidationResult.getInstance().resetValues(false, TestEditorEnums.DataOperands.NEQ.name().toLowerCase() + " filter failed because empty queryset");
                }
                dataBool = (Boolean) data;
                query = queryList.get(0);
                if (query instanceof String) {
                    queryBool = Boolean.valueOf((String) query);
                    result = (boolean) dataBool != queryBool;
                } else {
                    result = ((boolean) dataBool != (boolean) queryList.get(0));
                }
                if (result) {
                    validationString = TestEditorEnums.DataOperands.NEQ.name().toLowerCase() + " filter passed";
                } else {
                    validationString = TestEditorEnums.DataOperands.NEQ.name().toLowerCase() + " filter failed because boolean data and query matched";
                }
            }
            
        } catch (Exception e) {
            return ValidationResult.getInstance().resetValues(false, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        }

        
        return ValidationResult.getInstance().resetValues(result, validationString);
    }

}
