package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.type.KeyTypes;

public class ContainsJwt extends DataOperandsImpl {
    
    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        List<Boolean> querySet = new ArrayList<>();
        Boolean queryVal;
        Boolean result = false;
        String data;
        try {

            querySet = (List<Boolean>) dataOperandFilterRequest.getQueryset();
            queryVal = (Boolean) querySet.get(0);
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return false;
        }

        if (data == null || queryVal == null) {
            return false;
        }

        String[] splitValue = data.toString().split(" ");
        for (String x: splitValue) {
            if (KeyTypes.isJWT(x)) {
                result = true;
                break;
            }
        }
        return result && queryVal;
    }
}
