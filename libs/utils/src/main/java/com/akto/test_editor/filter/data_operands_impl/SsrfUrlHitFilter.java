package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.test_editor.Utils;

public class SsrfUrlHitFilter extends DataOperandsImpl {

    List<String> querySet;
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        result = false;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return ValidationResult.getInstance().resetValues(result, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        }

        for (String queryString: querySet) {
            if(Utils.sendRequestToSsrfServer(queryString)){
                result = true;
                break;
            }
        }
        String validationString;
        if (result) {
            validationString = TestEditorEnums.PredicateOperator.SSRF_URL_HIT.name().toLowerCase() + " filter passed";
        } else {
            validationString = TestEditorEnums.PredicateOperator.SSRF_URL_HIT.name().toLowerCase() + " filter failed due to - " + querySet;;
        }
        return ValidationResult.getInstance().resetValues(result, validationString);
    }

}
