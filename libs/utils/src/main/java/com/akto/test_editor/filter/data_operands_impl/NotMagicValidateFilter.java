package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public class NotMagicValidateFilter extends MagicValidateFilter {

    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        return !super.isValid(dataOperandFilterRequest);
    }
}
