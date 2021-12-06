package com.akto.oas;

import io.swagger.v3.oas.models.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.*;

public class ValidateSecurityTest {

    @Test
    public void validateNullGlobalSecurity() {
        List<String> path = new ArrayList<>();
        List<Issue> issues = OpenAPIValidator.validateSecurity(null, null, true, path);
        Assertions.assertEquals(issues.size(), 1);
        Assertions.assertEquals(issues.get(0).getIssueCode(), Issue.generateGlobalSecurityNotDefinedIssue(path).getIssueCode());
    }

    @Test
    public void validateNullLocalSecurity() {
        List<String> path = new ArrayList<>();
        List<Issue> issues = OpenAPIValidator.validateSecurity(null, null,false, path);
        Assertions.assertEquals(issues.size(), 0);
    }

    @Test
    public void validateEmptySecurity() {
        List<String> path = new ArrayList<>();
        List<Issue> issues = OpenAPIValidator.validateSecurity(new ArrayList<>(), null,true, path);
        Assertions.assertEquals(issues.size(),1);
        Assertions.assertEquals(issues.get(0).getIssueCode(),Issue.generateSecurityFieldEmptyArrayIssue(path).getIssueCode());
        Assertions.assertEquals(issues.get(0).getPath(), Collections.singletonList(PathComponent.SECURITY));
    }

    @Test
    public void emptySecurityRequirement() {
        List<String> path = new ArrayList<>();
        List<SecurityRequirement> securityRequirements = Collections.singletonList(new SecurityRequirement());
        List<Issue> issues = OpenAPIValidator.validateSecurity(securityRequirements, null,true, path);
        Assertions.assertEquals(issues.size(),1);
        Assertions.assertEquals(issues.get(0).getIssueCode(),Issue.generateEmptySecurityRequirementIssue(path).getIssueCode());
        Assertions.assertEquals(issues.get(0).getPath(), Arrays.asList(PathComponent.SECURITY, "0"));
    }

    @Test
    public void notDefinedSecurityScheme() {
        List<String> path = new ArrayList<>();
        SecurityRequirement securityRequirement = new SecurityRequirement();
        securityRequirement.put("first", new ArrayList<>());
        List<Issue> issues = OpenAPIValidator.validateSecurity(Collections.singletonList(securityRequirement), null,true, path);
        Assertions.assertEquals(issues.size(),1);
        Assertions.assertEquals(issues.get(0).getMessage(), Issue.generateSecuritySchemeNotDefined("first",path).getMessage());
        Assertions.assertEquals(issues.get(0).getPath(), Arrays.asList(PathComponent.SECURITY, "0", "first"));
    }

    @Test void validOauthSecurity() {
        List<String> path = new ArrayList<>();
        SecurityRequirement securityRequirement = new SecurityRequirement();
        securityRequirement.put("first", Collections.singletonList("scope1"));

        Map<String, SecurityScheme> securitySchemeMap = new HashMap<>();
        SecurityScheme securityScheme = new SecurityScheme();
        securityScheme.setType(SecurityScheme.Type.OAUTH2);
        OAuthFlows oAuthFlows = new OAuthFlows();
        OAuthFlow authCodeFlow = new OAuthFlow();
        Scopes scopes = new Scopes();
        scopes.addString("scope1", "second scope");
        authCodeFlow.setScopes(scopes);
        oAuthFlows.setAuthorizationCode(authCodeFlow);
        securityScheme.setFlows(oAuthFlows);
        securitySchemeMap.put("first", securityScheme);

        List<Issue> issues = OpenAPIValidator.validateSecurity(Collections.singletonList(securityRequirement),securitySchemeMap,true, path);
        Assertions.assertEquals(issues.size(), 0);
    }

    @Test
    public void invalidOauthSecurityScheme() {
        List<String> path = new ArrayList<>();
        SecurityRequirement securityRequirement = new SecurityRequirement();
        securityRequirement.put("first", Collections.singletonList("scope1"));

        Map<String, SecurityScheme> securitySchemeMap = new HashMap<>();
        SecurityScheme securityScheme = new SecurityScheme();
        securityScheme.setType(SecurityScheme.Type.OAUTH2);
        OAuthFlows oAuthFlows = new OAuthFlows();
        OAuthFlow authCodeFlow = new OAuthFlow();
        Scopes scopes = new Scopes();
        scopes.addString("scope2", "second scope");
        authCodeFlow.setScopes(scopes);
        oAuthFlows.setAuthorizationCode(authCodeFlow);
        securityScheme.setFlows(oAuthFlows);
        securitySchemeMap.put("first", securityScheme);

        List<Issue> issues = OpenAPIValidator.validateSecurity(Collections.singletonList(securityRequirement),securitySchemeMap,true, path);
        Assertions.assertEquals(issues.size(), 1);
        Assertions.assertEquals(issues.get(0).getMessage(), Issue.generateScopeNotDefinedSecuritySchema("scope1","first",path).getMessage());
    }

    @Test
    public void invalidAPIKeySecurityScheme() {
        List<String> path = new ArrayList<>();
        SecurityRequirement securityRequirement = new SecurityRequirement();
        securityRequirement.put("first", Collections.singletonList("scope1"));

        Map<String, SecurityScheme> securitySchemeMap = new HashMap<>();
        SecurityScheme securityScheme = new SecurityScheme();
        securityScheme.setType(SecurityScheme.Type.APIKEY);
        securitySchemeMap.put("first", securityScheme);

        List<Issue> issues = OpenAPIValidator.validateSecurity(Collections.singletonList(securityRequirement),securitySchemeMap,true, path);
        Assertions.assertEquals(issues.size(), 1);
        Assertions.assertEquals(issues.get(0).getMessage(), Issue.generateScopesNotDefinedForType(SecurityScheme.Type.APIKEY+"",path).getMessage());
    }

    @Test
    public void validAPIKeySecurityScheme() {
        List<String> path = new ArrayList<>();
        SecurityRequirement securityRequirement = new SecurityRequirement();
        securityRequirement.put("first", new ArrayList<>());

        Map<String, SecurityScheme> securitySchemeMap = new HashMap<>();
        SecurityScheme securityScheme = new SecurityScheme();
        securityScheme.setType(SecurityScheme.Type.APIKEY);
        securitySchemeMap.put("first", securityScheme);

        List<Issue> issues = OpenAPIValidator.validateSecurity(Collections.singletonList(securityRequirement),securitySchemeMap,true, path);
        Assertions.assertEquals(issues.size(), 0);
    }
}
