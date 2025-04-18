package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.type.KeyTypes;

public class ContainsJwt extends DataOperandsImpl {
    
    private static List<Boolean> querySet = new ArrayList<>();
    private static Boolean result = false;
    private static Boolean queryVal;
    private static String data;
    private String[] splitValue;
    String jwtKeyType;

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        result = false;
        querySet.clear();
        try {

            querySet = (List<Boolean>) dataOperandFilterRequest.getQueryset();
            queryVal = (Boolean) querySet.get(0);
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return ValidationResult.getInstance().resetValues(result, "");
        }

        if (data == null || queryVal == null) {
            return ValidationResult.getInstance().resetValues(result, "");
        }

        splitValue = data.toString().split(" ");
        jwtKeyType = null;
        for (String x: splitValue) {
            if (KeyTypes.isJWT(x)) {
                result = true;
                jwtKeyType = x;
                break;
            }
        }
        if (queryVal == result) {
            return ValidationResult.getInstance().resetValues(true, "");
        }
        if (queryVal) {
            return ValidationResult.getInstance().resetValues(false, "");
        }
        return ValidationResult.getInstance().resetValues(false, "");
    }
}
