package com.akto.action;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.Config.McpRegistryConfig;
import com.akto.dto.Config.McpRegistryConfig.McpRegistry;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class McpRegistryAction extends UserAction {

    private static final int MAX_REGISTRIES = 10;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_URL_LENGTH = 500;
    private static final String DEFAULT_REGISTRY_ID = "default";
    private static final String DEFAULT_REGISTRY_NAME = "Official MCP Registry";
    private static final String DEFAULT_REGISTRY_URL = "https://registry.modelcontextprotocol.io/v0/servers";

    private List<McpRegistry> registries;
    private McpRegistryConfig mcpRegistryConfig;

    public String addMcpRegistryIntegration() {
        try {
            int accId = Context.accountId.get();
            Bson filters = Filters.eq("_id", accId + "_" + ConfigType.MCP_REGISTRY.name());
            
            // If registries list is provided (even if empty), handle it
            if (registries != null) {
                if (registries.isEmpty()) {
                    // Empty list means user wants to reset to default - delete the custom config
                    ConfigsDao.instance.getMCollection().deleteOne(filters);
                } else {
                    // Validate all registries
                    String validationError = validateRegistries(registries);
                    if (validationError != null) {
                        addActionError(validationError);
                        return ERROR.toUpperCase();
                    }
                    
                    // Save to database
                    saveRegistriesToDb(filters, registries, accId);
                }
            }
            
            // Always fetch and return current state from DB
            fetchAndSetConfig(filters, accId);
            
            return SUCCESS.toUpperCase();
            
        } catch (Exception e) {
            addActionError("Failed to update MCP registry configuration: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Validates the list of registries
     * @param registries List of registries to validate
     * @return Error message if validation fails, null otherwise
     */
    private String validateRegistries(List<McpRegistry> registries) {
        if (registries == null || registries.isEmpty()) {
            return "At least one registry is required";
        }

        if (registries.size() > MAX_REGISTRIES) {
            return "Maximum " + MAX_REGISTRIES + " registries allowed";
        }

        Set<String> names = new HashSet<>();
        Set<String> urls = new HashSet<>();

        for (McpRegistry registry : registries) {
            // Validate registry ID
            if (registry.getId() == null || registry.getId().trim().isEmpty()) {
                return "Registry ID cannot be empty";
            }

            // Validate name
            if (registry.getName() == null || registry.getName().trim().isEmpty()) {
                return "Registry name cannot be empty";
            }

            String trimmedName = registry.getName().trim();
            if (trimmedName.length() > MAX_NAME_LENGTH) {
                return "Registry name too long (max " + MAX_NAME_LENGTH + " characters)";
            }

            // Sanitize name - prevent XSS
            if (trimmedName.matches(".*[<>\"'].*")) {
                return "Registry name contains invalid characters";
            }

            // Check for duplicate names (case-insensitive)
            String nameLower = trimmedName.toLowerCase();
            if (names.contains(nameLower)) {
                return "Duplicate registry name: " + trimmedName;
            }
            names.add(nameLower);

            // Validate URL
            if (registry.getUrl() == null || registry.getUrl().trim().isEmpty()) {
                return "Registry URL cannot be empty";
            }

            String trimmedUrl = registry.getUrl().trim();
            if (trimmedUrl.length() > MAX_URL_LENGTH) {
                return "Registry URL too long (max " + MAX_URL_LENGTH + " characters)";
            }

            // Validate URL scheme
            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                return "Registry URL must start with http:// or https://";
            }

            // Check for duplicate URLs (case-insensitive)
            String urlLower = trimmedUrl.toLowerCase();
            if (urls.contains(urlLower)) {
                return "Duplicate registry URL: " + trimmedUrl;
            }
            urls.add(urlLower);
        }

        return null;
    }

    /**
     * Saves registries to database
     */
    private void saveRegistriesToDb(Bson filters, List<McpRegistry> registries, int accId) {
        McpRegistryConfig existingConfig = (McpRegistryConfig) ConfigsDao.instance.findOne(filters);

        if (existingConfig != null) {
            // Update existing config
            Bson updates = Updates.set(McpRegistryConfig.REGISTRIES, registries);
            ConfigsDao.instance.updateOne(filters, updates);
        } else {
            // Create new config
            McpRegistryConfig config = new McpRegistryConfig(registries, accId);
            ConfigsDao.instance.insertOne(config);
        }
    }

    /**
     * Fetches config from DB and sets it to mcpRegistryConfig field
     */
    private void fetchAndSetConfig(Bson filters, int accId) {
        mcpRegistryConfig = (McpRegistryConfig) ConfigsDao.instance.findOne(filters);

        // If no config exists, return default registry
        if (mcpRegistryConfig == null) {
            List<McpRegistry> defaultRegistries = new ArrayList<>();
            defaultRegistries.add(new McpRegistry(
                DEFAULT_REGISTRY_ID,
                DEFAULT_REGISTRY_NAME,
                DEFAULT_REGISTRY_URL,
                true
            ));
            mcpRegistryConfig = new McpRegistryConfig(defaultRegistries, accId);
        }
    }

    public List<McpRegistry> getRegistries() {
        return registries;
    }

    public void setRegistries(List<McpRegistry> registries) {
        this.registries = registries;
    }

    public McpRegistryConfig getMcpRegistryConfig() {
        return mcpRegistryConfig;
    }

    public void setMcpRegistryConfig(McpRegistryConfig mcpRegistryConfig) {
        this.mcpRegistryConfig = mcpRegistryConfig;
    }
}

