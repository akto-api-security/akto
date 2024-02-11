package com.akto.oas;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariables;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenAPIValidator {
    public List<io.swagger.v3.oas.models.tags.Tag> definedTags = new ArrayList<>();

    public List<Issue> validateMain(String openAPIString) {
        SwaggerParseResult result = new OpenAPIParser().readContents(openAPIString, null, null);
        OpenAPI openAPI = result.getOpenAPI();
        return validateOpenAPI(openAPI, new ArrayList<>());
    }

    public List<Issue> validateOpenAPI(OpenAPI openAPI, List<String> path){
        List<Issue> issues = new ArrayList<>();

        if (openAPI == null) {
            issues.add(Issue.generateOpenApiIsNullIssue(path));
            return issues;
        }

        Map<String, SecurityScheme> securitySchemeMap = new HashMap<>();
        if (openAPI.getComponents()!=null && openAPI.getComponents().getSecuritySchemes() != null) {
            securitySchemeMap = openAPI.getComponents().getSecuritySchemes();
        }

        path.add(PathComponent.OPEN_API);
        // TODO: check for version 3.0
        path.remove(path.size()-1);

        issues.addAll(validateServers(openAPI.getServers(),true,path));

        issues.addAll(validateComponents(openAPI.getComponents(),openAPI.getSecurity(), path));

        issues.addAll(validateSecurity(openAPI.getSecurity(), securitySchemeMap,true, path));

        path.add(PathComponent.TAGS);
        issues.addAll(validateTags(openAPI.getTags(), path));
        path.remove(path.size()-1);

        path.add(PathComponent.PATHS);
        issues.addAll(validatePaths(openAPI.getPaths(), securitySchemeMap, path));
        path.remove(path.size()-1);


        return issues;
    }

    public List<Issue> validateTags(List<io.swagger.v3.oas.models.tags.Tag> tags, List<String> path) {
        List<Issue> issues = new ArrayList<>();
        if (tags == null) {
            tags = new ArrayList<>();
        }
        for (io.swagger.v3.oas.models.tags.Tag tag: tags) {
            this.definedTags.add(tag);
            if (tag.getName() == null)  {
                issues.add(Issue.generateTagNameNullIssue(path));
            }
        }
        return issues;
    }

    public static List<Issue> validateComponents(Components components, List<SecurityRequirement> securityRequirements, List<String> path) {
        List<Issue> issues = new ArrayList<>();

        if (components == null) {
            issues.add(Issue.generateComponentsNullIssue(path));
            return issues;
        }

        path.add(PathComponent.COMPONENTS);

        path.add(PathComponent.SCHEMAS);
        Map<String, Schema> schemaMap = components.getSchemas();
        if (schemaMap == null) schemaMap = new HashMap<>();
        for (String schemaName: schemaMap.keySet()) {
            path.add(schemaName);
            issues.addAll(SchemaValidator.validate(schemaMap.get(schemaName), path));
            path.remove(path.size()-1);
        }
        path.remove(path.size()-1);

//        validateResponses(components.getResponses(),null,path);
//        components.getParameters();
//        components.getExamples();
//        components.getRequestBodies();
//        components.getHeaders();

        issues.addAll(SecuritySchemesValidator.validate(components.getSecuritySchemes(), path));

//        components.getLinks();
//        components.getCallbacks();


        path.remove(path.size()-1);
        return issues;
    }

    public static List<Issue> validateSecurity(List<SecurityRequirement> securityRequirements,
                                               Map<String, SecurityScheme> securitySchemeMap, boolean globalSecurity,
                                               List<String> path) {
        List<Issue> issues = new ArrayList<>();

        if (securityRequirements == null)  {
            if (globalSecurity) {
                issues.add(Issue.generateGlobalSecurityNotDefinedIssue(path));
            }
            return issues;
        }

        path.add(PathComponent.SECURITY);
        if (securityRequirements.size() == 0) {
            issues.add(Issue.generateSecurityFieldEmptyArrayIssue(path));
        }

        if (securitySchemeMap == null) securitySchemeMap = new HashMap<>();

        int index = 0;
        for (SecurityRequirement securityRequirement: securityRequirements) {
            path.add(index+"");

            if (securityRequirement==null || securityRequirement.isEmpty()) {
                issues.add(Issue.generateEmptySecurityRequirementIssue(path));
                index += 1;
                path.remove(path.size()-1);
                continue;
            }

            for (String securityName: securityRequirement.keySet()) {
                path.add(securityName);
                SecurityScheme scheme = securitySchemeMap.get(securityName);
                if (scheme==null) {
                    issues.add(Issue.generateSecuritySchemeNotDefined(securityName,path));
                    path.remove(path.size()-1);
                    continue;
                }

                List<String> scopes = securityRequirement.get(securityName);
                SecurityScheme.Type type =  scheme.getType();
                if (type == SecurityScheme.Type.OAUTH2 || type == SecurityScheme.Type.OPENIDCONNECT) {
                    Set<String> scopesDefined = new HashSet<>();
                    OAuthFlow oAuthFlow;
                    // TODO: nulls
                    // gathering all scopes from security schema
                    // currently not discriminating among different scope's parent type like authCode or Implicit
                    if ((oAuthFlow=scheme.getFlows().getAuthorizationCode())!=null) {
                        scopesDefined.addAll(oAuthFlow.getScopes().keySet());
                    }
                    if ((oAuthFlow=scheme.getFlows().getClientCredentials())!=null) {
                        scopesDefined.addAll(oAuthFlow.getScopes().keySet());
                    }
                    if ((oAuthFlow=scheme.getFlows().getImplicit())!=null) {
                        scopesDefined.addAll(oAuthFlow.getScopes().keySet());
                    }
                    if ((oAuthFlow=scheme.getFlows().getPassword())!=null) {
                        scopesDefined.addAll(oAuthFlow.getScopes().keySet());
                    }

                    int i = 0;
                    for (String scopeName: scopes) {
                        path.add(i+"");
                        if (!scopesDefined.contains(scopeName)) {
                            issues.add(Issue.generateScopeNotDefinedSecuritySchema(scopeName,securityName,path));
                        }
                        i += 1;
                        path.remove(path.size()-1);
                    }
                } else {
                    if (!scopes.isEmpty()) {
                        issues.add(Issue.generateScopesNotDefinedForType(type + "", path));
                    }
                }
                path.remove(path.size()-1);
            }
            index += 1;
            path.remove(path.size()-1);
        }
        path.remove(path.size()-1);
        return issues;
    }

    public static String convertToPathNameToKey(String pathName) {
        pathName = pathName.replaceAll("\\{.*?}", "x");
        return pathName;
    }

    public List<Issue> validatePaths(Paths paths,Map<String, SecurityScheme> securitySchemeMap, List<String> path) {
        // TODO: null paths
        List<Issue> issues = new ArrayList<>();
        Map<String, String> store = new HashMap<>();
        String key;
        for (String pathName: paths.keySet()) {
            path.add(pathName);
            key = convertToPathNameToKey(pathName);
            if (store.get(key) != null){
                issues.add(Issue.generateIdenticalPaths(pathName,store.get(key),path));
            } else {
                store.put(key, pathName);
            }

            issues.addAll(PathItemValidator.validate(paths.get(pathName), this.definedTags, pathName,securitySchemeMap, path));
            path.remove(path.size()-1);
        }
        return issues;
    }

    public static String generateURL(String url, ServerVariables serverVariables) {
        if (serverVariables == null || url == null) return url;

        for (String variableName: serverVariables.keySet()) {
            String defaultValue = serverVariables.get(variableName).getDefault();
            if (defaultValue != null) {
                url = url.replaceAll("\\{"+variableName+"}", defaultValue);
            }
        }
        return url;
    }

    public static List<Issue> validateServers(List<Server> servers, boolean isGlobal, List<String> path) {
        List<Issue> issues = new ArrayList<>();

        if (servers == null) {
            if (isGlobal) {
                issues.add(Issue.generateGlobalServerIsNull(path));
            }
            return issues;
        }

        path.add(PathComponent.SERVERS);

        int x = 0;
        for (Server server: servers) {
            path.add(String.valueOf(x));
            path.add(PathComponent.URL);
            String finalUrl = generateURL(server.getUrl(), server.getVariables());
            issues.addAll(urlIssues(finalUrl,isGlobal, path));
            path.remove(path.size()-1);

            path.remove(path.size()-1);
            x+=1;
        }

        path.remove(path.size()-1);
        return issues;
    }

    public static List<Issue> urlIssues(String urlString, boolean absoluteUrlRequired, List<String> path) {
        List<Issue> issues = new ArrayList<>();
        Pattern curlyBracesPattern = Pattern.compile("\\{(.*?)\\}");
        Matcher matchPattern = curlyBracesPattern.matcher(urlString);
        String missingParameter = null;
        while(matchPattern.find()) {
            missingParameter = matchPattern.group(1);
            issues.add(Issue.generateMissingParameterInServerUrl(missingParameter, path));
        }
        if (missingParameter != null) return issues;
        try {
            // TODO: URL makes network connection so find better way
            URL url = new URL(urlString);
            if (!Objects.equals(url.getProtocol(), "https")) {
                issues.add(Issue.generateHttpIssue(path));
            }
        } catch (MalformedURLException e) {
            if (absoluteUrlRequired) {
                issues.add(Issue.generateUseAbsoluteUrl(path));
            }
        }

        return issues;
    }



}
