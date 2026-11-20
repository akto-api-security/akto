package com.akto.action;

import com.akto.action.settings.ModuleInfoAction;
import com.akto.dao.AuthTokensDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.AuthToken;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.utils.AuthTokenService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

public class AuthTokenAction extends UserAction {

    private static final int MAX_LISTED_TOKENS = 1000;
    // same window as the Modules tab, so a module listed there is always listed under its token here
    private static final int ACTIVE_HEARTBEAT_SECONDS = 300;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ROW_TYPE = new TypeReference<Map<String, Object>>() {};

    @Setter private String name;
    @Setter private List<String> scope;
    @Setter private int expiryTimeInMonth;
    @Setter private String reason;
    @Getter @Setter private String tokenId;

    @Getter private String apiToken;
    @Getter private String hint;
    @Getter private List<Map<String, Object>> authTokens;
    @Getter private long revokedCount;

    public String createAuthToken() {
        try {
            AuthTokenService.CreatedToken created = AuthTokenService.createToken(
                    name, scope, expiryTimeInMonth, getSUser().getId(), getSUser().getLogin());
            this.apiToken = created.apiToken;
            this.tokenId = created.tokenId;
            this.hint = created.hint;
            return Action.SUCCESS.toUpperCase();
        } catch (IllegalArgumentException | IllegalStateException e) {
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    // Newest first; each token row carries every module that heartbeated with it in the last 5 minutes.
    public String fetchAuthTokens() {
        List<AuthToken> tokens = AuthTokensDao.instance.findAll(
                Filters.empty(), 0, MAX_LISTED_TOKENS, Sorts.descending(AuthToken.CREATED_AT));
        Map<String, List<Map<String, Object>>> modules = modulesByToken(tokens);

        this.authTokens = new ArrayList<>();
        for (AuthToken token : tokens) {
            Map<String, Object> row = MAPPER.convertValue(token, ROW_TYPE);
            row.put("modules", modules.getOrDefault(token.getId(), new ArrayList<>()));
            authTokens.add(row);
        }
        return Action.SUCCESS.toUpperCase();
    }

    // Stale rows (restarts, retired devices) are left out in the query itself, so they never inflate a token's list.
    private Map<String, List<Map<String, Object>>> modulesByToken(List<AuthToken> tokens) {
        Map<String, List<Map<String, Object>>> byToken = new HashMap<>();
        if (tokens.isEmpty()) {
            return byToken;
        }
        int activeAfter = Context.now() - ACTIVE_HEARTBEAT_SECONDS;
        List<String> ids = tokens.stream().map(AuthToken::getId).collect(Collectors.toList());
        List<ModuleInfo> modules = ModuleInfoDao.instance.findAll(
                Filters.and(
                        Filters.in(ModuleInfo.TOKEN_ID, ids),
                        Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, activeAfter)),
                Projections.include(ModuleInfo.NAME, ModuleInfo.MODULE_TYPE, ModuleInfo.LAST_HEARTBEAT_RECEIVED, ModuleInfo.TOKEN_ID));
        modules.sort((a, b) -> Integer.compare(b.getLastHeartbeatReceived(), a.getLastHeartbeatReceived()));
        for (ModuleInfo module : modules) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", module.getId());
            row.put("name", displayName(module));
            row.put("moduleType", module.getModuleType());
            row.put("lastHeartbeatReceived", module.getLastHeartbeatReceived());
            byToken.computeIfAbsent(module.getTokenId(), k -> new ArrayList<>()).add(row);
        }
        return byToken;
    }

    // Akto-hosted modules get a generic name so a customer never sees Akto's container hostnames.
    private static String displayName(ModuleInfo module) {
        ModuleInfo.ModuleType type = module.getModuleType();
        return type != null && ModuleInfoAction.AKTO_HOSTED_MODULE_TYPES.contains(type.name())
                ? "Akto-hosted " + type.name().toLowerCase() : module.getName();
    }

    public String revokeAuthToken() {
        if (tokenId == null || tokenId.isEmpty()) {
            addActionError("Token id is required");
            return Action.ERROR.toUpperCase();
        }
        if (!AuthTokenService.revoke(tokenId, reason, revokedBy())) {
            addActionError("Token not found");
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String revokeAllAuthTokens() {
        this.revokedCount = AuthTokenService.revokeAllActive(reason, revokedBy());
        return Action.SUCCESS.toUpperCase();
    }

    private String revokedBy() {
        return getSUser() == null ? "" : getSUser().getLogin();
    }
}
