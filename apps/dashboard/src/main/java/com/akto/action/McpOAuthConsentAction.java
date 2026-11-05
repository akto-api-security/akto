package com.akto.action;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dao.AccountsDao;
import com.akto.dao.McpOAuthClientsDao;
import com.akto.dao.McpOAuthGrantsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.McpOAuthClient;
import com.akto.dto.McpOAuthGrant;
import com.akto.dto.RBAC.Role;
import com.akto.util.Constants;
import com.akto.util.McpOAuthUtils;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;

// Logged-in user approves or denies an MCP client's pending authorize request
public class McpOAuthConsentAction extends UserAction {

    private static final String EXPIRED_MESSAGE = "This authorization request has expired. Start the connection again from your MCP client.";
    private static final String ACCESS_DENIED = "access_denied";
    private static final String SESSION_API_KEY_UTILITY = "utility";
    private static final String INTERACTIVE_LOGIN_REQUIRED_MESSAGE = "Connections can only be approved from a signed-in Akto dashboard session.";
    private static final String NO_PRODUCT_ACCESS_MESSAGE = "You don't have access to any Akto product in this account. Ask an admin for access.";

    private String requestId;
    private String contextSource;

    private String clientName;
    private String redirectHost;
    private String accountName;
    private String userLogin;
    private String redirectUrl;
    private List<String> allowedContexts;

    public String fetchAuthorizeRequest() {
        McpOAuthGrant request = McpOAuthGrantsDao.instance.find(hashOfRequestId(), McpOAuthGrant.TYPE_REQUEST);
        if (request == null) {
            return expired();
        }
        McpOAuthClient client = McpOAuthClientsDao.instance.findById(request.getClientId());
        clientName = client == null ? McpOAuthUtils.DEFAULT_CLIENT_NAME : client.getClientName();
        redirectHost = URI.create(request.getRedirectUri()).getHost();
        int currentAccountId = Context.accountId.get();
        Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, currentAccountId));
        accountName = account == null ? String.valueOf(currentAccountId) : account.getName();
        userLogin = getSUser().getLogin();
        allowedContexts = accessibleContexts();
        if (allowedContexts.isEmpty()) {
            addActionError(NO_PRODUCT_ACCESS_MESSAGE);
            return ERROR.toUpperCase();
        }
        String currentContext = Context.contextSource.get().name();
        contextSource = allowedContexts.contains(currentContext) ? currentContext : allowedContexts.get(0);
        return SUCCESS.toUpperCase();
    }

    public String approveAuthorizeRequest() {
        // An access token or API key must not be able to approve a client, or it could mint a long-lived refresh token
        if (!isInteractiveSession()) {
            addActionError(INTERACTIVE_LOGIN_REQUIRED_MESSAGE);
            return ERROR.toUpperCase();
        }
        String chosenContext = contextSource == null ? Context.contextSource.get().name() : contextSource.toUpperCase();
        if (!accessibleContexts().contains(chosenContext)) {
            addActionError("You don't have access to this product in this account.");
            return ERROR.toUpperCase();
        }
        McpOAuthGrant request = consumeRequest();
        if (request == null) {
            return expired();
        }

        String codeSecret = McpOAuthUtils.newSecret();
        McpOAuthGrant code = McpOAuthUtils.newGrant(McpOAuthGrant.TYPE_CODE, codeSecret, McpOAuthUtils.CODE_TTL_SECONDS);
        code.setClientId(request.getClientId());
        code.setRedirectUri(request.getRedirectUri());
        code.setState(request.getState());
        code.setCodeChallenge(request.getCodeChallenge());
        code.setResource(request.getResource());
        code.setUsername(getSUser().getLogin());
        code.setAccountId(Context.accountId.get());
        code.setContextSource(chosenContext);
        McpOAuthGrantsDao.instance.insert(code);

        redirectUrl = McpOAuthUtils.redirectWith(request.getRedirectUri(), request.getState(), McpOAuthUtils.PARAM_CODE, codeSecret);
        return SUCCESS.toUpperCase();
    }

    public String denyAuthorizeRequest() {
        McpOAuthGrant request = consumeRequest();
        if (request == null) {
            return expired();
        }
        redirectUrl = McpOAuthUtils.redirectWith(request.getRedirectUri(), request.getState(), McpOAuthUtils.PARAM_ERROR, ACCESS_DENIED);
        return SUCCESS.toUpperCase();
    }

    private boolean isInteractiveSession() {
        Map<String, Object> session = getSession();
        return session == null
                || (!Boolean.TRUE.equals(session.get(McpOAuthUtils.SESSION_MCP_TOKEN_AUTH)) && session.get(SESSION_API_KEY_UTILITY) == null);
    }

    // Products where the user has a role in the current account; NO_ACCESS products are left out
    private List<String> accessibleContexts() {
        int userId = getSUser().getId();
        int currentAccountId = Context.accountId.get();
        CONTEXT_SOURCE original = Context.contextSource.get();
        List<String> accessible = new ArrayList<>();
        try {
            for (String name : McpOAuthUtils.CONTEXT_SOURCES) {
                // The role lookup reads the thread's current context, so evaluate each product in turn
                Context.contextSource.set(CONTEXT_SOURCE.valueOf(name));
                if (!Role.NO_ACCESS.equals(RBACDao.getCurrentRoleForUser(userId, currentAccountId))) {
                    accessible.add(name);
                }
            }
        } finally {
            Context.contextSource.set(original);
        }
        return accessible;
    }

    private String hashOfRequestId() {
        return McpOAuthUtils.sha256Hex(requestId == null ? "" : requestId);
    }

    // Single-use, so a request can be approved or denied only once
    private McpOAuthGrant consumeRequest() {
        return McpOAuthGrantsDao.instance.consume(hashOfRequestId(), McpOAuthGrant.TYPE_REQUEST);
    }

    private String expired() {
        addActionError(EXPIRED_MESSAGE);
        return ERROR.toUpperCase();
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getContextSource() {
        return contextSource;
    }

    public void setContextSource(String contextSource) {
        this.contextSource = contextSource;
    }

    public String getClientName() {
        return clientName;
    }

    public String getRedirectHost() {
        return redirectHost;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public List<String> getAllowedContexts() {
        return allowedContexts;
    }
}
