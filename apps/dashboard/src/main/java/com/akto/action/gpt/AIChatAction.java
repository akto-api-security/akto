package com.akto.action.gpt;

import com.akto.action.UserAction;
import com.akto.dto.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.struts2.ServletActionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Chat Action for conversational AI responses
 *
 * Handles POST requests to /api/ai/chat with streaming support
 */
public class AIChatAction extends UserAction {

    private static final Logger logger = LoggerFactory.getLogger(AIChatAction.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // OpenAI API configuration
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    // Request parameters
    private List<Map<String, String>> messages;
    private String model = "gpt-4o-mini";
    private boolean webSearch = false;

    /**
     * Main action method to handle AI chat requests
     */
    public String chat() {
        try {
            // Validate API key
            if (OPENAI_API_KEY == null || OPENAI_API_KEY.isEmpty()) {
                logger.error("OPENAI_API_KEY environment variable not set");
                return sendError("OpenAI API key not configured. Please set OPENAI_API_KEY environment variable.");
            }

            // Validate request
            if (messages == null || messages.isEmpty()) {
                return sendError("Messages are required");
            }

            // Get authenticated user
            User sUser = getSUser();
            if (sUser == null) {
                return sendError("Unauthorized");
            }

            logger.info("AI chat request from user: {} with {} messages",
                       sUser.getLogin(), messages.size());

            // Build system prompt with Akto context
            String systemPrompt = buildSystemPrompt(sUser);

            // Call OpenAI API and stream response
            streamOpenAIResponse(systemPrompt);

            return null; // We handle the response directly

        } catch (Exception e) {
            logger.error("Error in AI chat", e);
            return sendError("Failed to process request: " + e.getMessage());
        }
    }

    /**
     * Build system prompt with Akto-specific context
     */
    private String buildSystemPrompt(User user) {
        return "You are an AI assistant for Akto, an API security platform.\n\n" +
               "Your role:\n" +
               "- Help users understand API security concepts\n" +
               "- Explain Akto features like API discovery, testing, and threat detection\n" +
               "- Provide guidance on risk assessment and remediation\n" +
               "- Answer questions about collections, endpoints, and vulnerabilities\n\n" +
               "Guidelines:\n" +
               "- Be concise and clear (aim for 2-3 sentences unless more detail is needed)\n" +
               "- Use bullet points for lists\n" +
               "- Stay focused on API security topics\n" +
               "- If unsure, suggest checking Akto documentation at https://docs.akto.io\n\n" +
               "Current Akto Features:\n" +
               "- API Discovery: Automatic detection of APIs from traffic\n" +
               "- Security Testing: Automated vulnerability scanning (OWASP API Top 10)\n" +
               "- Risk Scoring: Prioritized threat assessment\n" +
               "- Collections: Grouped API endpoints by domain/service\n" +
               "- Threat Detection: Real-time security monitoring\n" +
               "- Test Editor: Custom security test creation\n\n" +
               "User: " + user.getLogin();
    }

    /**
     * Stream response from OpenAI API
     */
    private void streamOpenAIResponse(String systemPrompt) {
        HttpServletResponse response = ServletActionContext.getResponse();
        PrintWriter writer = null;
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse httpResponse = null;

        try {
            // Set streaming headers
            response.setContentType("text/plain; charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            response.setHeader("Transfer-Encoding", "chunked");

            writer = response.getWriter();

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("messages", buildMessagesWithSystem(systemPrompt));
            requestBody.put("stream", true);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 1000);

            String requestJson = objectMapper.writeValueAsString(requestBody);

            // Create HTTP request
            HttpPost httpPost = new HttpPost(OPENAI_API_URL);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + OPENAI_API_KEY);
            httpPost.setEntity(new StringEntity(requestJson, "UTF-8"));

            // Execute request
            httpClient = HttpClients.createDefault();
            httpResponse = httpClient.execute(httpPost);

            // Check status
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                HttpEntity entity = httpResponse.getEntity();
                String errorBody = entity != null ? EntityUtils.toString(entity) : "Unknown error";
                logger.error("OpenAI API error: {} - {}", statusCode, errorBody);
                writer.write("0:\"Sorry, I encountered an error. Please try again later.\"\n");
                writer.flush();
                return;
            }

            // Stream response
            HttpEntity entity = httpResponse.getEntity();
            if (entity != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entity.getContent(), "UTF-8"))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();

                            if ("[DONE]".equals(data)) {
                                break;
                            }

                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> chunk = objectMapper.readValue(data, Map.class);

                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> choices =
                                    (List<Map<String, Object>>) chunk.get("choices");

                                if (choices != null && !choices.isEmpty()) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> delta =
                                        (Map<String, Object>) choices.get(0).get("delta");

                                    if (delta != null && delta.containsKey("content")) {
                                        String content = (String) delta.get("content");
                                        if (content != null && !content.isEmpty()) {
                                            // Escape quotes for frontend parsing
                                            String escaped = content
                                                .replace("\\", "\\\\")
                                                .replace("\"", "\\\"")
                                                .replace("\n", "\\n")
                                                .replace("\r", "");

                                            writer.write("0:\"" + escaped + "\"\n");
                                            writer.flush();
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                logger.debug("Error parsing chunk: {}", e.getMessage());
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error streaming OpenAI response", e);
            if (writer != null) {
                try {
                    writer.write("0:\"An error occurred. Please try again later.\"\n");
                    writer.flush();
                } catch (Exception ex) {
                    logger.error("Error writing error message", ex);
                }
            }
        } finally {
            // Clean up resources
            try {
                if (writer != null) {
                    writer.close();
                }
                if (httpResponse != null) {
                    httpResponse.close();
                }
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (Exception e) {
                logger.error("Error closing resources", e);
            }
        }
    }

    /**
     * Build messages array with system prompt
     */
    private List<Map<String, String>> buildMessagesWithSystem(String systemPrompt) {
        List<Map<String, String>> allMessages = new ArrayList<>();

        // Add system message
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        allMessages.add(systemMessage);

        // Add user messages
        if (messages != null) {
            allMessages.addAll(messages);
        }

        return allMessages;
    }

    /**
     * Send error response
     */
    private String sendError(String message) {
        try {
            HttpServletResponse response = ServletActionContext.getResponse();
            response.setContentType("application/json");
            response.setStatus(500);

            PrintWriter writer = response.getWriter();
            writer.write("{\"error\": \"" + message.replace("\"", "\\\"") + "\"}");
            writer.flush();
            writer.close();
        } catch (Exception e) {
            logger.error("Error sending error response", e);
        }
        return null;
    }

    // Getters and Setters
    public List<Map<String, String>> getMessages() {
        return messages;
    }

    public void setMessages(List<Map<String, String>> messages) {
        this.messages = messages;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isWebSearch() {
        return webSearch;
    }

    public void setWebSearch(boolean webSearch) {
        this.webSearch = webSearch;
    }
}
