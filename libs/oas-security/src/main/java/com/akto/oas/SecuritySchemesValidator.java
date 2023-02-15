package com.akto.oas;

import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SecuritySchemesValidator {

    public static List<Issue> validate(Map<String, SecurityScheme> securitySchemeMap, List<String> path) {
        List<Issue> issues = new ArrayList<>();
        path.add(PathComponent.SECURITY_SCHEMES);

        if (securitySchemeMap == null || securitySchemeMap.isEmpty()) {
            issues.add(Issue.generateSecuritySchemeNotDefinedIssue(path));
            return issues;
        }

        for (String schemeName : securitySchemeMap.keySet()) {
            SecurityScheme securityScheme = securitySchemeMap.get(schemeName);
            path.add(schemeName);

            if (securityScheme == null){
                issues.add(Issue.generateSecuritySchemeNullIssue(path));
                path.remove(path.size() - 1);
                continue;
            }

            SecurityScheme.Type type = securityScheme.getType();
            String scheme = securityScheme.getScheme();
            SecurityScheme.In in = securityScheme.getIn();
            String name = securityScheme.getName();
            String openIdConnectUrl = securityScheme.getOpenIdConnectUrl();
            OAuthFlows flows = securityScheme.getFlows();

            if (type == null) {
                issues.add(Issue.generateSecuritySchemeTypeNullIssue(path));
                path.remove(path.size() - 1);
                continue;
            }

            switch (type) {
                case HTTP:
                    switch (scheme.toLowerCase()) {
                        // registry: https://www.iana.org/assignments/http-authschemes/http-authschemes.xhtml
                        case "basic":
                            issues.add(Issue.generateBasicAuthTransportedOverNetworkIssue(path));
                            break;
                        case "negotiate":
                            issues.add(Issue.generateApiNegotiatesAuthIssue(path));
                            break;
                        case "oauth":
                            issues.add(Issue.generateOauth1AllowedIssue(path));
                            break;
                        case "digest":
                            issues.add(Issue.generateDigestAuthAllowedIssue(path));
                        case "bearer":
                        case "hoba":
                        case "mutual":
                        case "vapid":
                        case "scram-sha-1":
                        case "scram-sha-256":
                            break;
                        default:
                            issues.add(Issue.generateUnknownHTTPSchemeIssue(path));
                    }
                    break;
                case APIKEY:
                    if (in == null) {
                        issues.add(Issue.generateInRequiredIssue(path));
                    }
                    if (name == null) {
                        issues.add(Issue.generateSecuritySchemeNameRequiredIssue(path));
                    }
                    issues.add(Issue.generateAPIKeysTransportedOverNetworkIssue(path));
                    break;
                case OAUTH2:
                    if (flows == null) {
                        issues.add(Issue.generateNeedFlowsForOauth2Issue(path));
                        path.remove(path.size()-1);
                        continue;
                    }
                    OAuthFlow flow;
                    // TABLE: https://swagger.io/docs/specification/authentication/oauth2/
                    path.add(PathComponent.AUTHORIZATION_CODE);
                    if ((flow = flows.getAuthorizationCode()) != null) {
                        if (flow.getAuthorizationUrl() == null) {
                            issues.add(Issue.generateAuthUrlRequiredForAuthCodeFlowIssue(path));
                        } else {
                            issues.addAll(OpenAPIValidator.urlIssues(flow.getAuthorizationUrl(), true, path));
                        }
                        if (flow.getTokenUrl() == null) {
                            issues.add(Issue.generateTokenUrlRequiredForOauth2ForAuthFlowIssue(path));
                        } else {
                            issues.addAll(OpenAPIValidator.urlIssues(flow.getAuthorizationUrl(), true, path));
                        }
                        if (flow.getScopes() == null) {
                            issues.add(Issue.generateScopesRequiredForOauth2ForAuthFlowIssue(path));
                        }
                    }
                    path.remove(path.size()-1);
                    path.add(PathComponent.PASSWORD);
                    if ((flow=flows.getPassword()) != null ) {
                        if (flow.getTokenUrl() == null) {
                            issues.add(Issue.generateTokenUrlRequiredForOauth2ForPasswordFlowIssue(path));
                        } else {
                            issues.addAll(OpenAPIValidator.urlIssues(flow.getAuthorizationUrl(), true, path));
                        }
                        if (flow.getScopes() == null) {
                            issues.add(Issue.generateScopesRequiredForOauth2ForPasswordFlowIssue(path));
                        }
                        if (flow.getAuthorizationUrl() != null){
                            issues.add(Issue.generateAuthUrlRequiredForPasswordFlowIssue(path));
                        }

                        issues.add(Issue.generateResourceOwnerPasswordFlowAllowedIssue(path));
                    }
                    path.remove(path.size()-1);
                    path.add(PathComponent.IMPLICIT);
                    if ((flow=flows.getImplicit()) != null ) {
                        if (flow.getAuthorizationUrl() == null) {
                            issues.add(Issue.generateAuthUrlRequiredForImplicitFlowIssue(path));
                        } else {
                            issues.addAll(OpenAPIValidator.urlIssues(flow.getAuthorizationUrl(), true, path));
                        }
                        if (flow.getScopes() == null) {
                            issues.add(Issue.generateScopesRequiredForOauth2ForImplicitlowIssue(path));
                        }
                        if (flow.getTokenUrl() != null){
                            issues.add(Issue.generateTokenUrlRequiredForOauth2ForImplicitFlowIssue(path));
                        } else {
                            issues.addAll(OpenAPIValidator.urlIssues(flow.getAuthorizationUrl(), true, path));
                        }

                        issues.add(Issue.generateUserImplicitFlowIssue(path));
                    }
                    path.remove(path.size()-1);
                    path.add(PathComponent.CLIENT_CREDENTIALS);
                    if ((flow=flows.getClientCredentials()) != null ) {
                        if (flow.getTokenUrl() == null) {
                            issues.add(Issue.generateTokenUrlRequiredForOauth2ForClientCredFlowIssue(path));
                        } else {
                            issues.addAll(OpenAPIValidator.urlIssues(flow.getAuthorizationUrl(), true, path));
                        }
                        if (flow.getScopes() == null) {
                            issues.add(Issue.generateScopesRequiredForOauth2ForClientCredentialsIssue(path));
                        }
                        if (flow.getAuthorizationUrl() != null){
                            issues.add(Issue.generateAuthUrlRequiredForClientCredentialsIssue(path));
                        }
                    }
                    path.remove(path.size()-1);
                    break;
                case OPENIDCONNECT:
                default:
                    issues.add(Issue.generateUnknownAuthMethodIssue(path));
                    break;
            }

            path.remove(path.size()-1);
        }

        path.remove(path.size() - 1);
        return issues;
    }

}
