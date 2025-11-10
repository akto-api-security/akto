package com.akto.action.prompt_hardening;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.prompt_hardening.PromptHardeningYamlTemplateDao;
import com.akto.dto.test_editor.Category;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.gpt.handlers.gpt_prompts.PromptHardeningTestHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromptHardeningAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(PromptHardeningAction.class, LogDb.DASHBOARD);

    // Security: Maximum input lengths to prevent DoS attacks
    private static final int MAX_PROMPT_LENGTH = 50000;  // ~50KB for system prompts
    private static final int MAX_USER_INPUT_LENGTH = 10000;  // ~10KB for user inputs
    private static final int MAX_ATTACK_PATTERN_LENGTH = 5000;  // Per attack pattern
    private static final int MAX_ATTACK_PATTERNS_COUNT = 50;  // Max number of attack patterns
    private static final int MAX_TEMPLATE_CONTENT_LENGTH = 100000;  // ~100KB for templates
    private static final int MAX_VULNERABILITY_CONTEXT_LENGTH = 20000;  // ~20KB

    private Map<String, Object> promptsObj;
    private String content;
    private String templateId;
    private String category;
    private boolean inactive;
    
    // Fields for testSystemPrompt endpoint
    private String systemPrompt;
    private String userInput;
    private List<String> attackPatterns;
    private Map<String, Object> detectionRules;  // Parsed by frontend using js-yaml
    private Map<String, Object> testResult;
    
    // Fields for hardenSystemPrompt endpoint
    private String vulnerabilityContext;
    private String hardenedPrompt;

    /**
     * Fetches all prompt hardening templates and organizes them by category
     * Returns a structure similar to the frontend's expected format
     */
    public String fetchAllPrompts() {
        try {
            Map<String, List<YamlTemplate>> templatesByCategory = 
                PromptHardeningYamlTemplateDao.instance.fetchTemplatesByCategory();
            
            Map<String, Object> customPrompts = new HashMap<>();
            Map<String, Object> aktoPrompts = new HashMap<>();
            Map<String, Object> mapPromptToData = new HashMap<>();
            Map<String, String> mapIdtoPrompt = new HashMap<>();
            
            int totalCustomPrompts = 0;
            int totalAktoPrompts = 0;

            for (Map.Entry<String, List<YamlTemplate>> entry : templatesByCategory.entrySet()) {
                String categoryName = entry.getKey();
                List<YamlTemplate> templates = entry.getValue();
                
                List<Map<String, Object>> categoryTemplates = new ArrayList<>();
                
                for (YamlTemplate template : templates) {
                    // Create template item for the list
                    Map<String, Object> templateItem = new HashMap<>();
                    templateItem.put("label", template.getInfo() != null ? template.getInfo().getName() : template.getId());
                    templateItem.put("value", template.getId());
                    templateItem.put("category", categoryName);
                    templateItem.put("inactive", template.isInactive());
                    
                    categoryTemplates.add(templateItem);
                    
                    // Add to mapPromptToData
                    Map<String, Object> templateData = new HashMap<>();
                    templateData.put("content", template.getContent());
                    templateData.put("category", categoryName);
                    if (template.getInfo() != null) {
                        templateData.put("name", template.getInfo().getName());
                        templateData.put("description", template.getInfo().getDescription());
                        templateData.put("severity", template.getInfo().getSeverity());
                    }
                    
                    String promptName = template.getInfo() != null ? template.getInfo().getName() : template.getId();
                    mapPromptToData.put(promptName, templateData);
                    mapIdtoPrompt.put(template.getId(), promptName);
                }
                
                // Categorize as custom or akto based on source
                boolean isAktoTemplate = false;
                if (!templates.isEmpty()) {
                    YamlTemplate firstTemplate = templates.get(0);
                    isAktoTemplate = firstTemplate.getSource() == GlobalEnums.YamlTemplateSource.AKTO_TEMPLATES;
                }
                
                if (isAktoTemplate) {
                    aktoPrompts.put(categoryName, categoryTemplates);
                    totalAktoPrompts += categoryTemplates.size();
                } else {
                    customPrompts.put(categoryName, categoryTemplates);
                    totalCustomPrompts += categoryTemplates.size();
                }
            }
            
            // Build the response object
            promptsObj = new HashMap<>();
            promptsObj.put("customPrompts", customPrompts);
            promptsObj.put("aktoPrompts", aktoPrompts);
            promptsObj.put("mapPromptToData", mapPromptToData);
            promptsObj.put("mapIdtoPrompt", mapIdtoPrompt);
            promptsObj.put("totalCustomPrompts", totalCustomPrompts);
            promptsObj.put("totalAktoPrompts", totalAktoPrompts);
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.error("Failed to fetch prompt hardening templates", e);
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Validates and sanitizes input strings for security
     * @param input Input string to validate
     * @param fieldName Name of the field for error messages
     * @param maxLength Maximum allowed length
     * @throws Exception if validation fails
     */
    private void validateInput(String input, String fieldName, int maxLength) throws Exception {
        if (input == null || input.trim().isEmpty()) {
            throw new Exception(fieldName + " cannot be empty");
        }
        
        if (input.length() > maxLength) {
            throw new Exception(fieldName + " exceeds maximum length of " + maxLength + " characters");
        }
    }

    /**
     * Sanitizes error messages to prevent information leakage
     * @param error Original error message
     * @return Sanitized error message safe for frontend display
     */
    private String sanitizeErrorMessage(String error) {
        if (error == null) {
            return "An unexpected error occurred. Please try again.";
        }
        
        loggerMaker.error("Prompt hardening error occurred: " + error);
        
        // Remove sensitive information patterns
        String sanitized = error;
        
        // Remove file paths
        sanitized = sanitized.replaceAll("(/[^\\s]+)+", "[path]");
        
        // Remove API keys or tokens (common patterns)
        sanitized = sanitized.replaceAll("(?i)(api[_-]?key|token|secret|password)[\"']?\\s*[:=]\\s*[\"']?[a-zA-Z0-9_-]+", "$1=[REDACTED]");
        
        // Remove stack trace information
        if (sanitized.contains("at ") && sanitized.contains(".java:")) {
            sanitized = "An internal error occurred. Please contact support if the issue persists.";
        }
        
        // Provide generic message for common LLM errors
        if (sanitized.toLowerCase().contains("openai") || 
            sanitized.toLowerCase().contains("azure") ||
            sanitized.toLowerCase().contains("llm") ||
            sanitized.toLowerCase().contains("rate limit")) {
            return "AI service temporarily unavailable. Please try again in a moment.";
        }
        
        return sanitized;
    }

    /**
     * Saves or updates a prompt hardening template
     */
    public String savePrompt() {
        try {
            validateInput(content, "Content", MAX_TEMPLATE_CONTENT_LENGTH);
            validateInput(templateId, "Template ID", 255);
            
            if (category != null && category.length() > 255) {
                throw new Exception("Category name exceeds maximum length of 255 characters");
            }

            int now = Context.now();
            String author = getSUser() != null ? getSUser().getLogin() : "system";
            
            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.set(YamlTemplate.CONTENT, content));
            updates.add(Updates.set(YamlTemplate.UPDATED_AT, now));
            updates.add(Updates.set(YamlTemplate.AUTHOR, author));
            updates.add(Updates.set(YamlTemplate.HASH, content.hashCode()));
            
            if (category != null && !category.isEmpty()) {
                Category cat = new Category(category, category, category);
                updates.add(Updates.set(YamlTemplate.INFO + ".category", cat));
            }
            
            updates.add(Updates.set(YamlTemplate.INACTIVE, inactive));
            
            // Check if template exists
            YamlTemplate existing = PromptHardeningYamlTemplateDao.instance.findOne(
                Filters.eq(Constants.ID, templateId)
            );
            
            if (existing == null) {
                // Create new template
                YamlTemplate newTemplate = new YamlTemplate();
                newTemplate.setId(templateId);
                newTemplate.setContent(content);
                newTemplate.setAuthor(author);
                newTemplate.setCreatedAt(now);
                newTemplate.setUpdatedAt(now);
                newTemplate.setInactive(inactive);
                newTemplate.setSource(GlobalEnums.YamlTemplateSource.CUSTOM);
                
                PromptHardeningYamlTemplateDao.instance.insertOne(newTemplate);
            } else {
                // Update existing template
                PromptHardeningYamlTemplateDao.instance.updateOne(
                    Filters.eq(Constants.ID, templateId),
                    Updates.combine(updates)
                );
            }
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.error("Failed to save prompt hardening template", e);
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Deletes a prompt hardening template
     */
    public String deletePrompt() {
        try {
            if (templateId == null || templateId.trim().isEmpty()) {
                throw new Exception("Template ID cannot be empty");
            }

            PromptHardeningYamlTemplateDao.instance.getMCollection().deleteOne(
                Filters.eq(Constants.ID, templateId)
            );
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.error("Failed to delete prompt hardening template", e);
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Converts nested Map structures to BasicDBObject recursively
     * This is needed because JSON deserialization creates HashMap objects
     */
    private BasicDBObject convertToBasicDBObject(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        
        BasicDBObject result = new BasicDBObject();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), convertValue(entry.getValue()));
        }
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private Object convertValue(Object value) {
        if (value instanceof Map) {
            return convertToBasicDBObject((Map<String, Object>) value);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> convertedList = new ArrayList<>();
            for (Object item : list) {
                convertedList.add(convertValue(item));
            }
            return convertedList;
        }
        return value;
    }

    private String extractPromptTemplate(BasicDBObject detectionRulesObj) {
        if (detectionRulesObj == null || !detectionRulesObj.containsKey("matchers")) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<BasicDBObject> matchers = (List<BasicDBObject>) detectionRulesObj.get("matchers");
        if (matchers == null) {
            return null;
        }

        for (BasicDBObject matcher : matchers) {
            if (matcher == null) {
                continue;
            }
            String type = matcher.getString("type");
            if (type != null && type.equalsIgnoreCase("prompt")) {
                String promptTemplate = matcher.getString("prompt");
                if (promptTemplate != null && !promptTemplate.trim().isEmpty()) {
                    return promptTemplate;
                }
            }
        }

        return null;
    }

    /**
     * Toggles the inactive status of a prompt template
     */
    public String togglePromptStatus() {
        try {
            if (templateId == null || templateId.trim().isEmpty()) {
                throw new Exception("Template ID cannot be empty");
            }

            YamlTemplate template = PromptHardeningYamlTemplateDao.instance.findOne(
                Filters.eq(Constants.ID, templateId)
            );
            
            if (template == null) {
                throw new Exception("Template not found");
            }

            PromptHardeningYamlTemplateDao.instance.updateOne(
                Filters.eq(Constants.ID, templateId),
                Updates.set(YamlTemplate.INACTIVE, !template.isInactive())
            );
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.error("Failed to toggle prompt status", e);
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Tests a system prompt against attack patterns using Azure OpenAI
     * NEW FLOW:
     * 1. User must provide userInput (either manually or via generateMaliciousUserInput button)
     * 2. Simulate AI agent response with the system prompt and user input
     * 3. Use LLM to analyze if the response is vulnerable (replaces regex detection)
     */
    public String testSystemPrompt() {
        try {
            // Validate system prompt and user input
            validateInput(systemPrompt, "System prompt", MAX_PROMPT_LENGTH);
            validateInput(userInput, "User input", MAX_USER_INPUT_LENGTH);
            
            // Validate attack patterns - they are required for LLM-based detection
            if (attackPatterns == null || attackPatterns.isEmpty()) {
                throw new Exception("Attack patterns are required for vulnerability testing");
            }
            
            if (attackPatterns.size() > MAX_ATTACK_PATTERNS_COUNT) {
                throw new Exception("Number of attack patterns exceeds maximum of " + MAX_ATTACK_PATTERNS_COUNT);
            }
            
            for (int i = 0; i < attackPatterns.size(); i++) {
                String pattern = attackPatterns.get(i);
                if (pattern != null && pattern.length() > MAX_ATTACK_PATTERN_LENGTH) {
                    throw new Exception("Attack pattern " + (i + 1) + " exceeds maximum length of " + MAX_ATTACK_PATTERN_LENGTH);
                }
            }
            
            // Convert detection rules if provided (used for custom LLM prompt templates or legacy regex)
            BasicDBObject detectionRulesObj = null;
            if (detectionRules != null && !detectionRules.isEmpty()) {
                detectionRulesObj = convertToBasicDBObject(detectionRules);
            }
            
            // Check rate limit before making LLM calls
            if (!checkRateLimit("testSystemPrompt")) {
                throw new Exception("Rate limit exceeded. Please try again in a few moments.");
            }
            
            // STEP 1: Simulate AI agent response with system prompt and user input
            BasicDBObject queryData = new BasicDBObject();
            queryData.put("systemPrompt", systemPrompt);
            queryData.put("userInput", userInput);
            
            PromptHardeningTestHandler handler = new PromptHardeningTestHandler();
            BasicDBObject response = handler.handle(queryData);
            
            // Check for errors and sanitize
            if (response.containsKey("error")) {
                String rawError = response.getString("error");
                loggerMaker.error("LLM test error: " + rawError);
                throw new Exception(sanitizeErrorMessage(rawError));
            }
            
            // Get the agent response
            String agentResponse = response.getString("agentResponse");
            
            // STEP 2: Use LLM to analyze if response is vulnerable (NEW APPROACH)
            BasicDBObject analysis;
            
            String promptTemplate = extractPromptTemplate(detectionRulesObj);
            if (promptTemplate != null) {
                analysis = PromptHardeningTestHandler.analyzeVulnerabilityWithLLM(
                    agentResponse,
                    attackPatterns,
                    userInput,
                    promptTemplate
                );
            } else if (detectionRulesObj != null) {
                // Legacy path: Use regex-based detection (will be removed in future)
                analysis = PromptHardeningTestHandler.analyzeVulnerability(
                    agentResponse, 
                    detectionRulesObj
                );
            } else {
                // NEW PATH: Use LLM-based detection (recommended)
                analysis = PromptHardeningTestHandler.analyzeVulnerabilityWithLLM(
                    agentResponse,
                    attackPatterns,
                    userInput,
                    null
                );
            }
            
            // Build result
            testResult = new HashMap<>();
            testResult.put("text", agentResponse);
            testResult.put("isSafe", analysis.getBoolean("isSafe"));
            testResult.put("safetyMessage", analysis.getString("safetyMessage"));
            testResult.put("analysisDetail", analysis.getString("analysisDetail"));
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.error("Failed to test system prompt", e);
            // Use sanitized error message for user-facing errors
            String sanitizedError = sanitizeErrorMessage(e.getMessage());
            addActionError(sanitizedError);
            return ERROR.toUpperCase();
        }
    }

    /**
     * Simple in-memory rate limiter using a sliding window approach
     * Maps user ID + operation to list of timestamps
     */
    private static final Map<String, List<Long>> rateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int RATE_LIMIT_WINDOW_SECONDS = 60;  // 1 minute window
    private static final int MAX_REQUESTS_PER_WINDOW = 10;  // 10 requests per minute
    
    /**
     * Checks if the current user has exceeded the rate limit for the given operation
     * @param operation The operation name (e.g., "testSystemPrompt", "hardenSystemPrompt")
     * @return true if request is allowed, false if rate limit exceeded
     */
    private boolean checkRateLimit(String operation) {
        try {
            String userId = getSUser() != null ? String.valueOf(getSUser().getId()) : "anonymous";
            String key = userId + ":" + operation;
            long now = System.currentTimeMillis();
            long windowStart = now - (RATE_LIMIT_WINDOW_SECONDS * 1000L);
            
            // Get or create timestamp list for this user+operation
            rateLimitMap.putIfAbsent(key, new java.util.concurrent.CopyOnWriteArrayList<>());
            List<Long> timestamps = rateLimitMap.get(key);
            
            // Remove timestamps outside the window
            timestamps.removeIf(timestamp -> timestamp < windowStart);
            
            // Check if limit exceeded
            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                return false;
            }
            
            // Add current timestamp
            timestamps.add(now);
            
            return true;
        } catch (Exception e) {
            // If rate limiting fails, log but allow the request
            loggerMaker.error("Rate limit check failed", e);
            return true;
        }
    }

    /**
     * Generates malicious user input from attack patterns using LLM
     * This is called when user clicks "Auto-generate prompt" button
     */
    public String generateMaliciousUserInput() {
        try {
            // Validate attack patterns
            if (attackPatterns == null || attackPatterns.isEmpty()) {
                throw new Exception("Attack patterns are required to generate malicious input");
            }
            
            if (attackPatterns.size() > MAX_ATTACK_PATTERNS_COUNT) {
                throw new Exception("Number of attack patterns exceeds maximum of " + MAX_ATTACK_PATTERNS_COUNT);
            }
            
            for (int i = 0; i < attackPatterns.size(); i++) {
                String pattern = attackPatterns.get(i);
                if (pattern != null && pattern.length() > MAX_ATTACK_PATTERN_LENGTH) {
                    throw new Exception("Attack pattern " + (i + 1) + " exceeds maximum length of " + MAX_ATTACK_PATTERN_LENGTH);
                }
            }
            
            // Check rate limit before making LLM call
            if (!checkRateLimit("generateMaliciousUserInput")) {
                throw new Exception("Rate limit exceeded. Please try again in a few moments.");
            }
            
            // Generate malicious user input from attack patterns
            String generatedInput = PromptHardeningTestHandler.generateMaliciousUserInput(attackPatterns);
            
            // Return the generated input
            userInput = generatedInput;
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.error("Failed to generate malicious user input", e);
            // Use sanitized error message for user-facing errors
            String sanitizedError = sanitizeErrorMessage(e.getMessage());
            addActionError(sanitizedError);
            return ERROR.toUpperCase();
        }
    }

    /**
     * Hardens a system prompt by adding security measures using LLM
     * Takes a vulnerable prompt and returns a hardened version
     */
    public String hardenSystemPrompt() {
        try {
            // Validate inputs with length checks
            validateInput(systemPrompt, "System prompt", MAX_PROMPT_LENGTH);
            
            if (vulnerabilityContext != null && vulnerabilityContext.length() > MAX_VULNERABILITY_CONTEXT_LENGTH) {
                throw new Exception("Vulnerability context exceeds maximum length of " + MAX_VULNERABILITY_CONTEXT_LENGTH);
            }
            
            // Check rate limit before making LLM call
            if (!checkRateLimit("hardenSystemPrompt")) {
                throw new Exception("Rate limit exceeded. Please try again in a few moments.");
            }
            
            // Call the handler to harden the prompt
            hardenedPrompt = PromptHardeningTestHandler.hardenSystemPrompt(
                systemPrompt,
                vulnerabilityContext  // Can be null
            );
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.error("Failed to harden system prompt", e);
            // Use sanitized error message for user-facing errors
            String sanitizedError = sanitizeErrorMessage(e.getMessage());
            addActionError(sanitizedError);
            return ERROR.toUpperCase();
        }
    }
}

