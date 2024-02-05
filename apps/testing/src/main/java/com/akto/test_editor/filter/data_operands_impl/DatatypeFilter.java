package com.akto.test_editor.filter.data_operands_impl;

import java.util.List;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public class DatatypeFilter extends DataOperandsImpl {

    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        Object data = dataOperandFilterRequest.getData();
        Object querySet = dataOperandFilterRequest.getQueryset();        
        try {
            List<String> queryList = (List) querySet;
            if (queryList == null || queryList.size() == 0) {
                return false;
            }

            if (data instanceof String && queryList.get(0).equalsIgnoreCase("string")) {
                return true;
            }
            if (data instanceof Integer && queryList.get(0).equalsIgnoreCase("number")) {
                return true;
            }
            if (data instanceof Boolean && queryList.get(0).equalsIgnoreCase("boolean")) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
        
    }

}
