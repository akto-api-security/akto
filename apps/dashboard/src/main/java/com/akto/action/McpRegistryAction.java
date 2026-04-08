package com.akto.action;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.Config.McpRegistryConfig;
import com.akto.dto.Config.McpRegistryConfig.ApprovedMcpServer;
import com.akto.dto.Config.McpRegistryConfig.McpRegistry;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.User;
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
    private List<ApprovedMcpServer> approvedServers;
    private Boolean blockNewMcpServers;
    private String csvContent;
    private List<String> parsedServerNames;

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

    public String saveMcpServerSettings() {
        try {
            int accId = Context.accountId.get();
            Bson filters = Filters.eq("_id", accId + "_" + ConfigType.MCP_REGISTRY.name());

            if (approvedServers != null) {
                String validationError = validateApprovedServers(approvedServers);
                if (validationError != null) {
                    addActionError(validationError);
                    return ERROR.toUpperCase();
                }

                // Find newly added servers by diffing against existing config
                McpRegistryConfig existingConfig = (McpRegistryConfig) ConfigsDao.instance.findOne(filters);
                Set<String> existingNames = new HashSet<>();
                if (existingConfig != null && existingConfig.getApprovedServers() != null) {
                    for (ApprovedMcpServer s : existingConfig.getApprovedServers()) {
                        if (s.getName() != null) {
                            existingNames.add(s.getName().toLowerCase());
                        }
                    }
                }

                // Bulk-approve existing McpAuditInfo records for newly added servers
                User user = getSUser();
                String markedBy = user != null ? user.getLogin() : "";
                int now = Context.now();
                for (ApprovedMcpServer server : approvedServers) {
                    if (server.getName() != null && !existingNames.contains(server.getName().toLowerCase())) {
                        Bson hostFilter = Filters.and(
                            Filters.eq(McpAuditInfo.MCP_HOST, server.getName()),
                            Filters.ne("remarks", "Approved")
                        );
                        List<McpAuditInfo> toApprove = McpAuditInfoDao.instance.findAll(hostFilter, 0, 1000, null);
                        for (McpAuditInfo info : toApprove) {
                            McpAuditInfoDao.instance.updateOne(
                                Filters.eq("_id", info.getId()),
                                Updates.combine(
                                    Updates.set("remarks", "Approved"),
                                    Updates.set("markedBy", markedBy),
                                    Updates.set("updatedTimestamp", now),
                                    Updates.set("approvedAt", now)
                                )
                            );
                        }
                    }
                }

                // Save approved servers to config
                if (existingConfig != null) {
                    ConfigsDao.instance.updateOne(filters,
                        Updates.set(McpRegistryConfig.APPROVED_SERVERS, approvedServers));
                } else {
                    McpRegistryConfig config = new McpRegistryConfig(null, accId);
                    config.setApprovedServers(approvedServers);
                    ConfigsDao.instance.insertOne(config);
                }
            }

            if (blockNewMcpServers != null) {
                AccountSettingsDao.instance.getMCollection().updateOne(
                    AccountSettingsDao.generateFilter(),
                    Updates.set(AccountSettings.BLOCK_NEW_MCP_SERVERS, blockNewMcpServers)
                );
            }

            // Fetch and return current state
            fetchAndSetConfig(filters, accId);
            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            this.blockNewMcpServers = accountSettings != null && accountSettings.isBlockNewMcpServers();

            return SUCCESS.toUpperCase();

        } catch (Exception e) {
            addActionError("Failed to save MCP server settings: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    private String validateApprovedServers(List<ApprovedMcpServer> servers) {
        Set<String> names = new HashSet<>();
        for (ApprovedMcpServer server : servers) {
            if (server.getName() == null || server.getName().trim().isEmpty()) {
                return "Server name cannot be empty";
            }
            String trimmed = server.getName().trim();
            if (trimmed.length() > MAX_NAME_LENGTH) {
                return "Server name too long (max " + MAX_NAME_LENGTH + " characters)";
            }
            if (trimmed.matches(".*[<>\"'].*")) {
                return "Server name contains invalid characters";
            }
            String lower = trimmed.toLowerCase();
            if (names.contains(lower)) {
                return "Duplicate server name: " + trimmed;
            }
            names.add(lower);
        }
        return null;
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

    public String uploadMcpServersCsv() {
        parsedServerNames = new ArrayList<>();

        if (csvContent == null || csvContent.trim().isEmpty()) {
            addActionError("CSV content cannot be empty");
            return ERROR.toUpperCase();
        }

        if (csvContent.length() > 1024 * 1024) { // 1MB limit
            addActionError("CSV file too large (max 1MB)");
            return ERROR.toUpperCase();
        }

        try {
            BufferedReader reader = new BufferedReader(new StringReader(csvContent));
            String line;
            boolean firstLine = true;
            Set<String> seen = new HashSet<>();

            while ((line = reader.readLine()) != null) {
                // Take first column only (in case it's a proper CSV)
                String cell = line.split(",")[0].trim();

                // Strip surrounding quotes
                if (cell.startsWith("\"") && cell.endsWith("\"") && cell.length() >= 2) {
                    cell = cell.substring(1, cell.length() - 1).trim();
                }

                if (cell.isEmpty()) continue;

                // Skip header row if it looks like a column label
                if (firstLine) {
                    firstLine = false;
                    String lower = cell.toLowerCase();
                    if (lower.equals("name") || lower.equals("server") || lower.equals("server_name")
                            || lower.equals("servername") || lower.equals("mcp_server")) {
                        continue;
                    }
                } else {
                    firstLine = false;
                }

                // Validate name
                if (cell.length() > MAX_NAME_LENGTH) {
                    addActionError("Server name too long (max " + MAX_NAME_LENGTH + " characters): " + cell);
                    return ERROR.toUpperCase();
                }
                if (cell.matches(".*[<>\"'].*")) {
                    addActionError("Server name contains invalid characters: " + cell);
                    return ERROR.toUpperCase();
                }

                String lower = cell.toLowerCase();
                if (!seen.contains(lower)) {
                    seen.add(lower);
                    parsedServerNames.add(cell);
                }
            }

            if (parsedServerNames.isEmpty()) {
                addActionError("No valid server names found in CSV");
                return ERROR.toUpperCase();
            }

        } catch (Exception e) {
            addActionError("Failed to parse CSV: " + e.getMessage());
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    public List<ApprovedMcpServer> getApprovedServers() {
        return approvedServers;
    }

    public void setApprovedServers(List<ApprovedMcpServer> approvedServers) {
        this.approvedServers = approvedServers;
    }

    public Boolean getBlockNewMcpServers() {
        return blockNewMcpServers;
    }

    public void setBlockNewMcpServers(Boolean blockNewMcpServers) {
        this.blockNewMcpServers = blockNewMcpServers;
    }

    public String getCsvContent() {
        return csvContent;
    }

    public void setCsvContent(String csvContent) {
        this.csvContent = csvContent;
    }

    public List<String> getParsedServerNames() {
        return parsedServerNames;
    }
}

