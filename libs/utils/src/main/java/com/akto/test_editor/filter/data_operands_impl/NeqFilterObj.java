package com.akto.test_editor.filter.data_operands_impl;

import static com.akto.testing.Utils.compareWithOriginalResponse;

import java.util.HashMap;
import java.util.List;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class NeqFilterObj extends DataOperandsImpl {
    
    Object querySet;
    List<String> queryList;
    boolean res;

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        
        try {
            data = dataOperandFilterRequest.getData();
            querySet = dataOperandFilterRequest.getQueryset();
            queryList = (List) querySet;
            if (queryList == null || queryList.size() == 0) {
                return ValidationResult.getInstance().resetValues(false, "");
            }
            Double matchVal = compareWithOriginalResponse(data.toString(), queryList.get(0), new HashMap<>());   
            res = (matchVal < 100.0);
            return ValidationResult.getInstance().resetValues(res, "");            
        } catch (Exception e) {
            return ValidationResult.getInstance().resetValues(false, "");
        }

    }
}
