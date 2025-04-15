package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.test_editor.Utils;

public class RegexFilter extends DataOperandsImpl {
    
    Boolean res;

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        
        result = false;
        List<String> querySet = new ArrayList<>();
        String data;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return ValidationResult.getInstance().resetValues(result, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        }
        for (String queryString: querySet) {
            try {
                res = Utils.checkIfContainsMatch(data.trim(), queryString.trim());
            } catch (Exception e) {
                res = false;
            }
            result = result || res;
        }
        validationString = null;
        if (result) {
            validationString = TestEditorEnums.DataOperands.REGEX.name().toLowerCase() + " filter passed";
        } else {
            validationString = TestEditorEnums.DataOperands.REGEX.name().toLowerCase() + " filter failed due to '" + data + "' not matching for - " + querySet;;
        }
        return ValidationResult.getInstance().resetValues(result, validationString);
    }

}
