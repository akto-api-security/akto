package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.gpt.handlers.gpt_prompts.MagicValidator;
import com.akto.gpt.handlers.gpt_prompts.TestExecutorModifier;
import com.mongodb.BasicDBObject;
import java.util.List;

public class MagicValidateFilter extends DataOperandsImpl {

    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        BasicDBObject queryData = new BasicDBObject();
        queryData.put(TestExecutorModifier._REQUEST, dataOperandFilterRequest.getData());

        boolean isVulnerable = false;
        for (String key : (List<String>)dataOperandFilterRequest.getQueryset()) {
            queryData.put(TestExecutorModifier._OPERATION, key);
            BasicDBObject response = new MagicValidator().handle(queryData);
            if (response != null) {
                isVulnerable = isVulnerable || response.getBoolean("vulnerable", false);
            }
        }
        return isVulnerable;
    }
}
