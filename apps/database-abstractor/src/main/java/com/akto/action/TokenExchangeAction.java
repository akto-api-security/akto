package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.ActionSupport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenExchangeAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TokenExchangeAction.class, LogDb.DB_ABS);

    private static final String ACCOUNT_ID = "accountId";
    private static final String SCOPE = "scope";
    private static final String MODULE_TYPE = "moduleType";
    private static final String ISSUER = "Akto";

    private String moduleType;
    private String token;

    public String exchangeToken() {
        List<String> scope = Context.tokenScope.get();
        if (scope == null || scope.isEmpty()) {
            addActionError("This token does not carry a scope to exchange");
            return ERROR.toUpperCase();
        }

        if (moduleType == null || moduleType.isEmpty() || !scope.contains(moduleType)) {
            addActionError("moduleType does not match the token's scope");
            return ERROR.toUpperCase();
        }

        try {
            Map<String, Object> claims = new HashMap<>();
            claims.put(ACCOUNT_ID, Context.accountId.get());
            claims.put(SCOPE, scope);
            claims.put(MODULE_TYPE, moduleType);

            token = JwtAuthenticator.createJWT(claims, ISSUER, moduleType, Context.tokenExpiry.get());
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error exchanging token: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error exchanging token");
            return ERROR.toUpperCase();
        }
    }
}
