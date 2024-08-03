package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.type.KeyTypes;

public class ContainsJwt extends DataOperandsImpl {
    
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        List<Boolean> querySet = new ArrayList<>();
        Boolean queryVal;
        Boolean result = false;
        String data;
        try {

            querySet = (List<Boolean>) dataOperandFilterRequest.getQueryset();
            queryVal = (Boolean) querySet.get(0);
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return new ValidationResult(result, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        }

        if (data == null || queryVal == null) {
            return new ValidationResult(result, "");
        }

        String[] splitValue = data.toString().split(" ");
        String jwtKeyType = null;
        for (String x: splitValue) {
            if (KeyTypes.isJWT(x)) {
                result = true;
                jwtKeyType = x;
                break;
            }
        }
        if (queryVal == result) {
            return new ValidationResult(true,
                    queryVal? TestEditorEnums.DataOperands.CONTAINS_JWT.name().toLowerCase() + ": true passed because key:"+ jwtKeyType+" is jwt type":
                            TestEditorEnums.DataOperands.CONTAINS_JWT.name().toLowerCase() + ": false passed because no jwt type found");
        }
        if (queryVal) {
            return new ValidationResult(false, TestEditorEnums.DataOperands.CONTAINS_JWT.name().toLowerCase() + ": true failed because no jwt type found");
        }
        return new ValidationResult(false, TestEditorEnums.DataOperands.CONTAINS_JWT.name().toLowerCase() + ": false failed because key:"+ jwtKeyType+" is jwt type");
    }
}
