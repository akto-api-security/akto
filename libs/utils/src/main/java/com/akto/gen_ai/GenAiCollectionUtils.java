package com.akto.gen_ai;

import com.akto.dto.HttpResponseParams;
import com.akto.util.Pair;

public class GenAiCollectionUtils {

    final static String _LLM = "LLM";
    final static String _SPACE = " ";
    public static Pair<Boolean, String> checkAndTagLLMCollection(HttpResponseParams responseParams) {

        for (LLMRule rule : LLMRule.fetchStandardLLMRules()) {
            if (rule.matchesProvider(responseParams)) {
                return new Pair<>(true, _LLM + _SPACE + rule.getBaseProvider());
            }
        }

        return new Pair<>(false, null);
    }

}
