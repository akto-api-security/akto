package com.akto.test_editor.filter.data_operands_impl;

import com.akto.billing.UsageMetricUtils;
import com.akto.data_actor.DataActor;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.gpt.handlers.gpt_prompts.MagicValidator;
import com.akto.gpt.handlers.gpt_prompts.TestExecutorModifier;
import com.mongodb.BasicDBObject;
import java.util.List;

public class MagicValidateFilter extends DataOperandsImpl {

    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(DataActor.actualAccountId,
            TestExecutorModifier._AKTO_GPT_AI);
        if (!featureAccess.getIsGranted()) {
            return false;
        }
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
