package com.akto.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.McpOAuthClientsDao;
import com.akto.dao.McpOAuthGrantsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.McpOAuthClient;

// What an account's admin allows: when "known clients only" is on, unknown https MCP clients cannot connect
public class McpClientPolicy {

    public static final int MAX_ALLOWED_URIS = 50;

    private final boolean knownClientsOnly;
    private final List<String> allowedUris;

    public McpClientPolicy(boolean knownClientsOnly, List<String> allowedUris) {
        this.knownClientsOnly = knownClientsOnly;
        this.allowedUris = allowedUris == null ? Collections.emptyList() : allowedUris;
    }

    // Off when the account has no settings yet
    public static McpClientPolicy load() {
        AccountSettings settings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        if (settings == null) {
            return new McpClientPolicy(false, null);
        }
        return new McpClientPolicy(settings.isMcpKnownClientsOnly(), settings.getMcpAllowedRedirectUris());
    }

    public boolean isKnown(String redirectUri) {
        return McpOAuthUtils.isKnownRedirect(redirectUri, allowedUris);
    }

    public boolean blocks(String redirectUri) {
        return knownClientsOnly && !isKnown(redirectUri);
    }

    // Every https redirect the client registered must be known, so one allowed URI can't vouch for the rest
    public boolean allowsClient(McpOAuthClient client) {
        for (String redirectUri : client.getRedirectUris()) {
            if (!isKnown(redirectUri)) {
                return false;
            }
        }
        return true;
    }

    // Deletes this account's refresh tokens of clients that are no longer allowed; their access tokens expire within an hour
    public long revokeDisallowedGrants() {
        if (!knownClientsOnly) {
            return 0;
        }
        int accountId = Context.accountId.get();
        List<String> disallowed = new ArrayList<>();
        for (String clientId : McpOAuthGrantsDao.instance.refreshClientIds(accountId)) {
            McpOAuthClient client = McpOAuthClientsDao.instance.findById(clientId);
            if (client == null || !allowsClient(client)) {
                disallowed.add(clientId);
            }
        }
        return disallowed.isEmpty() ? 0 : McpOAuthGrantsDao.instance.deleteRefreshForClients(accountId, disallowed);
    }
}
