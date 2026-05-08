package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import java.util.List;

public class CategoryFilter extends DataOperandsImpl {

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        // TODO: MCP - Implement actual category matching logic here
        // For now, this is a demo placeholder that accepts all but validates nothing
        // Expected format in queryset: ["category_name"] like ["hate_speech"]
        
        Object queryData = dataOperandFilterRequest.getQueryset();
        if (queryData instanceof List) {
            List<?> queryList = (List<?>) queryData;
            if (queryList.size() >= 1) {
                String category = String.valueOf(queryList.get(0));
                // TODO: MCP - Call actual category matching service
                // For demo purposes, always return false to indicate feature not implemented
                return new ValidationResult(false, "Category matching not yet implemented - demo mode. Category: " + category);
            }
        }
        
        return new ValidationResult(false, "Invalid category parameters format");
    }
}
