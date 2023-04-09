package com.akto.dao.test_editor.data_operands_impl;

import java.util.ArrayList;
import java.util.List;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public class ContainsAllFilter extends DataOperandsImpl {
    
    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = true;
        Boolean res;
        List<String> querySet = new ArrayList<>();
        String data;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return result;
        }
        for (String queryString: querySet) {
            res = evaluateOnStringQuerySet(data, queryString);
            result = result && res;
        }
        return result;
    }

    public Boolean evaluateOnListQuerySet(String data, List<String> querySet) {
        Boolean result = true;
        for (String queryString: querySet) {
            result = result && evaluateOnStringQuerySet(data, queryString);
        }
        return result;
    }

    public Boolean evaluateOnStringQuerySet(String data, String query) {
        return data.contains(query);
    }

    // Boolean result = false;
        // Boolean res;
        // List<String> querySet = new ArrayList<>();
        // String data;
        // try {
        //     querySet = (List<String>) dataOperandFilterRequest.getQueryset();
        //     data = (String) dataOperandFilterRequest.getData();
        // } catch(Exception e) {
        //     return result;
        // }
        // for (String queryString: querySet) {
        //     res = evaluateOnStringQuerySet(data, queryString);
        //     result = result || res;
        // }
        // return result;   

        // Boolean result = true;
        // Object data = dataOperandFilterRequest.getData();
        // Object querySet = dataOperandFilterRequest.getQueryset();

        // if (dataOperandFilterRequest.getConcernedSubProperty() == null) {
        //     if (data instanceof String) {
        //         if (querySet instanceof ArrayList) {
        //             String dataStr = (String) data;
        //             ArrayList<String> queryList = (ArrayList) querySet;
        //             evaluateOnListQuerySet(dataStr, queryList);
        //         }
        //     }
        // }

        // if (dataOperandFilterRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
        //     List<String> matchingKeys = new ArrayList<>();
        //     ArrayList<String> queryList = (ArrayList) querySet;
        //     if (data instanceof BasicDBObject) {
        //         BasicDBObject dataObj = (BasicDBObject) data;
        //         for (String key: dataObj.keySet()) {
        //             Boolean res = evaluateOnListQuerySet(key, queryList);
        //             result = result || res;
        //             if (res) {
        //                 matchingKeys.add(key);
        //             }
        //         }
        //         return new DataOperandsFilterResponse(result, matchingKeys);
        //     }
        //     else if (data instanceof Map) {
        //         Map<String, List<String>> dataObj = (Map) data;
        //         for (String key: dataObj.keySet()) {
        //             Boolean res = evaluateOnListQuerySet(key, queryList);
        //             result = result || res;
        //             if (res) {
        //                 matchingKeys.add(key);
        //             }
        //         }
        //         return new DataOperandsFilterResponse(result, matchingKeys);
        //     }
        // }

        // if (dataOperandFilterRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
        //     List<String> matchingKeys = dataOperandFilterRequest.getMatchingKeySet();
        //     ArrayList<String> queryList = (ArrayList) querySet;
        //     if (data instanceof BasicDBObject) {
        //         BasicDBObject dataObj = (BasicDBObject) data;
        //         for (String key: dataObj.keySet()) {
        //             if (matchingKeys == null || !matchingKeys.contains(key)) {
        //                 continue;
        //             }
        //             Boolean res = evaluateOnListQuerySet((String) dataObj.get(key), queryList);
        //             result = result || res;
        //             if (res) {
        //                 matchingKeys.add(key);
        //             }
        //         }
        //         return new DataOperandsFilterResponse(result, matchingKeys);
        //     }
        //     else if (data instanceof Map) {
        //         Map<String, List<String>> dataObj = (Map) data;
        //         for (String key: dataObj.keySet()) {
        //             if (matchingKeys == null || !matchingKeys.contains(key)) {
        //                 continue;
        //             }
        //             for (String val: dataObj.get(key)) {
        //                 Boolean res = evaluateOnListQuerySet(val, queryList);
        //                 result = result || res;
        //             }
        //         }
        //         return new DataOperandsFilterResponse(result, matchingKeys);
        //     }
        // }

        // return new DataOperandsFilterResponse(false, null);
}
