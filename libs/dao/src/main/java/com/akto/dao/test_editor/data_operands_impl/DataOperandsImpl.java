package com.akto.dao.test_editor.data_operands_impl;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.TestConfig;

public abstract class DataOperandsImpl {
    
    public abstract Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest);
    
}
