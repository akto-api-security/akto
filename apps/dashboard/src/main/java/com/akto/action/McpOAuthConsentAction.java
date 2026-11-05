package com.akto.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.struts2.interceptor.ServletRequestAware;

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
import com.akto.util.McpClientPolicy;
import com.akto.util.McpOAuthUtils;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// Logged-in user approves or denies an MCP client's pending authorize request
@Getter
public class McpOAuthConsentAction extends UserAction implements ServletRequestAware {

    private static final String EXPIRED_MESSAGE = "This authorization request has expired. Start the connection again from your MCP client.";
    private static final String ACCESS_DENIED = "access_denied";
    private static final String SESSION_API_KEY_UTILITY = "utility";
    private static final String INTERACTIVE_LOGIN_REQUIRED_MESSAGE = "Connections can only be approved from a signed-in Akto dashboard session.";
    private static final String NOT_STARTED_HERE_MESSAGE = "This connection was not started in this browser. Start it again from your MCP client.";
    private static final String NOT_APPROVED_MESSAGE = "Your admin only allows approved MCP clients, and %s is not approved. Ask an admin to allow it.";
    private static final String CONFIRM_UNVERIFIED_MESSAGE = "Confirm that you trust this app to continue.";
    private static final String NO_PRODUCT_ACCESS_MESSAGE = "You don't have access to any Akto product in this account. Ask an admin for access.";

    // Request params; the fields below are response-only
    @Setter
    private String requestId;
    @Setter
    private String contextSource;
    @Setter
    private boolean confirmUnverified;

    private String clientName;
    private String redirectHost;
    private String accountName;
    private String userLogin;
    private String redirectUrl;
    private List<String> allowedContexts;
    private boolean unverifiedRedirect;
    private String redirectUri;
    private int clientCreatedAt;

    @Getter(AccessLevel.NONE)
    private HttpServletRequest servletRequest;

    public String fetchAuthorizeRequest() {
        McpOAuthGrant request = McpOAuthGrantsDao.instance.find(hashOfRequestId(), McpOAuthGrant.TYPE_REQUEST);
        if (request == null) {
            return expired();
        }
        if (!startedInThisBrowser()) {
            addActionError(NOT_STARTED_HERE_MESSAGE);
            return ERROR.toUpperCase();
        }
        McpClientPolicy policy = McpClientPolicy.load();
        if (policy.blocks(request.getRedirectUri())) {
            addActionError(String.format(NOT_APPROVED_MESSAGE, McpOAuthUtils.redirectDisplayHost(request.getRedirectUri())));
            return ERROR.toUpperCase();
        }
        McpOAuthClient client = McpOAuthClientsDao.instance.findById(request.getClientId());
        clientName = client == null ? McpOAuthUtils.DEFAULT_CLIENT_NAME : client.getClientName();
        clientCreatedAt = client == null ? 0 : client.getCreatedAt();
        redirectUri = request.getRedirectUri();
        redirectHost = McpOAuthUtils.redirectDisplayHost(request.getRedirectUri());
        unverifiedRedirect = !policy.isKnown(request.getRedirectUri());
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
        // Tokens and API keys must not approve clients, or they could mint refresh tokens
        if (!isInteractiveSession()) {
            addActionError(INTERACTIVE_LOGIN_REQUIRED_MESSAGE);
            return ERROR.toUpperCase();
        }
        if (!startedInThisBrowser()) {
            addActionError(NOT_STARTED_HERE_MESSAGE);
            return ERROR.toUpperCase();
        }
        String chosenContext = contextSource == null ? Context.contextSource.get().name() : contextSource.toUpperCase();
        if (!accessibleContexts().contains(chosenContext)) {
            addActionError("You don't have access to this product in this account.");
            return ERROR.toUpperCase();
        }
        // The admin's policy and the unknown-host confirmation are checked before the single-use request is spent
        McpOAuthGrant pending = McpOAuthGrantsDao.instance.find(hashOfRequestId(), McpOAuthGrant.TYPE_REQUEST);
        if (pending != null) {
            McpClientPolicy policy = McpClientPolicy.load();
            if (policy.blocks(pending.getRedirectUri())) {
                addActionError(String.format(NOT_APPROVED_MESSAGE, McpOAuthUtils.redirectDisplayHost(pending.getRedirectUri())));
                return ERROR.toUpperCase();
            }
            if (!confirmUnverified && !policy.isKnown(pending.getRedirectUri())) {
                addActionError(CONFIRM_UNVERIFIED_MESSAGE);
                return ERROR.toUpperCase();
            }
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

    // The authorize endpoint sets a cookie in the browser that started the request, so a consent link sent to someone else fails
    private boolean startedInThisBrowser() {
        if (servletRequest == null || servletRequest.getCookies() == null) {
            return false;
        }
        String requestHash = hashOfRequestId();
        String cookieName = McpOAuthUtils.requestBindingCookieName(requestHash);
        for (Cookie cookie : servletRequest.getCookies()) {
            if (cookieName.equals(cookie.getName()) && requestHash.equals(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean isInteractiveSession() {
        Map<String, Object> session = getSession();
        return session != null
                && !Boolean.TRUE.equals(session.get(McpOAuthUtils.SESSION_MCP_TOKEN_AUTH))
                && session.get(SESSION_API_KEY_UTILITY) == null;
    }

    // Products where the user has a role in this account
    private List<String> accessibleContexts() {
        int userId = getSUser().getId();
        int currentAccountId = Context.accountId.get();
        CONTEXT_SOURCE original = Context.contextSource.get();
        List<String> accessible = new ArrayList<>();
        try {
            for (String name : McpOAuthUtils.CONTEXT_SOURCES) {
                // The role lookup uses the thread's context, so set it per product
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

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.servletRequest = request;
    }
}
