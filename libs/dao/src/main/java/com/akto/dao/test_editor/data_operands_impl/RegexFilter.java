package com.akto.dao.test_editor.data_operands_impl;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.test_editor.Utils;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class RegexFilter extends DataOperandsImpl {
    
    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        
        Boolean result = false;
        Boolean res;
        List<String> querySet = new ArrayList<>();
        String data;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return result;
        }
        for (String queryString: querySet) {
            res = Utils.checkIfContainsMatch(data, queryString);
            result = result || res;
        }
        return result;
    }

}
