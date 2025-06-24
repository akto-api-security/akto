package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;

public class TestFilterModifier extends TestExecutorModifier {

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String request = queryData.getString(_REQUEST);
        String operation = queryData.getString(_OPERATION);
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are a API request expert.\n\n")
                .append("You are given an API request :\n")
                .append("----------------------------------------\n")
                .append(request)
                .append("\n----------------------------------------\n\n")
                .append("Your task:\n")
                .append("- Give out the data for filter operation based on the idea given.\n")
                .append("- The operation is: ")
                .append(operation)
                .append("\n----------------------------------------\n\n")
                .append("Strict rules:\n")
                .append("- If you're unable to do the operation, return only this word: " + _NOT_FOUND + "\n\n")
                .append("Expected Output:\n")
                .append("- A JSON object with operation and value data.\n")
                .append("- Example: { \"contains_either\": [\"param1\"] }\n")
                .append("- Example: { \"not_contains_either\": [\"param1\"] }\n")
                .append("- Example: { \"contains_all\": [\"param1\"] }\n")
                .append("- Example: { \"contains_jwt\": true }\n")
                .append("- Example: { \"not_contains\": [\"param1\"] }\n")
                .append("- Example: { \"regex\": \".*\" }\n")
                .append("- Example: { \"gt\": 200 }\n")
                .append("- Return ONLY the JSON or " + _NOT_FOUND + " — nothing else.");
        return promptBuilder.toString();
    }

}
