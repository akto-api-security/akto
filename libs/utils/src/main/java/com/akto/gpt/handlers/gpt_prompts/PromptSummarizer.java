package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.List;

public class PromptSummarizer {
    private static final int CHUNK_SIZE = 3000; // Safe chunk size

    public String summarizeWithLLM(String longText) {
        if (longText == null || longText.isEmpty()) {
            return "";
        }

        if (longText.length() <= CHUNK_SIZE) {
            return longText;
        }

        List<String> chunks = splitIntoChunks(longText);
        List<String> summaries = new ArrayList<>();

        for (String chunk : chunks) {
            String prompt = buildSummarizationPrompt(chunk);
            try {
                String partialSummary = new PromptHandler() {
                    @Override
                    protected void validate(com.mongodb.BasicDBObject q) {
                    }

                    @Override
                    protected String getPrompt(com.mongodb.BasicDBObject q) {
                        return prompt;
                    }

                    @Override
                    protected BasicDBObject processResponse(String raw) {
                        String clean = PromptHandler.processOutput(raw);
                        return new BasicDBObject("summary", clean);
                    }
                }.handle(new BasicDBObject()).getString("summary");

                summaries.add(partialSummary);
            } catch (Exception e) {
                //summaries.add("[Error summarizing chunk: " + e.getMessage() + "]");
            }
        }

        return String.join("\n\n", summaries);
    }

    private static List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i += CHUNK_SIZE) {
            chunks.add(text.substring(i, Math.min(length, i + CHUNK_SIZE)));
        }
        return chunks;
    }

    private static String buildSummarizationPrompt(String chunk) {
        return "Summarize the following data. Final output must be a text with max 100 words. Remove boilerplate.\n\n"
            + chunk;
    }
}
