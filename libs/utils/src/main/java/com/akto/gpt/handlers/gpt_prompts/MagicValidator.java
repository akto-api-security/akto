package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;

public class MagicValidator extends TestExecutorModifier {

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        return
            "You are a strict security validation engine. "
                + "Your task is to determine if the provided API RESPONSE matches the given CONTEXT criteria. "
                + "You must validate only according to the CONTEXT — do not consider any unrelated vulnerabilities, threats, or patterns.\n\n"
                + "RULES:\n"
                + "1. Read the CONTEXT carefully and identify the exact validation condition(s) it describes.\n"
                + "2. Examine the RESPONSE strictly against those condition(s) only.\n"
                + "3. If the RESPONSE matches the CONTEXT condition, output:\n"
                + "   {\"vulnerable\": true, \"reason\": \"<proof from the response showing the match>\"}\n"
                + "4. If the RESPONSE does not match the CONTEXT condition, output:\n"
                + "   {\"vulnerable\": false, \"reason\": \"<proof from the response showing no match>\"}\n"
                + "5. The 'reason' must always be concise and fact-based, directly derived from the RESPONSE and relevant to the CONTEXT.\n"
                + "6. Output must be a single valid JSON object exactly in the above format.\n"
                + "7. Do not add extra text, formatting, or explanation outside the JSON.\n\n"
                + "INPUTS:\n"
                + "CONTEXT: \n"
                + queryData.get(TestExecutorModifier._OPERATION) + "\n"
                + "RESPONSE: \n"
                + queryData.get(TestExecutorModifier._REQUEST) + "\n\n"
                + "Now perform the validation and return the JSON verdict.";
    }
}
