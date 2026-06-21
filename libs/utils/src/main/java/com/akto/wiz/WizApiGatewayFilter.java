package com.akto.wiz;

import com.mongodb.BasicDBObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WizApiGatewayFilter {

    // Standard convention: {service}-[core-]{env}[-dr][-qualifier]-api (no dots — rejects domain names)
    // The optional qualifier (explicit list: public, internal, external, private) is stripped from the
    // canonical key so variants like foo-dev-public-api merge into the same group as foo-dev-api.
    private static final Pattern GATEWAY_NAME_PATTERN = Pattern.compile(
        "^(?!.*\\.)(?<service>.+?)-(?:(?<core>core)-)?(?<env>[^-]+)(?:-(?<dr>dr))?(?:-(?<qualifier>public|internal|external|private))?-api$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> KNOWN_ENV_TOKENS = new HashSet<>(Arrays.asList(
        "dev", "qa", "prd", "prod", "uat", "stg", "piisecurenonprod", "piisecureprod"
    ));
    // Standard convention with DR placed before the env token: {service}[-core-]dr-{env}-api
    private static final Pattern GATEWAY_NAME_DR_BEFORE_ENV_PATTERN = Pattern.compile(
        "^(?!.*\\.)(?<service>.+?)-(?:(?<core>core)-)?(?<dr>dr)-(?<env>[^-]+)-api$",
        Pattern.CASE_INSENSITIVE
    );
    // Non-standard: env token in the middle, e.g. BigData-DEV-ODS-API
    private static final Pattern GATEWAY_NAME_MID_ENV_PATTERN = Pattern.compile(
        "^(?<prefix>.+?)-(?<env>DEV|QA|PRD|PROD|UAT|STG)-(?<suffix>.+)$",
        Pattern.CASE_INSENSITIVE
    );
    // Non-standard: env token at the end, e.g. ODS-API-DEV
    private static final Pattern GATEWAY_NAME_END_ENV_PATTERN = Pattern.compile(
        "^(?<prefix>.+)-(?<env>DEV|QA|PRD|PROD|UAT|STG)$",
        Pattern.CASE_INSENSITIVE
    );
    // Suffixes that identify auto-generated cloud API gateway hostnames.
    // Named/custom hosts are preferred over these in host-resolution logic.
    // Add new provider suffixes here as needed.
    private static final List<String> GATEWAY_HOST_SUFFIXES = Arrays.asList(
        ".execute-api."  // AWS API Gateway: *.execute-api.<region>.amazonaws.com[.cn]
    );

    public static boolean isGatewayHost(String host) {
        if (host == null) return false;
        String lower = host.toLowerCase();
        return GATEWAY_HOST_SUFFIXES.stream().anyMatch(lower::contains);
    }

    /**
     * Extracts {canonicalKey, envLower, isDr} from a gateway name using four patterns in priority order:
     *   1. DR-before-env: {service}[-core-]dr-{env}-api    e.g. customer-dr-prod-api
     *   2. Standard:      {service}[-core-]{env}[-dr][-qualifier]-api  (env must be a known token)
     *                     e.g. customer-dev-public-api → canonical customer-dev (qualifier stripped)
     *   3. Mid-env:       {prefix}-{ENV}-{suffix}          e.g. CUSTOMER-DEV-ASIA-API
     *   4. End-env:       {prefix}-{ENV}                   e.g. ASIA-API-DEV
     * DR-before-env runs first to prevent the standard pattern from absorbing "dr" into the service name.
     * Non-standard env tokens are normalised: piisecurenonprod → qa, piisecureprod → prod.
     * Returns null for gateway names that match none of the above.
     */
    static String[] extractCanonicalAndEnv(String gatewayName) {
        Matcher m = GATEWAY_NAME_DR_BEFORE_ENV_PATTERN.matcher(gatewayName);
        if (m.matches() && KNOWN_ENV_TOKENS.contains(m.group("env").toLowerCase())) {
            String canonical = m.group("service") + (m.group("core") != null ? "-core" : "");
            return new String[]{canonical, normaliseEnv(m.group("env")), "true"};
        }
        m = GATEWAY_NAME_PATTERN.matcher(gatewayName);
        if (m.matches() && KNOWN_ENV_TOKENS.contains(m.group("env").toLowerCase())) {
            String canonical = m.group("service") + (m.group("core") != null ? "-core" : "");
            return new String[]{canonical, normaliseEnv(m.group("env")), m.group("dr") != null ? "true" : "false"};
        }
        m = GATEWAY_NAME_MID_ENV_PATTERN.matcher(gatewayName);
        if (m.matches()) {
            return new String[]{m.group("prefix") + "-" + m.group("suffix"), normaliseEnv(m.group("env")), "false"};
        }
        m = GATEWAY_NAME_END_ENV_PATTERN.matcher(gatewayName);
        if (m.matches()) {
            return new String[]{m.group("prefix"), normaliseEnv(m.group("env")), "false"};
        }
        return null;
    }

    private static String normaliseEnv(String env) {
        switch (env.toLowerCase()) {
            case "piisecurenonprod": return "qa";
            case "piisecureprod":    return "prod";
            default:                 return env.toLowerCase();
        }
    }

    /**
     * Scans all endpoint metadata and builds a map from canonical service key → QA host.
     * Named hosts are preferred over execute-api hosts; non-DR over DR.
     */
    public static Map<String, String> buildCanonicalKeyToQaHost(List<BasicDBObject> allEndpointMeta) {
        Map<String, String> gatewayNameToNamedHost = new HashMap<>();
        Map<String, String> gatewayNameToGatewayHost = new HashMap<>();

        for (BasicDBObject meta : allEndpointMeta) {
            String host = meta.getString("host");
            List<?> relatedResources = (List<?>) meta.get("relatedResources");
            if (host == null || relatedResources == null) continue;
            for (Object r : relatedResources) {
                BasicDBObject resource = (BasicDBObject) r;
                if ("API_GATEWAY".equals(resource.getString("type"))) {
                    String gatewayName = resource.getString("name");
                    if (gatewayName != null) {
                        if (isGatewayHost(host)) gatewayNameToGatewayHost.put(gatewayName, host);
                        else gatewayNameToNamedHost.put(gatewayName, host);
                    }
                }
            }
        }

        Map<String, String> canonicalKeyToQaHost = new HashMap<>();
        // pass 1: named hosts (preferred)
        for (Map.Entry<String, String> entry : gatewayNameToNamedHost.entrySet()) {
            String[] parts = extractCanonicalAndEnv(entry.getKey());
            if (parts == null || !"qa".equals(parts[1])) continue;
            boolean isDr = "true".equals(parts[2]);
            if (!canonicalKeyToQaHost.containsKey(parts[0]) || !isDr) {
                canonicalKeyToQaHost.put(parts[0], entry.getValue());
            }
        }
        // pass 2: execute-api fallback for services that have no named QA host
        for (Map.Entry<String, String> entry : gatewayNameToGatewayHost.entrySet()) {
            String[] parts = extractCanonicalAndEnv(entry.getKey());
            if (parts == null || !"qa".equals(parts[1])) continue;
            String existing = canonicalKeyToQaHost.get(parts[0]);
            if (existing != null && !isGatewayHost(existing)) continue; // named host already wins
            boolean isDr = "true".equals(parts[2]);
            if (existing == null || !isDr) canonicalKeyToQaHost.put(parts[0], entry.getValue());
        }

        return canonicalKeyToQaHost;
    }

    /**
     * Returns the QA host to use for an endpoint, or null if no replacement is needed.
     * Named hosts may only be replaced with another named host, not an execute-api host.
     */
    public static String resolveQaHost(String host, List<?> relatedResources, Map<String, String> canonicalKeyToQaHost) {
        if (relatedResources == null) return null;
        for (Object r : relatedResources) {
            BasicDBObject resource = (BasicDBObject) r;
            if ("API_GATEWAY".equals(resource.getString("type"))) {
                String gatewayName = resource.getString("name");
                if (gatewayName != null) {
                    String[] parts = extractCanonicalAndEnv(gatewayName);
                    if (parts != null) {
                        String qaHost = canonicalKeyToQaHost.get(parts[0]);
                        if (qaHost != null && (!isGatewayHost(qaHost) || isGatewayHost(host))) {
                            return qaHost;
                        }
                    }
                }
                break;
            }
        }
        return null;
    }

    /**
     * Returns the API_GATEWAY name from relatedResources, or null if none is present.
     */
    public static String extractGatewayName(List<?> relatedResources) {
        if (relatedResources == null) return null;
        for (Object r : relatedResources) {
            BasicDBObject resource = (BasicDBObject) r;
            if ("API_GATEWAY".equals(resource.getString("type"))) {
                return resource.getString("name");
            }
        }
        return null;
    }

    /**
     * Builds the Wiz tags JSON string for an Akto message.
     * Always includes source=wiz; appends api-gateway=<name> when the message host
     * is found in hostToGatewayName.
     */
    public static String buildWizTagsJson(String requestHeadersStr, Map<String, String> hostToGatewayName) {
        BasicDBObject tagsMap = new BasicDBObject();
        tagsMap.put("source", "wiz");
        if (requestHeadersStr != null && hostToGatewayName != null) {
            try {
                BasicDBObject requestHeaders = BasicDBObject.parse(requestHeadersStr);
                String msgHost = null;
                for (String key : requestHeaders.keySet()) {
                    if ("host".equalsIgnoreCase(key)) {
                        msgHost = requestHeaders.getString(key);
                        break;
                    }
                }
                if (msgHost != null) {
                    String gatewayName = hostToGatewayName.get(msgHost);
                    if (gatewayName != null) {
                        tagsMap.put("api-gateway", gatewayName);
                    }
                }
            } catch (Exception ignored) {}
        }
        return tagsMap.toJson();
    }
}
