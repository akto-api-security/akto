package com.akto.oas;

import java.util.ArrayList;
import java.util.List;

public class Issue {
    private String message;
    private int issueCode;
    private List<String> path;
    private Type type;

    public enum Type {
        DATA_VALIDATION, SECURITY, STRUCTURE;
    }

    private Severity severity;
    private String description;
    private String remedy;

    public enum Severity {
        Low,
        Medium,
        High,
    }

    private Issue(String message, int issueCode, List<String> path, Type type) {
        this.message = message;
        this.issueCode = issueCode;
        this.path = new ArrayList<>(path);
        this.type = type;
        this.severity = (type == Type.SECURITY) ? Severity.High : (type == Type.DATA_VALIDATION ? Severity.Medium : Severity.Low); 
    }

    public static Issue generateOpenApiIsNullIssue(List<String> path) {
        return new Issue("Open api is null", 0, path, Type.STRUCTURE);
    }

    public static Issue generateGlobalSecurityNotDefinedIssue(List<String> path) {
        return new Issue("Global 'security' field is not defined",1,path, Type.SECURITY);
    }

    public static Issue generateTagNameNullIssue(List<String> path) {
        return new Issue("Tag name can't be null",2,path, Type.STRUCTURE);
    }

    public static Issue generateComponentsNullIssue(List<String> path) {
        return new Issue("Components can't be null",3,path, Type.STRUCTURE);
    }

    public static Issue generateSecurityFieldEmptyArrayIssue(List<String> path) {
        return new Issue("'Security' field contains an empty array",4, path, Type.SECURITY);
    }

    public static Issue generateEmptySecurityRequirementIssue(List<String> path) {
        return new Issue("'Security' field contains an empty security requirement",5, path, Type.SECURITY);
    }

    public static Issue generateSecuritySchemeNotDefined(String securityName, List<String> path) {
        return new Issue(securityName + " not defined in securitySchemes",6,path, Type.SECURITY);
    }

    public static Issue generateScopeNotDefinedSecuritySchema(String scopeName, String securityName, List<String> path) {
        return new Issue("Scope " + scopeName + " not defined in security schema of " + securityName,7,path, Type.SECURITY);
    }

    public static Issue generateScopesNotDefinedForType(String type, List<String> path) {
        return new Issue("Scopes are not defined for " + type,8,path, Type.SECURITY);
    }

    public static Issue generateIdenticalPaths(String pathName, String identicalPathName, List<String> path) {
        return new Issue("Path '" + pathName + "' and '" + identicalPathName + "' are identical", 9, path, Type.SECURITY);
    }

    public static Issue generateGlobalServerIsNull(List<String> path) {
        return new Issue("Global server field is null/empty", 10,path, Type.STRUCTURE);
    }

    public static Issue generateHttpIssue(List<String> path) {
        return new Issue("Use https",11,path, Type.SECURITY);
    }

    public static Issue generateUseAbsoluteUrl(List<String> path) {
        return new Issue("Use absolute url",12,path, Type.SECURITY);
    }

    public static Issue generateMissingParameterInServerUrl(String missingParameter, List<String> path) {
        return new Issue("Missing parameter "+missingParameter+" in server url",13,path, Type.STRUCTURE);
    }

    public static Issue generateNoSchemaIssue(List<String> path) {
        return new Issue("No schema defined", 14, path, Type.STRUCTURE);
    }

    public static Issue generateSchemaTypeNeededIssue(List<String> path) {
        return new Issue("Type needed", 15,path, Type.STRUCTURE);
    }

    public static Issue generateRefFlagIssue(String field, List<String> path) {
        return new Issue("Field " + field + " is not to be used when $ref is mentioned",16,path, Type.STRUCTURE);
    }

    public static Issue generateSchemaFormatIssue(List<String> path) {
        return new Issue("Please define valid format",17, path, Type.DATA_VALIDATION);
    }

    public static Issue generateUnknownNumberFormat(List<String> path) {
        return new Issue("Unknown number format",18, path, Type.DATA_VALIDATION);
    }

    public static Issue generateUnknownIntegerFormat(List<String> path) {
        return new Issue("Unknown integer format",19, path, Type.DATA_VALIDATION);
    }

    public static Issue generateSchemaMaxLengthIssue(List<String> path) {
        return new Issue("String schema needs maxLength field", 20, path, Type.DATA_VALIDATION);
    }

    public static Issue generateSchemaMaxLengthNotDefinedIssue(String type, List<String> path) {
        return new Issue("Max length field is not defined for " + type, 21, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePatternRequiredIssue(List<String> path) {
        return new Issue("String schema need pattern field",22, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePatternNotRequiredIssue(String type, List<String> path) {
        return new Issue("Pattern is not defined for " + type, 23,path, Type.DATA_VALIDATION);
    }

    public static Issue generateMaxItemsRequiredIssue(List<String> path) {
        return new Issue("Array schema need max items field",24, path, Type.DATA_VALIDATION);
    }

    public static Issue generateMaxItemsNotRequiredIssue(String type, List<String> path) {
        return new Issue("maxItems is not defined for " + type, 25,path, Type.DATA_VALIDATION);
    }

    public static Issue generatePropertiesRequired(List<String> path) {
        return new Issue("Schema of a JSON object has no properties defined", 26, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePropertiesNotRequired(String type, List<String> path) {
        return new Issue("Properties is not defined for " + type, 27, path, Type.DATA_VALIDATION);
    }

    public static Issue generateMaxMinRequiredIssue(String type, String maxOrMin, List<String> path) {
        return new Issue(type + " needs " + maxOrMin,28, path, Type.DATA_VALIDATION);
    }

    public static Issue generateMaxMinNotRequiredIssue(String maxOrMin, String type, List<String> path) {
        return new Issue(maxOrMin+ " is not defined for " + type,29, path, Type.DATA_VALIDATION);
    }

    public static Issue generateSecuritySchemeNotDefinedIssue(List<String> path) {
        return new Issue("Reusable security scheme is not defined",30, path, Type.SECURITY);
    }

    public static Issue generateSecuritySchemeNullIssue(List<String> path) {
        return new Issue("Security Scheme object cannot be null",31, path, Type.SECURITY);
    }

    public static Issue generateSecuritySchemeTypeNullIssue(List<String> path) {
        return new Issue("Type can't be null", 32,path, Type.SECURITY);
    }

    public static Issue generateBasicAuthTransportedOverNetworkIssue(List<String> path) {
        return new Issue("Basic authentication credentials transported over network", 33, path, Type.SECURITY);
    }

    public static Issue generateApiNegotiatesAuthIssue(List<String> path) {
        return new Issue("API negotiates authentication",34,path, Type.SECURITY);
    }

    public static Issue generateOauth1AllowedIssue(List<String> path) {
        return new Issue("OAuth 1.0 authentication allowed", 35, path, Type.SECURITY);
    }

    public static Issue generateDigestAuthAllowedIssue(List<String> path) {
        return new Issue("Transporting digest authentication credentials over network allowed", 36, path, Type.SECURITY);
    }

    public static Issue generateUnknownHTTPSchemeIssue(List<String> path) {
        return new Issue("Unknown HTTP authentication scheme used",37, path, Type.SECURITY);
    }

    public static Issue generateInRequiredIssue(List<String> path) {
        return new Issue("In required",38,path, Type.STRUCTURE);
    }

    public static Issue generateSecuritySchemeNameRequiredIssue(List<String> path) {
        return new Issue("Name required",39,path, Type.SECURITY);
    }

    public static Issue generateAPIKeysTransportedOverNetworkIssue(List<String> path) {
        return new Issue("API keys are being transported over network",40,path, Type.SECURITY);
    }

    public static Issue generateNeedFlowsForOauth2Issue(List<String> path) {
        return new Issue("Need flows for oauth2",41,path, Type.SECURITY);
    }

    public static Issue generateAuthUrlRequiredForAuthCodeFlowIssue(List<String> path) {
        return new Issue("Property 'authorizationUrl' must be defined for the 'authorizationCode' flow OAuth2 security schemes", 42,path, Type.SECURITY);
    }

    public static Issue generateTokenUrlRequiredForOauth2ForAuthFlowIssue(List<String> path) {
        return new Issue("Property 'tokenUrl' must be defined for the 'authorizationCode' flow OAuth2 security schemes", 43, path, Type.SECURITY);
    }

    public static Issue generateScopesRequiredForOauth2ForAuthFlowIssue(List<String> path) {
        return new Issue("Property 'scopes' must be defined for the 'authorizationCode' flow OAuth2 security schemes", 44,path, Type.SECURITY);
    }

    public static Issue generateTokenUrlRequiredForOauth2ForPasswordFlowIssue(List<String> path) {
        return new Issue("Property 'tokenUrl' must be defined for the 'password' flow OAuth2 security schemes", 45,path, Type.SECURITY);
    }

    public static Issue generateScopesRequiredForOauth2ForPasswordFlowIssue(List<String> path) {
        return new Issue("Property 'scopes' must be defined for the 'password' flow OAuth2 security schemes", 46,path, Type.SECURITY);
    }

    public static Issue generateAuthUrlRequiredForPasswordFlowIssue(List<String> path) {
        return new Issue("Property 'authorizationUrl' must not be defined for the 'password' flow OAuth2 security schemes", 47,path, Type.SECURITY);
    }

    public static Issue generateResourceOwnerPasswordFlowAllowedIssue(List<String> path) {
        return new Issue("Resource owner password grant flow in OAuth2 authentication allowed", 48,path, Type.SECURITY);
    }

    public static Issue generateAuthUrlRequiredForImplicitFlowIssue(List<String> path) {
        return new Issue("Property 'authorizationUrl' must be defined for the 'implicit' flow OAuth2 security schemes", 49,path, Type.SECURITY);
    }

    public static Issue generateScopesRequiredForOauth2ForImplicitlowIssue(List<String> path) {
        return new Issue("Property 'scopes' must be defined for the 'implicit' flow OAuth2 security schemes", 50,path, Type.SECURITY);
    }

    public static Issue generateTokenUrlRequiredForOauth2ForImplicitFlowIssue(List<String> path) {
        return new Issue("Property 'tokenUrl' must not be defined for the 'implicit' flow OAuth2 security schemes", 51,path, Type.SECURITY);
    }

    public static Issue generateUserImplicitFlowIssue(List<String> path) {
        return new Issue("Operation uses implicit grant flow in OAuth2 authentication", 52,path, Type.SECURITY);
    }

    public static Issue generateTokenUrlRequiredForOauth2ForClientCredFlowIssue(List<String> path) {
        return new Issue("Property 'tokenUrl' must be defined for the 'clientCredentials' flow OAuth2 security schemes", 53,path, Type.SECURITY);
    }

    public static Issue generateScopesRequiredForOauth2ForClientCredentialsIssue(List<String> path) {
        return new Issue("Property 'scopes' must be defined for the 'clientCredentials' flow OAuth2 security schemes", 54,path, Type.SECURITY);
    }

    public static Issue generateAuthUrlRequiredForClientCredentialsIssue(List<String> path) {
        return new Issue("Property 'authorizationUrl' must not be defined for the 'clientCredentials' flow OAuth2 security schemes", 55,path, Type.SECURITY);
    }

    public static Issue generateUnknownAuthMethodIssue(List<String> path) {
        return new Issue("Unknown HTTP authentication method used",56, path, Type.SECURITY);
    }

    public static Issue generate401Issue(List<String> path) {
        return new Issue("If operation has security defined, the 401 response should be defined",57, path, Type.DATA_VALIDATION);
    }

    public static Issue generate403Issue(List<String> path) {
        return new Issue("If operation has security defined, the 403 response should be defined", 58, path, Type.DATA_VALIDATION);
    }

    public static Issue generate400Issue(List<String> path) {
        return new Issue("400 response should be defined", 59, path, Type.DATA_VALIDATION);
    }

    public static Issue generate429Issue(List<String> path) {
        return new Issue("429 response should be defined", 60, path, Type.DATA_VALIDATION) ;
    }

    public static Issue generate500Issue(List<String> path) {
        return new Issue("500 response should be defined", 61, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePOST20xIssue(List<String> path) {
        return new Issue("At least one 200, 201, 202, or 204 response should be defined for POST operations", 62, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePost406Issue(List<String> path) {
        return new Issue("406 response should be defined for POST operations", 63, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePost415Issue(List<String> path) {
        return new Issue("415 response should be defined for POST operations", 64, path, Type.DATA_VALIDATION);
    }

    public static Issue generateGet20xIssue(List<String> path) {
        return new Issue("200 or 202 response should be defined for all GET operations", 65, path, Type.DATA_VALIDATION);
    }

    public static Issue generateGet404Issue(List<String> path) {
        return new Issue("404 response should be defined for GET operations", 66, path, Type.DATA_VALIDATION);
    }

    public static Issue generateGet406Issue(List<String> path) {
        return new Issue("406 response should be defined for GET operations", 67, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePut20xIssue(List<String> path) {
        return new Issue("At least one 200, 201, 202, or 204 response should be defined for PUT operations", 68, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePut404Issue(List<String> path) {
        return new Issue("404 response should be defined for PUT operations", 69, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePut415Issue(List<String> path) {
        return new Issue("415 response should be defined for PUT operations", 70, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePatch20xIssue(List<String> path) {
        return new Issue("At least one 200, 201, 202, or 204 response should be defined for PATCH operations", 71, path, Type.DATA_VALIDATION);
    }

    public static Issue generatePatch406Issue(List<String> path) {
        return new Issue("406 response should be defined for PATCH operations", 72, path, Type.DATA_VALIDATION);
    }
    public static Issue generatePatch415Issue(List<String> path) {
        return new Issue("415 response should be defined for PATCH operations", 73, path, Type.DATA_VALIDATION);
    }

    public static Issue generateDelete20xIssue(List<String> path) {
        return new Issue("At least one 200, 201, 202, or 204 response should be defined for DELETE operations", 74, path, Type.DATA_VALIDATION);
    }

    public static Issue generateDelete404Issue(List<String> path) {
        return new Issue("404 response should be defined for DELETE operations", 75, path, Type.DATA_VALIDATION);
    }

    public static Issue generateDelete406Issue(List<String> path) {
        return new Issue("406 response should be defined for DELETE operations", 76, path, Type.DATA_VALIDATION);
    }

    public static Issue generateHead404Issue(List<String> path) {
        return new Issue("404 response should be defined for HEAD operations", 77, path, Type.DATA_VALIDATION);
    }

    public static Issue generateHead20xIssue(List<String> path) {
        return new Issue("200 or 202 response should be defined for all HEAD operations", 78, path, Type.DATA_VALIDATION);
    }

    public static Issue generateOptions200Issue(List<String> path) {
        return new Issue("200 response should be defined for OPTIONS operations", 79, path, Type.DATA_VALIDATION);
    }

    public static Issue generateTrace200Issue(List<String> path) {
        return new Issue("200 response should be defined for TRACE operations", 80, path, Type.DATA_VALIDATION);
    }

    public static Issue generateParameterInFieldIssue(List<String> path) {
        return new Issue("In field required",81, path, Type.STRUCTURE);
    }

    public static Issue generateParameterPropertyIssue(List<String> path) {
        return new Issue("Invalid In property",82, path, Type.STRUCTURE);
    }

    public static Issue generatePathParamRequiredIssue(List<String> path) {
        return new Issue("path parameters must have required: true",83, path, Type.STRUCTURE);
    }

    public static Issue generateDuplicateParameterIssue(List<String> path) {
        return new Issue("Duplicate parameter",84,path, Type.STRUCTURE);
    }

    public static Issue generateNoCorrespondingPathTemplateIssue(String name, String pathName, List<String> path) {
        return new Issue("Path parameter '"+name+"' has no corresponding path template in the path '"+pathName+"'",85,path, Type.STRUCTURE);
    }

    public static Issue generateNoCorrespondingPathParamIssue(String in, String par, List<String> path) {
        return new Issue(in + " template '" + par + "' has no corresponding " + in + " parameter" + " defined",86,path, Type.STRUCTURE);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getIssueCode() {
        return issueCode;
    }

    public void setIssueCode(int issueCode) {
        this.issueCode = issueCode;
    }

    public List<String> getPath() {
        return path;
    }

    public void setPath(List<String> path) {
        this.path = path;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRemedy() {
        return remedy;
    }

    public void setRemedy(String remedy) {
        this.remedy = remedy;
    }

    public Type getType() {
        return this.type;
    }

    public void setType(Type type) {
        this.type = type;
    }
}