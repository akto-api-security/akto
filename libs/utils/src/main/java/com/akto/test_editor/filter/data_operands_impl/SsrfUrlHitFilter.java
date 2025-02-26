package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.test_editor.Utils;

public class SsrfUrlHitFilter extends DataOperandsImpl {

    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = false;
        List<String> querySet = new ArrayList<>();
        String data;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return result;
        }

        for (String queryString: querySet) {
            if(Utils.sendRequestToSsrfServer(queryString)){
                result = true;
                break;
            }
        }

        return result;
    }

}
