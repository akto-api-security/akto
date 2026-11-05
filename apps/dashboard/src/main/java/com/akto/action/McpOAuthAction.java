package com.akto.action;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import com.akto.dao.McpOAuthClientsDao;
import com.akto.dao.McpOAuthGrantsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.McpOAuthClient;
import com.akto.dto.McpOAuthGrant;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.McpOAuthUtils;
import com.akto.util.McpTokenGenerator;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;

// Public OAuth 2.1 endpoints (register / authorize / token) used by MCP clients such as Claude
public class McpOAuthAction implements Action, ServletRequestAware, ServletResponseAware {

    private static final LoggerMaker logger = new LoggerMaker(McpOAuthAction.class, LogDb.DASHBOARD);

    private static final String CONSENT_PATH = "/dashboard/mcp-oauth/consent?request_id=";
    private static final String GRANT_AUTH_CODE = "authorization_code";
    private static final String GRANT_REFRESH = "refresh_token";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_REDIRECT_URI = "redirect_uri";
    private static final String PARAM_REDIRECT_URIS = "redirect_uris";
    private static final String PARAM_RESOURCE = "resource";
    private static final String PARAM_REFRESH_TOKEN = "refresh_token";
    private static final String RESPONSE_TYPE_CODE = "code";
    private static final String CHALLENGE_METHOD_S256 = "S256";
    private static final String ERROR_DESCRIPTION = "error_description";
    private static final String INVALID_REQUEST = "invalid_request";
    private static final String INVALID_GRANT = "invalid_grant";
    private static final String INVALID_TARGET = "invalid_target";
    private static final String INVALID_CLIENT_METADATA = "invalid_client_metadata";
    private static final String INVALID_REDIRECT_URI = "invalid_redirect_uri";
    private static final String SERVER_ERROR = "server_error";
    private static final int SECONDS_PER_DAY = 24 * 60 * 60;

    private HttpServletRequest servletRequest;
    private HttpServletResponse servletResponse;

    @Override
    public String execute() {
        return SUCCESS;
    }

    @FunctionalInterface
    private interface Handler {
        void run() throws Exception;
    }

    // Test seam for the MCP_PUBLIC_URL environment variable
    protected String configuredResource() {
        return McpOAuthUtils.configuredResource();
    }

    // The endpoints exist only when the MCP server URL is configured; unexpected failures answer in OAuth JSON
    private String handle(Handler handler) throws IOException {
        if (configuredResource() == null) {
            servletResponse.sendError(404);
            return null;
        }
        try {
            handler.run();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "MCP OAuth request failed");
            if (!servletResponse.isCommitted()) {
                writeError(500, SERVER_ERROR, "Unable to process the request");
            }
        }
        return null;
    }

    // POST /oauth/register (RFC 7591), public clients only
    public String register() throws IOException {
        return handle(this::doRegister);
    }

    private void doRegister() throws IOException {
        if (!isPost()) {
            rejectNonPost();
            return;
        }
        BasicDBObject body = parseRegistrationBody();
        if (body == null) {
            writeError(400, INVALID_CLIENT_METADATA, "Body must be a JSON object of at most " + McpOAuthUtils.MAX_REGISTER_BODY_CHARS + " characters");
            return;
        }

        List<String> redirectUris = toStringList(body.get(PARAM_REDIRECT_URIS));
        if (redirectUris == null || redirectUris.isEmpty() || redirectUris.size() > McpOAuthUtils.MAX_REDIRECT_URIS) {
            writeError(400, INVALID_REDIRECT_URI, "redirect_uris must be a non-empty list of at most " + McpOAuthUtils.MAX_REDIRECT_URIS);
            return;
        }
        for (String uri : redirectUris) {
            if (!McpOAuthUtils.isAllowedRedirectUri(uri)) {
                writeError(400, INVALID_REDIRECT_URI, "redirect_uris must be https, http on loopback, or a reverse-domain app scheme");
                return;
            }
        }
        if (!isSubset(body.get("grant_types"), GRANT_AUTH_CODE, GRANT_REFRESH) || !isSubset(body.get("response_types"), RESPONSE_TYPE_CODE)) {
            writeError(400, INVALID_CLIENT_METADATA, "Only the authorization_code and refresh_token grants with response type code are supported");
            return;
        }

        int now = Context.now();
        McpOAuthClient client = new McpOAuthClient(UUID.randomUUID().toString(), clientNameFrom(body), redirectUris, now, new Date());
        McpOAuthClientsDao.instance.insertOne(client);

        BasicDBList grantTypes = new BasicDBList();
        grantTypes.add(GRANT_AUTH_CODE);
        grantTypes.add(GRANT_REFRESH);
        BasicDBList responseTypes = new BasicDBList();
        responseTypes.add(RESPONSE_TYPE_CODE);
        writeJson(201, new BasicDBObject(PARAM_CLIENT_ID, client.getId())
                .append("client_id_issued_at", now)
                .append("client_name", client.getClientName())
                .append(PARAM_REDIRECT_URIS, redirectUris)
                .append("grant_types", grantTypes)
                .append("response_types", responseTypes)
                .append("token_endpoint_auth_method", "none"));
        return;
    }

    // GET /oauth/authorize: parks the request and sends the user to consent
    public String authorize() throws IOException {
        return handle(this::doAuthorize);
    }

    private void doAuthorize() throws IOException {
        McpOAuthClient client = McpOAuthClientsDao.instance.findById(param(PARAM_CLIENT_ID));
        String redirectUri = param(PARAM_REDIRECT_URI);
        if (redirectUri == null && client != null && client.getRedirectUris().size() == 1) {
            redirectUri = client.getRedirectUris().get(0);
        }
        // Never redirect to an unverified URI
        if (client == null || redirectUri == null || !matchesRegistered(client, redirectUri)) {
            servletResponse.sendError(400, "Unknown client or redirect_uri");
            return;
        }

        String state = param("state");
        String resource = param(PARAM_RESOURCE);
        String codeChallenge = param("code_challenge");
        // An oversized state is not echoed back
        if (McpOAuthUtils.exceedsParamLimit(state) || McpOAuthUtils.exceedsParamLimit(resource)) {
            redirectWithError(redirectUri, INVALID_REQUEST, null);
            return;
        }
        if (!RESPONSE_TYPE_CODE.equals(param("response_type"))) {
            redirectWithError(redirectUri, "unsupported_response_type", state);
            return;
        }
        if (!McpOAuthUtils.isValidCodeChallenge(codeChallenge) || !CHALLENGE_METHOD_S256.equals(param("code_challenge_method"))) {
            redirectWithError(redirectUri, INVALID_REQUEST, state);
            return;
        }
        String configuredResource = configuredResource();
        if (!McpOAuthUtils.isResourceAllowed(resource, configuredResource)) {
            redirectWithError(redirectUri, INVALID_TARGET, state);
            return;
        }

        McpOAuthClientsDao.instance.touch(client.getId());

        String requestSecret = McpOAuthUtils.newSecret();
        McpOAuthGrant request = McpOAuthUtils.newGrant(McpOAuthGrant.TYPE_REQUEST, requestSecret, McpOAuthUtils.REQUEST_TTL_SECONDS);
        request.setClientId(client.getId());
        request.setRedirectUri(redirectUri);
        request.setState(state);
        request.setCodeChallenge(codeChallenge);
        request.setResource(McpOAuthUtils.resolveResource(resource, configuredResource));
        McpOAuthGrantsDao.instance.insert(request);

        servletResponse.sendRedirect(CONSENT_PATH + URLEncoder.encode(requestSecret, StandardCharsets.UTF_8));
        return;
    }

    // POST /oauth/token: authorization_code and refresh_token grants
    public String token() throws IOException {
        return handle(this::doToken);
    }

    private void doToken() throws IOException {
        if (!isPost()) {
            rejectNonPost();
            return;
        }
        String grantType = param("grant_type");
        String clientId = param(PARAM_CLIENT_ID);
        if (clientId == null) {
            writeError(400, INVALID_REQUEST, "client_id is required");
            return;
        }

        McpOAuthGrant grant;
        if (GRANT_AUTH_CODE.equals(grantType)) {
            grant = redeemAuthorizationCode(clientId);
        } else if (GRANT_REFRESH.equals(grantType)) {
            grant = checkRefreshToken(clientId);
        } else {
            writeError(400, "unsupported_grant_type", "Only authorization_code and refresh_token are supported");
            return;
        }
        if (grant == null) {
            return;
        }

        // Removing a user from the account cuts them off at the next refresh
        User user = UsersDao.instance.findOne("login", grant.getUsername());
        if (user == null || user.getAccounts() == null || !user.getAccounts().containsKey(String.valueOf(grant.getAccountId()))) {
            writeError(400, INVALID_GRANT, "User no longer has access to this account");
            return;
        }
        String accessToken = McpTokenGenerator.generateOAuthToken(
                grant.getUsername(), grant.getAccountId(), grant.getContextSource(), grant.getResource(), clientId);
        if (accessToken == null) {
            writeError(500, SERVER_ERROR, "Unable to issue token");
            return;
        }
        // The refresh token is spent last and atomically, so concurrent refreshes yield one winner
        if (GRANT_REFRESH.equals(grantType)
                && McpOAuthGrantsDao.instance.consume(McpOAuthUtils.sha256Hex(param(PARAM_REFRESH_TOKEN)), McpOAuthGrant.TYPE_REFRESH) == null) {
            writeError(400, INVALID_GRANT, "Invalid or expired refresh token");
            return;
        }
        // Keeps a client that is still refreshing from expiring as unused
        McpOAuthClientsDao.instance.touch(clientId);
        respondWithTokens(grant, clientId, accessToken);
        return;
    }

    // Consumed before checking, so a failed attempt burns the code
    private McpOAuthGrant redeemAuthorizationCode(String clientId) throws IOException {
        String code = param(McpOAuthUtils.PARAM_CODE);
        if (code == null) {
            writeError(400, INVALID_REQUEST, "code is required");
            return null;
        }
        McpOAuthGrant grant = McpOAuthGrantsDao.instance.consume(McpOAuthUtils.sha256Hex(code), McpOAuthGrant.TYPE_CODE);
        // redirect_uri is optional if authorize defaulted it; it must match when sent
        String redirectUri = param(PARAM_REDIRECT_URI);
        if (grant == null || !clientId.equals(grant.getClientId())
                || (redirectUri != null && !redirectUri.equals(grant.getRedirectUri()))
                || !McpOAuthUtils.verifyPkce(param("code_verifier"), grant.getCodeChallenge())) {
            writeError(400, INVALID_GRANT, "Invalid or expired authorization code");
            return null;
        }
        return validResource(grant) ? grant : null;
    }

    // Peeks at the refresh token without spending it
    private McpOAuthGrant checkRefreshToken(String clientId) throws IOException {
        String refreshToken = param(PARAM_REFRESH_TOKEN);
        if (refreshToken == null) {
            writeError(400, INVALID_REQUEST, "refresh_token is required");
            return null;
        }
        McpOAuthGrant stored = McpOAuthGrantsDao.instance.find(McpOAuthUtils.sha256Hex(refreshToken), McpOAuthGrant.TYPE_REFRESH);
        if (stored == null || !clientId.equals(stored.getClientId())) {
            writeError(400, INVALID_GRANT, "Invalid or expired refresh token");
            return null;
        }
        return validResource(stored) ? stored : null;
    }

    // RFC 8707: the resource must match the authorized one
    private boolean validResource(McpOAuthGrant grant) throws IOException {
        if (McpOAuthUtils.isResourceAllowed(param(PARAM_RESOURCE), grant.getResource())) {
            return true;
        }
        writeError(400, INVALID_TARGET, "resource does not match the authorized resource");
        return false;
    }

    // Every redemption rotates the refresh token
    private void respondWithTokens(McpOAuthGrant grant, String clientId, String accessToken) throws IOException {
        String refreshSecret = McpOAuthUtils.newSecret();
        McpOAuthGrant refresh = McpOAuthUtils.newGrant(McpOAuthGrant.TYPE_REFRESH, refreshSecret, McpOAuthUtils.REFRESH_TTL_DAYS * SECONDS_PER_DAY);
        refresh.setClientId(clientId);
        refresh.setResource(grant.getResource());
        refresh.setUsername(grant.getUsername());
        refresh.setAccountId(grant.getAccountId());
        refresh.setContextSource(grant.getContextSource());
        McpOAuthGrantsDao.instance.insert(refresh);

        writeJson(200, new BasicDBObject("access_token", accessToken)
                .append("token_type", "Bearer")
                .append("expires_in", McpTokenGenerator.ACCESS_TOKEN_MINUTES * 60)
                .append(PARAM_REFRESH_TOKEN, refreshSecret));
    }

    // Null when the body is not a JSON object or exceeds the size limit
    private BasicDBObject parseRegistrationBody() throws IOException {
        // JSON is UTF-8; the servlet default mangles non-ASCII names
        servletRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[1024];
        BufferedReader reader = servletRequest.getReader();
        int read;
        while ((read = reader.read(buffer)) != -1) {
            body.append(buffer, 0, read);
            if (body.length() > McpOAuthUtils.MAX_REGISTER_BODY_CHARS) {
                return null;
            }
        }
        try {
            return BasicDBObject.parse(body.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String clientNameFrom(BasicDBObject body) {
        Object name = body.get("client_name");
        String clientName = name instanceof String ? ((String) name).trim() : "";
        if (clientName.isEmpty()) {
            return McpOAuthUtils.DEFAULT_CLIENT_NAME;
        }
        return clientName.length() > McpOAuthUtils.MAX_CLIENT_NAME ? clientName.substring(0, McpOAuthUtils.MAX_CLIENT_NAME) : clientName;
    }

    private static boolean matchesRegistered(McpOAuthClient client, String redirectUri) {
        for (String registered : client.getRedirectUris()) {
            if (McpOAuthUtils.redirectUriMatches(redirectUri, registered)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPost() {
        return "POST".equalsIgnoreCase(servletRequest.getMethod());
    }

    // The token and registration endpoints are POST-only (RFC 6749 3.2)
    private void rejectNonPost() throws IOException {
        servletResponse.setHeader("Allow", "POST");
        servletResponse.sendError(405);
    }

    private String param(String name) {
        String value = servletRequest.getParameter(name);
        return value == null || value.isEmpty() ? null : value;
    }

    private void redirectWithError(String redirectUri, String error, String state) throws IOException {
        servletResponse.sendRedirect(McpOAuthUtils.redirectWith(redirectUri, state, McpOAuthUtils.PARAM_ERROR, error));
    }

    private void writeError(int status, String error, String description) throws IOException {
        writeJson(status, new BasicDBObject(McpOAuthUtils.PARAM_ERROR, error).append(ERROR_DESCRIPTION, description));
    }

    private void writeJson(int status, BasicDBObject body) throws IOException {
        servletResponse.setStatus(status);
        servletResponse.setContentType("application/json");
        servletResponse.setCharacterEncoding("UTF-8");
        servletResponse.setHeader("Cache-Control", "no-store");
        servletResponse.setHeader("Pragma", "no-cache");
        PrintWriter writer = servletResponse.getWriter();
        writer.write(body.toJson());
        writer.flush();
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List)) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof String)) {
                return null;
            }
            result.add((String) item);
        }
        return result;
    }

    // Absent is fine; present must only contain supported values
    private static boolean isSubset(Object value, String... allowed) {
        if (value == null) {
            return true;
        }
        List<String> values = toStringList(value);
        return values != null && Arrays.asList(allowed).containsAll(values);
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.servletRequest = request;
    }

    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.servletResponse = response;
    }
}
