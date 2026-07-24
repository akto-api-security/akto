package com.akto.action;

import com.akto.dao.CopilotStudioIntegrationDao;
import com.akto.dao.OAuthStatesDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.AccountJobDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.CopilotStudioIntegration;
import com.akto.dto.OAuthState;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.CopilotOAuthAuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

public class CopilotStudioAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(CopilotStudioAction.class, LogDb.DASHBOARD);

    private static final String AUTH_ENDPOINT_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";
    private static final String SCOPE = "https://api.powerplatform.com/CopilotStudio.Copilots.Invoke offline_access";

    public static final String FLOW_TYPE_MULTI_ENV_SETUP = "COPILOT_STUDIO_MULTI_ENV_SETUP";
    private static final String FLOW_TYPE_TEST_ROLE_AUTH = "TEST_ROLE_AUTH";

    @Setter private String roleId;

    // Copilot Studio (Multi Environment) setup parameters
    @Setter private String tenantId;
    @Setter private String clientId;
    @Setter private String clientSecret;
    @Setter private String dataIngestionUrl;
    @Setter private String integrationId;

    @Getter private String authorizationUrl;
    @Getter private CopilotStudioIntegration integration;
    @Getter private String jobId;

    public String fetchAuthorizationUrl() {
        int accountId = Context.accountId.get();

        if (roleId == null || roleId.isEmpty()) {
            addActionError("roleId is required");
            return Action.ERROR.toUpperCase();
        }

        TestRoles role = TestRolesDao.instance.findOne(Filters.eq("_id", new ObjectId(roleId)));
        if (role == null) {
            addActionError("TestRole not found");
            return Action.ERROR.toUpperCase();
        }

        CopilotOAuthAuthParam copilotParam = findCopilotParam(role);
        if (copilotParam == null) {
            addActionError("No COPILOT_OAUTH auth param found in TestRole");
            return Action.ERROR.toUpperCase();
        }

        try {
            authorizationUrl = buildAuthorizationUrl(accountId, copilotParam.getTenantId(), copilotParam.getClientId(),
                SCOPE, FLOW_TYPE_TEST_ROLE_AUTH, roleId, null);

            logger.infoAndAddToDb("getAuthorizationUrl: generated for accountId=" + accountId + " roleId=" + roleId);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("getAuthorizationUrl: failed: " + e.getMessage());
            addActionError("Failed to build authorization URL");
            return Action.ERROR.toUpperCase();
        }
    }


    public String initiateMultiEnvSetup() {
        int accountId = Context.accountId.get();

        if (tenantId == null || tenantId.isEmpty() || clientId == null || clientId.isEmpty()
                || clientSecret == null || clientSecret.isEmpty()
                || dataIngestionUrl == null || dataIngestionUrl.isEmpty()) {
            addActionError("tenantId, clientId, clientSecret and dataIngestionUrl are required");
            return Action.ERROR.toUpperCase();
        }

        CopilotStudioIntegration existing = CopilotStudioIntegrationDao.instance.findOne(new BasicDBObject());
        if (existing != null) {
            addActionError("An integration already exists. Remove it before connecting again.");
            return Action.ERROR.toUpperCase();
        }

        try {
            int now = Context.now();

            CopilotStudioIntegration newIntegration =
                new CopilotStudioIntegration(tenantId, clientId, clientSecret, dataIngestionUrl, now);
            CopilotStudioIntegrationDao.instance.insertOne(newIntegration);

            authorizationUrl = buildAuthorizationUrl(accountId, tenantId, clientId,
                Constants.SCOPE_COPILOT_STUDIO_MULTI_ENV, FLOW_TYPE_MULTI_ENV_SETUP, null,
                newIntegration.getId().toHexString());

            this.integrationId = newIntegration.getId().toHexString();
            logger.infoAndAddToDb("initiateMultiEnvSetup: generated for accountId=" + accountId
                + " integrationId=" + integrationId);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("initiateMultiEnvSetup: failed: " + e.getMessage());
            addActionError("Failed to start Copilot Studio (Multi Environment) setup");
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchMultiEnvIntegration() {
        integration = StringUtils.isNotEmpty(integrationId)
            ? CopilotStudioIntegrationDao.instance.findOne(Filters.eq(CopilotStudioIntegration.ID, new ObjectId(integrationId)))
            : CopilotStudioIntegrationDao.instance.findOne(new BasicDBObject());

        if (integration != null) {
            integration.setClientSecret(null);
        }
        return Action.SUCCESS.toUpperCase();
    }

    /** Removes the integration and stops its job — the only way to change settings is to remove and reconnect. */
    public String removeMultiEnvIntegration() {
        CopilotStudioIntegration existing = CopilotStudioIntegrationDao.instance.findOne(new BasicDBObject());
        if (existing == null) {
            addActionError("Integration not found");
            return Action.ERROR.toUpperCase();
        }

        if (existing.getJobId() != null && !existing.getJobId().isEmpty()) {
            AccountJobDao.instance.deleteAll(Filters.eq(AccountJob.ID, new ObjectId(existing.getJobId())));
        }
        CopilotStudioIntegrationDao.instance.deleteAll(Filters.eq(CopilotStudioIntegration.ID, existing.getId()));

        logger.infoAndAddToDb("removeMultiEnvIntegration: removed integration and job for accountId=" + Context.accountId.get());
        return Action.SUCCESS.toUpperCase();
    }

    /** Confirms the discovered environment list and creates the recurring wrapper AccountJob. */
    public String confirmMultiEnvIntegration() {
        if (integrationId == null || integrationId.isEmpty()) {
            addActionError("integrationId is required");
            return Action.ERROR.toUpperCase();
        }

        CopilotStudioIntegration existing = CopilotStudioIntegrationDao.instance.findOne(
            Filters.eq(CopilotStudioIntegration.ID, new ObjectId(integrationId)));
        if (existing == null) {
            addActionError("Integration not found");
            return Action.ERROR.toUpperCase();
        }
        if (existing.getStatus() != CopilotStudioIntegration.Status.ENVIRONMENTS_DISCOVERED) {
            addActionError("Integration is not ready to be confirmed (status=" + existing.getStatus() + ")");
            return Action.ERROR.toUpperCase();
        }

        int accountId = Context.accountId.get();
        int now = Context.now();

        Map<String, Object> jobConfig = new HashMap<>();
        jobConfig.put(CONFIG_COPILOT_STUDIO_MULTI_ENV_INTEGRATION_ID, integrationId);

        AccountJob accountJob = new AccountJob(
            accountId,
            JOB_TYPE_COPILOT_STUDIO_MULTI_ENV_CONNECTOR,
            CONNECTOR_TYPE_COPILOT_STUDIO_MULTI_ENV,
            jobConfig,
            RECURRING_JOB_INTERVAL_30_MINUTES,
            now,
            now
        );
        accountJob.setJobStatus(JobStatus.SCHEDULED);
        accountJob.setScheduleType(ScheduleType.RECURRING);
        accountJob.setScheduledAt(now);
        accountJob.setHeartbeatAt(0);
        accountJob.setStartedAt(0);
        accountJob.setFinishedAt(0);

        AccountJobDao.instance.insertOne(accountJob);
        this.jobId = accountJob.getId().toHexString();

        CopilotStudioIntegrationDao.instance.updateOneNoUpsert(
            Filters.eq(CopilotStudioIntegration.ID, existing.getId()),
            Updates.combine(
                Updates.set(CopilotStudioIntegration.STATUS, CopilotStudioIntegration.Status.CONFIRMED.name()),
                Updates.set(CopilotStudioIntegration.JOB_ID, this.jobId),
                Updates.set(CopilotStudioIntegration.UPDATED_AT, now)
            )
        );

        logger.infoAndAddToDb("confirmMultiEnvIntegration: created job " + this.jobId + " for accountId=" + accountId);
        return Action.SUCCESS.toUpperCase();
    }

    private String buildAuthorizationUrl(int accountId, String tenantId, String clientId, String scope,
                                          String flowType, String roleId, String integrationId) {
        String callbackUrl = Constants.DEFAULT_AKTO_DASHBOARD_URL + "/copilot/oauth/callback";
        String nonce = UUID.randomUUID().toString();
        int expiresAt = Context.now() + OAuthState.EXPIRY_SECONDS;

        Map<String, String> stateData = new HashMap<>();
        stateData.put("accountId", String.valueOf(accountId));
        stateData.put("flowType", flowType);
        if (roleId != null) {
            stateData.put("roleId", roleId);
        }
        if (integrationId != null) {
            stateData.put("integrationId", integrationId);
        }
        OAuthStatesDao.instance.insertOne(new OAuthState(nonce, stateData, expiresAt));

        return String.format(AUTH_ENDPOINT_TEMPLATE, tenantId)
            + "?response_type=code"
            + "&response_mode=query"
            + "&client_id=" + HttpRequestResponseUtils.encode(clientId)
            + "&redirect_uri=" + HttpRequestResponseUtils.encode(callbackUrl)
            + "&scope=" + HttpRequestResponseUtils.encode(scope)
            + "&state=" + HttpRequestResponseUtils.encode(nonce);
    }

    private static CopilotOAuthAuthParam findCopilotParam(TestRoles role) {
        if (role.getAuthWithCondList() == null) return null;
        for (AuthWithCond authWithCond : role.getAuthWithCondList()) {
            if (authWithCond.getAuthMechanism() == null) continue;
            List<AuthParam> params = authWithCond.getAuthMechanism().getAuthParams();
            if (params == null) continue;
            for (AuthParam param : params) {
                if (param instanceof CopilotOAuthAuthParam) {
                    return (CopilotOAuthAuthParam) param;
                }
            }
        }
        return null;
    }
}
