package com.akto.action;

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
    private static final int MAX_MODULES_PER_TOKEN = 20;
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

    // Newest first; each token row carries the modules currently heartbeating with it.
    public String fetchAuthTokens() {
        List<AuthToken> tokens = AuthTokensDao.instance.findAll(
                Filters.empty(), 0, MAX_LISTED_TOKENS, Sorts.descending(AuthToken.CREATED_AT));
        Map<String, List<Map<String, Object>>> modules = modulesByToken(tokens);

        this.authTokens = new ArrayList<>();
        for (AuthToken token : tokens) {
            List<Map<String, Object>> tokenModules = modules.getOrDefault(token.getId(), new ArrayList<>());
            Map<String, Object> row = MAPPER.convertValue(token, ROW_TYPE);
            row.put("moduleCount", tokenModules.size());
            row.put("modules", newestFirst(tokenModules));
            authTokens.add(row);
        }
        return Action.SUCCESS.toUpperCase();
    }

    // Capped so a token shared by thousands of devices cannot bloat the response; moduleCount carries the real total.
    private static List<Map<String, Object>> newestFirst(List<Map<String, Object>> modules) {
        return modules.stream()
                .sorted((a, b) -> Integer.compare((int) b.get("lastHeartbeatReceived"), (int) a.get("lastHeartbeatReceived")))
                .limit(MAX_MODULES_PER_TOKEN)
                .collect(Collectors.toList());
    }

    private Map<String, List<Map<String, Object>>> modulesByToken(List<AuthToken> tokens) {
        Map<String, List<Map<String, Object>>> byToken = new HashMap<>();
        if (tokens.isEmpty()) {
            return byToken;
        }
        int activeAfter = Context.now() - ACTIVE_HEARTBEAT_SECONDS;
        List<String> ids = tokens.stream().map(AuthToken::getId).collect(Collectors.toList());
        List<ModuleInfo> modules = ModuleInfoDao.instance.findAll(
                Filters.in(ModuleInfo.TOKEN_ID, ids),
                Projections.include(ModuleInfo.NAME, ModuleInfo.MODULE_TYPE, ModuleInfo.LAST_HEARTBEAT_RECEIVED, ModuleInfo.TOKEN_ID));
        for (ModuleInfo module : modules) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", module.getId());
            row.put("name", module.getName());
            row.put("moduleType", module.getModuleType());
            row.put("lastHeartbeatReceived", module.getLastHeartbeatReceived());
            row.put("active", module.getLastHeartbeatReceived() >= activeAfter);
            byToken.computeIfAbsent(module.getTokenId(), k -> new ArrayList<>()).add(row);
        }
        return byToken;
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
