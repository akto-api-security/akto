package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.gpt.handlers.gpt_prompts.MagicValidator;
import com.akto.gpt.handlers.gpt_prompts.TestExecutorModifier;
import com.mongodb.BasicDBObject;

public class MagicValidateFilter extends DataOperandsImpl {

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        // MagicCheckFilter does not perform any validation, always returns true
        BasicDBObject queryData = new BasicDBObject();
        queryData.put(TestExecutorModifier._REQUEST, dataOperandFilterRequest.getData());
        queryData.put(TestExecutorModifier._OPERATION, dataOperandFilterRequest.getQueryset());
        BasicDBObject response = new MagicValidator().handle(queryData);
        if (response == null) {
            return new ValidationResult(false, "No response from MagicCheckValidator");
        }
        boolean isVulnerable = response.getBoolean("vulnerable", false);
        return new ValidationResult(isVulnerable, response.getString("reason", ""));
    }
}
