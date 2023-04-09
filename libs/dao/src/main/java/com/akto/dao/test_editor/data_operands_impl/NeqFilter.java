package com.akto.dao.test_editor.data_operands_impl;

import java.util.List;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public class NeqFilter extends DataOperandsImpl {

    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = false;
        Object data = dataOperandFilterRequest.getData();
        Object querySet = dataOperandFilterRequest.getQueryset();
        
        try {
            if (data instanceof String) {
                List<String> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return false;
                }
                result = !data.toString().toLowerCase().equals(queryList.get(0).toLowerCase());
            }

            if (data instanceof Integer) {
                List<Integer> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return false;
                }
                Integer dataInt = (Integer) data;
                result = (dataInt != queryList.get(0));                
            }
            
            if (data instanceof Boolean && querySet instanceof Boolean) {
                List<Boolean> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return false;
                }
                Boolean dataBool = (Boolean) data;
                result = (dataBool != queryList.get(0));
            }
            
        } catch (Exception e) {
            return false;
        }
        
        return result;
    }

}
