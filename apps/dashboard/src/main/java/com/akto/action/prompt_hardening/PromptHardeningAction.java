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
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class PromptHardeningAction extends UserAction {

    private Map<String, Object> promptsObj;
    private String content;
    private String templateId;
    private String category;
    private boolean inactive;

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
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Saves or updates a prompt hardening template
     */
    public String savePrompt() {
        try {
            if (content == null || content.isEmpty()) {
                throw new Exception("Content cannot be empty");
            }
            
            if (templateId == null || templateId.isEmpty()) {
                throw new Exception("Template ID cannot be empty");
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
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Deletes a prompt hardening template
     */
    public String deletePrompt() {
        try {
            if (templateId == null || templateId.isEmpty()) {
                throw new Exception("Template ID cannot be empty");
            }

            PromptHardeningYamlTemplateDao.instance.getMCollection().deleteOne(
                Filters.eq(Constants.ID, templateId)
            );
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Toggles the inactive status of a prompt template
     */
    public String togglePromptStatus() {
        try {
            if (templateId == null || templateId.isEmpty()) {
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
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    // Getters and Setters
    public Map<String, Object> getPromptsObj() {
        return promptsObj;
    }

    public void setPromptsObj(Map<String, Object> promptsObj) {
        this.promptsObj = promptsObj;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isInactive() {
        return inactive;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }
}

