package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.test_editor.Util;
import com.akto.util.Pair;

public class JWTSigningAlg extends DataOperandsImpl {
    
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        List<String> querySet = new ArrayList<>();
        String queryVal;
        Boolean result = false;
        String data;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            queryVal = (String) querySet.get(0);
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return new ValidationResult(result, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        }

        if (data == null || queryVal == null) {
            return new ValidationResult(result, "");
        }
        
        String[] splitValues = data.split(" ");
        for (String value: splitValues) {
            if (value.length() < 25 || value.split("\\.").length != 3) {
                // not a jwt based on length and structure
                continue;
            }

            Pair<JSONObject, JSONObject> jwtPair = null;

            try {
                jwtPair = Util.decodeJWT(value);
                JSONObject jwtHeader = jwtPair.getFirst();
  
                if (!jwtHeader.has("alg")) {
                    return new ValidationResult(false, TestEditorEnums.DataOperands.JWT_SIGNING_ALG.name().toLowerCase() + ": failed because alg key is not present in jwt header");
                }
                 
                String alg = jwtHeader.getString("alg");

                if (alg.equals(queryVal)) {
                    return new ValidationResult(true, TestEditorEnums.DataOperands.JWT_SIGNING_ALG.name().toLowerCase() + ": passed because alg in jwt header matches with query value");
                } else {
                    return new ValidationResult(false, TestEditorEnums.DataOperands.JWT_SIGNING_ALG.name().toLowerCase() + ": failed because alg in jwt header does not match with query value");
                }
            } catch (Exception e) {
                // not a jwt
                continue;
            }
        }

        return new ValidationResult(false, TestEditorEnums.DataOperands.JWT_SIGNING_ALG.name().toLowerCase() + ": failed because no jwt type found");
    }
}
