package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public abstract class DataOperandsImpl {
    public abstract ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest);
    
}
