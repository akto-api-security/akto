package com.akto.gen_ai;

import java.util.Arrays;
import java.util.List;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class LLMRule {

    String baseProvider;
    List<String> baseEndpoints;
    List<String> endpoints;
    List<String> requestBodyKeywords;
    final static int MAX_PAYLOAD_LENGTH_CHECK = 10000;

    public boolean matchesProvider(HttpResponseParams responseParams) {
        HttpRequestParams requestParams = responseParams.getRequestParams();
        String url = requestParams.getUrl();
        if (url == null) {
            return false;
        }
        /*
         * If the exact baseEndpoints is present,
         * directly return true, because the service is definitely from that provider
         */
        if (baseEndpoints != null) {
            for (String endpoint : baseEndpoints) {
                if (url.contains(endpoint)) {
                    return true;
                }
            }
        }
        /*
         * If the exact baseEndpoint is not present [ self-hosted/custom models ]
         * check if the url matches any of the possible urls
         * [ generally self-hosted models like ollama have specific urls ]
         * but that doesn't directly imply that the service is from that provider
         * So, we'll check for request body keywords in the request body as well
         */
        boolean urlPossibleMatch = false;
        if (endpoints != null) {
            for (String endpoint : endpoints) {
                if (url.contains(endpoint)) {
                    urlPossibleMatch = true;
                    break;
                }
            }
        }

        boolean isJsonRequest = requestParams.isJsonRequest();
        String requestPayload = requestParams.getPayload();

        boolean requestBodyKeywordMatch = false;
        if (isJsonRequest &&
                urlPossibleMatch &&
                requestPayload != null
                && requestPayload.length() < MAX_PAYLOAD_LENGTH_CHECK) {
            if (requestBodyKeywords != null) {
                for (String keyword : requestBodyKeywords) {
                    if (requestPayload.contains(keyword)) {
                        requestBodyKeywordMatch = true;
                        break;
                    }
                }
            }
        }

        return requestBodyKeywordMatch;
    }

    final static List<LLMRule> standardLLMRules = Arrays.asList(
            new LLMRule(
                    "OpenAI",
                    Arrays.asList("api.openai.com/v1"),
                    null, null),
            new LLMRule(
                    "Azure OpenAI",
                    Arrays.asList("openai.azure.com"),
                    null, null),
            new LLMRule(
                    "Anthropic",
                    Arrays.asList("api.anthropic.com/v1"),
                    null, null),
            new LLMRule(
                    "Cohere",
                    Arrays.asList("api.cohere.ai"),
                    null, null),
            new LLMRule(
                    "AI21",
                    Arrays.asList("api.ai21.com/studio/v1"),
                    null, null),
            new LLMRule(
                    "Google",
                    Arrays.asList("generativelanguage.googleapis.com"),
                    null, null),
            new LLMRule(
                    "Hugging Face",
                    Arrays.asList("api-inference.huggingface.co/models"),
                    null, null),
            new LLMRule(
                    "IBM",
                    Arrays.asList("ml.cloud.ibm.com"),
                    null, null),
            new LLMRule(
                    "Replicate",
                    Arrays.asList("api.replicate.com"),
                    null, null),
            new LLMRule(
                    "Ollama",
                    Arrays.asList(),
                    Arrays.asList("/api/generate","/api/chat"), 
                    Arrays.asList("\"model\"")),
            new LLMRule(
                    "Custom",
                    Arrays.asList(),
                    Arrays.asList("/v1/completions","/v1/chat/completions"), 
                    Arrays.asList("\"model\""))

    );

    public static List<LLMRule> fetchStandardLLMRules() {
        return standardLLMRules;
    }

}
