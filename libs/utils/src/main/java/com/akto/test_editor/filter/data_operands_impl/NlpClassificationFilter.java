package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import java.util.List;
import java.util.Map;

public class NlpClassificationFilter extends DataOperandsImpl {

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        // TODO: MCP - Implement actual NLP classification logic here
        // For now, this is a demo placeholder that accepts all but validates nothing
        // Expected format in queryset: [category, threshold] like ["hate_speech", "0.65"]
        // OR nested format: {category: "hate_speech", gt: 0.65}
        
        // Get the data (payload content) - similar to magic implementation
        Object data = dataOperandFilterRequest.getData();
        Object querySetObj = dataOperandFilterRequest.getQueryset();
        
        if (data == null) {
            return new ValidationResult(false, "No data provided for NLP classification");
        }
        
        String dataString = data.toString();
        
        // TODO: MCP - Call actual NLP classification service on the dataString
        // The dataString should be analyzed for hate speech, toxicity, etc.
        // For demo purposes, always return false to indicate feature not implemented
        
        if (querySetObj instanceof List) {
            List<?> queryList = (List<?>) querySetObj;
            if (queryList.size() >= 2) {
                String category = String.valueOf(queryList.get(0));
                try {
                    double threshold = Double.parseDouble(String.valueOf(queryList.get(1)));
                    return new ValidationResult(false, "NLP Classification not yet implemented - demo mode. " +
                        "Category: " + category + ", Threshold: " + threshold + 
                        ", Data length: " + dataString.length());
                } catch (NumberFormatException e) {
                    return new ValidationResult(false, "Invalid threshold format in NLP classification");
                }
            }
        } else if (querySetObj instanceof Map) {
            Map<?, ?> queryMap = (Map<?, ?>) querySetObj;
            String category = String.valueOf(queryMap.get("category"));
            Object gtValue = queryMap.get("gt");
            Object ltValue = queryMap.get("lt");
            Object gteValue = queryMap.get("gte");
            Object lteValue = queryMap.get("lte");
            
            String operator = "unknown";
            double threshold = 0.0;
            
            if (gtValue != null) {
                operator = "gt";
                threshold = Double.parseDouble(String.valueOf(gtValue));
            } else if (ltValue != null) {
                operator = "lt";
                threshold = Double.parseDouble(String.valueOf(ltValue));
            } else if (gteValue != null) {
                operator = "gte";
                threshold = Double.parseDouble(String.valueOf(gteValue));
            } else if (lteValue != null) {
                operator = "lte";
                threshold = Double.parseDouble(String.valueOf(lteValue));
            }
            
            return new ValidationResult(false, "NLP Classification not yet implemented - demo mode. " +
                "Category: " + category + ", Operator: " + operator + ", Threshold: " + threshold + 
                ", Data length: " + dataString.length());
        }
        
        return new ValidationResult(false, "Invalid NLP classification parameters format");
    }
}
