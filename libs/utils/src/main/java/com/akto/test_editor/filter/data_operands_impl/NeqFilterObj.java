package com.akto.test_editor.filter.data_operands_impl;

import static com.akto.testing.Utils.compareWithOriginalResponse;

import java.util.HashMap;
import java.util.List;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;

public class NeqFilterObj extends DataOperandsImpl {
    
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        try {
            Object data = dataOperandFilterRequest.getData();
            Object querySet = dataOperandFilterRequest.getQueryset();
            List<String> queryList = (List) querySet;
            if (queryList == null || queryList.size() == 0) {
                return new ValidationResult(false, TestEditorEnums.DataOperands.EQ_OBJ.name().toLowerCase() + " validation failed because of empty query");
            }
            Double matchVal = compareWithOriginalResponse(data.toString(), queryList.get(0), new HashMap<>());   
            boolean res = (matchVal < 100.0);
            return new ValidationResult(res, TestEditorEnums.DataOperands.EQ.name().toLowerCase() + " validation passed ");            
        } catch (Exception e) {
            return new ValidationResult(false, TestEditorEnums.DataOperands.EQ_OBJ.name().toLowerCase() + " validation failed because of error " + e.getMessage());
        }

    }
}
