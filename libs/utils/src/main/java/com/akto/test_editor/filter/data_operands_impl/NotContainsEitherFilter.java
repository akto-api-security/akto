package com.akto.test_editor.filter.data_operands_impl;

import java.util.ArrayList;
import java.util.List;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public class NotContainsEitherFilter extends DataOperandsImpl {
    
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

        // data is the same for every item in querySet - trim+lowercase it once instead of once per item.
        // On failure, leave it null so the per-item try/catch below fails exactly like the old
        // per-item data.trim() would have - same res=false per item, same accumulated result.
        String normalizedData = null;
        try {
            normalizedData = data.trim().toLowerCase();
        } catch (Exception e) {
            // fall through with normalizedData=null, preserving old per-item error behavior
        }
        for (String queryString: querySet) {
            try {
                res = evaluateOnStringQuerySet(normalizedData, queryString.trim());
            } catch (Exception e) {
                res = false;
            }
            result = result || res;
        }
        return result;
    }

    // data is expected pre-normalized (trimmed + lowercased) by the caller now - see isValid above.
    public Boolean evaluateOnStringQuerySet(String data, String query) {
        return !data.contains(query.toLowerCase());
    }
}
