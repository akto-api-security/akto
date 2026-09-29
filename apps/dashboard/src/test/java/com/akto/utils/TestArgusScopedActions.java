package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import com.akto.action.AuditDataAction;
import com.akto.action.GuardrailPoliciesAction;
import com.akto.action.InsightsAction;
import com.akto.action.SecurityPostureAction;
import com.akto.action.TraceAction;
import com.akto.action.monitoring.EndpointShieldAgentAction;
import com.akto.action.monitoring.LLMObservabilityAction;
import com.akto.action.settings.ModuleInfoAction;
import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dao.tracing.SpanDao;
import com.akto.dao.tracing.TraceDao;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.User;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.dto.tracing.model.Span;
import com.akto.dto.tracing.model.Trace;
import com.akto.interceptor.RoleAccessInterceptor;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.mongodb.client.model.Filters;

/*
 * Action-level checks for users limited to specific collections in Argus: they only reach their own
 * agents' data, and users who are not limited keep the existing behaviour.
 */
public class TestArgusScopedActions extends ArgusScopeTestBase {

    private static final String SUCCESS = "SUCCESS";
    private static final String ERROR = "ERROR";

    @Before
    public void cleanActionData() {
        GuardrailPoliciesDao.instance.getMCollection().drop();
        McpAuditInfoDao.instance.getMCollection().drop();
        TraceDao.instance.getMCollection().drop();
        SpanDao.instance.getMCollection().drop();
        ModuleInfoDao.instance.getMCollection().drop();
    }

    // ── Guardrail policies (full action path) ──────────────────────────────────

    private static GuardrailPoliciesAction policyAction(int userId) {
        as(userId, CONTEXT_SOURCE.AGENTIC);
        GuardrailPoliciesAction action = new GuardrailPoliciesAction();
        action.setSession(session(userId));
        return action;
    }

    private static GuardrailPolicies policy(String name, boolean applyToAll, String... targets) {
        GuardrailPolicies policy = new GuardrailPolicies();
        policy.setName(name);
        policy.setApplyToAllServers(applyToAll);
        policy.setSelectedAgentServersV2(Arrays.stream(targets)
            .map(t -> new GuardrailPolicies.SelectedServer(t, t)).collect(Collectors.toList()));
        return policy;
    }

    private static String insertPolicy(String name, boolean applyToAll, String... targets) {
        GuardrailPolicies policy = policy(name, applyToAll, targets);
        policy.setId(new ObjectId());
        GuardrailPoliciesDao.instance.insertOne(policy);
        return policy.getId().toHexString();
    }

    @Test
    public void testCreatePolicyOnOwnAgentIsSaved() {
        GuardrailPoliciesAction action = policyAction(TEAM_A);
        action.setPolicy(policy("team-a-policy", false, OWN_HOST));
        assertEquals(SUCCESS, action.createGuardrailPolicy());
        assertNotNull(GuardrailPoliciesDao.instance.findOne(Filters.eq("name", "team-a-policy")));
    }

    @Test
    public void testCreatePolicyForAllAgentsIsRefused() {
        GuardrailPoliciesAction action = policyAction(TEAM_A);
        action.setPolicy(policy("team-a-global", true));
        assertEquals(ERROR, action.createGuardrailPolicy());
        assertNull(GuardrailPoliciesDao.instance.findOne(Filters.eq("name", "team-a-global")));
    }

    @Test
    public void testOverwritingAnotherTeamsPolicyByNameIsRefused() {
        insertPolicy("shared-name", true);
        GuardrailPoliciesAction action = policyAction(TEAM_A);
        // no hexId: the upsert matches by name, so this would replace the existing global policy
        action.setPolicy(policy("shared-name", false, OWN_HOST));
        assertEquals(ERROR, action.createGuardrailPolicy());
        assertTrue(GuardrailPoliciesDao.instance.findOne(Filters.eq("name", "shared-name")).isApplyToAllServers());
    }

    @Test
    public void testDeleteOnlyOwnPolicies() {
        String own = insertPolicy("own", false, OWN_HOST);
        String global = insertPolicy("global", true);

        GuardrailPoliciesAction action = policyAction(TEAM_A);
        action.setPolicyIds(Arrays.asList(own, global));
        assertEquals(ERROR, action.deleteGuardrailPolicies());
        assertEquals(2, GuardrailPoliciesDao.instance.count(Filters.empty()));

        action = policyAction(TEAM_A);
        action.setPolicyIds(Collections.singletonList(own));
        assertEquals(SUCCESS, action.deleteGuardrailPolicies());
        assertEquals(1, GuardrailPoliciesDao.instance.count(Filters.empty()));
    }

    @Test
    public void testApproveServerOnlyOnOwnPolicies() {
        String global = insertPolicy("global", true);
        GuardrailPoliciesAction action = policyAction(TEAM_A);
        action.setHexId(global);
        action.setApprovedServerId(OWN_HOST);
        action.setApprovalMode("ALWAYS");
        assertEquals(ERROR, action.approveServerForPolicy());

        action = policyAction(ADMIN);
        action.setHexId(global);
        action.setApprovedServerId(OWN_HOST);
        action.setApprovalMode("ALWAYS");
        assertEquals(SUCCESS, action.approveServerForPolicy());
    }

    @Test
    public void testSecurityEngineerCannotSaveInArgusButCanInAtlas() {
        GuardrailPoliciesAction action = policyAction(MEMBER);
        action.setPolicy(policy("member-policy", true));
        assertEquals(ERROR, action.createGuardrailPolicy());

        as(MEMBER, CONTEXT_SOURCE.ENDPOINT);
        action = new GuardrailPoliciesAction();
        action.setSession(session(MEMBER));
        action.setPolicy(policy("member-atlas-policy", true));
        assertEquals(SUCCESS, action.createGuardrailPolicy());
    }

    // ── Audit data ─────────────────────────────────────────────────────────────

    private static ObjectId insertAudit(int collectionId, String type, String resourceName, String markedBy) {
        McpAuditInfo audit = new McpAuditInfo(0, markedBy, type, 0, resourceName, "", new HashSet<>(), collectionId, resourceName);
        audit.setContextSource(CONTEXT_SOURCE.AGENTIC.name());
        McpAuditInfoDao.instance.insertOne(audit);
        return McpAuditInfoDao.instance.findOne(Filters.eq(McpAuditInfo.RESOURCE_NAME, resourceName)).getId();
    }

    private static AuditDataAction auditAction(int userId) {
        as(userId, CONTEXT_SOURCE.AGENTIC);
        AuditDataAction action = new AuditDataAction();
        action.setSession(session(userId));
        return action;
    }

    private static String remarksOf(ObjectId id) {
        return McpAuditInfoDao.instance.findOne(Filters.eq(Constants.ID, id)).getRemarks();
    }

    @Test
    public void testUpdateAuditDataOnlyOwnRecords() {
        ObjectId own = insertAudit(1, Constants.AKTO_MCP_SERVER_TAG, "own-server", "");
        ObjectId other = insertAudit(3, Constants.AKTO_MCP_SERVER_TAG, "other-server", "");

        AuditDataAction action = auditAction(TEAM_A);
        action.setHexIds(Arrays.asList(own.toHexString(), other.toHexString()));
        action.setRemarks(McpAuditInfo.REMARKS_APPROVED);
        assertEquals(SUCCESS, action.updateAuditData());
        assertEquals(McpAuditInfo.REMARKS_APPROVED, remarksOf(own));
        assertEquals("", remarksOf(other));

        action = auditAction(TEAM_A);
        action.setHexIds(Collections.singletonList(other.toHexString()));
        action.setRemarks(McpAuditInfo.REMARKS_APPROVED);
        assertEquals(ERROR, action.updateAuditData());
        assertEquals("", remarksOf(other));
    }

    @Test
    public void testBlockForAllAgentsIsRefusedForLimitedUsers() {
        ObjectId own = insertAudit(1, Constants.AKTO_MCP_SERVER_TAG, "own-server", "");
        AuditDataAction action = auditAction(TEAM_A);
        action.setHexIds(Collections.singletonList(own.toHexString()));
        action.setMcpServerForAllAgents("own-server");
        action.setRemarks(McpAuditInfo.REMARKS_REJECTED);
        assertEquals(ERROR, action.updateAuditData());
        assertEquals("", remarksOf(own));
    }

    @Test
    public void testAdminUpdatesAnyAuditRecord() {
        ObjectId other = insertAudit(3, Constants.AKTO_MCP_SERVER_TAG, "other-server", "");
        AuditDataAction action = auditAction(ADMIN);
        action.setHexIds(Collections.singletonList(other.toHexString()));
        action.setRemarks(McpAuditInfo.REMARKS_APPROVED);
        assertEquals(SUCCESS, action.updateAuditData());
        assertEquals(McpAuditInfo.REMARKS_APPROVED, remarksOf(other));
    }

    @Test
    public void testSkillsAndFilterOptionsOnlyOwnRecords() {
        insertAudit(1, McpAuditInfo.TYPE_AGENT_SKILL, "own-skill", "alice@example.com");
        insertAudit(3, McpAuditInfo.TYPE_AGENT_SKILL, "other-skill", "bob@example.com");
        insertAudit(1, Constants.AKTO_MCP_SERVER_TAG, "own-server", "alice@example.com");
        insertAudit(3, Constants.AKTO_MCP_SERVER_TAG, "other-server", "bob@example.com");

        AuditDataAction action = auditAction(TEAM_A);
        assertEquals(SUCCESS, action.fetchSkillsData());
        assertEquals(1, action.getAuditData().size());

        action = auditAction(TEAM_A);
        assertEquals(SUCCESS, action.fetchAuditDataFilterOptions());
        assertEquals(Collections.singletonList("alice@example.com"), action.getAuditFilterOptions().get("markedBy"));

        action = auditAction(ADMIN);
        assertEquals(SUCCESS, action.fetchSkillsData());
        assertEquals(2, action.getAuditData().size());
    }

    // ── Agent traces (Mongo) ───────────────────────────────────────────────────

    private static void insertTrace(String traceId, int collectionId) {
        Trace trace = new Trace();
        trace.setId(traceId);
        trace.setApiCollectionId(collectionId);
        trace.setStartTimeMillis(System.currentTimeMillis());
        TraceDao.instance.insertOne(trace);
        Span span = new Span();
        span.setId(traceId + "-span");
        span.setTraceId(traceId);
        SpanDao.instance.insertOne(span);
    }

    private static String traceScopeError(int userId, Integer collectionId, String traceId) throws Exception {
        as(userId, CONTEXT_SOURCE.AGENTIC);
        TraceAction action = new TraceAction();
        if (collectionId != null) action.setApiCollectionId(collectionId);
        action.setTraceId(traceId);
        RoleAccessInterceptor interceptor = new RoleAccessInterceptor();
        interceptor.setCollectionScope("OWN_COLLECTION");
        Method method = RoleAccessInterceptor.class.getDeclaredMethod("checkCollectionScope", Object.class, User.class);
        method.setAccessible(true);
        return (String) method.invoke(interceptor, action, user(userId));
    }

    @Test
    public void testTracesOnlyOwnCollections() throws Exception {
        insertTrace("own-trace", 1);
        insertTrace("other-trace", 3);

        assertNull(traceScopeError(TEAM_A, 1, null));
        assertNotNull(traceScopeError(TEAM_A, 3, null));
        assertNull(traceScopeError(TEAM_A, null, "own-trace"));
        assertNotNull(traceScopeError(TEAM_A, null, "other-trace"));
        assertNotNull(traceScopeError(TEAM_A, null, "missing-trace"));
        assertNull(traceScopeError(ADMIN, 3, null));
        assertNull(traceScopeError(ADMIN, null, "other-trace"));
        assertNull(traceScopeError(THREAT_ENGINEER_ALL, null, "other-trace"));
    }

    // ── Trace search filter (Elasticsearch / ADX query) ────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> traceFilters(int userId, List<String> serviceIds) throws Exception {
        as(userId, CONTEXT_SOURCE.AGENTIC);
        LLMObservabilityAction action = new LLMObservabilityAction();
        action.setSession(session(userId));
        action.setServiceIds(serviceIds);
        Method method = LLMObservabilityAction.class.getDeclaredMethod("buildMultiFilters", boolean.class);
        method.setAccessible(true);
        return (Map<String, List<String>>) method.invoke(action, false);
    }

    @Test
    public void testTraceSearchFilterOnlyOwnAgents() throws Exception {
        assertEquals(new HashSet<>(Arrays.asList(OWN_HOST, OWN_CLAUDE_HOST)),
            new HashSet<>(traceFilters(TEAM_A, Collections.emptyList()).get(AgentQueryRecord.F_SERVICE_ID_KW)));
        assertEquals(Collections.singletonList(OWN_HOST),
            traceFilters(TEAM_A, Arrays.asList(OWN_HOST, OTHER_HOST)).get(AgentQueryRecord.F_SERVICE_ID_KW));

        // another team's agent only: filter on a value no trace has, so nothing comes back
        List<String> none = traceFilters(TEAM_A, Collections.singletonList(OTHER_HOST)).get(AgentQueryRecord.F_SERVICE_ID_KW);
        assertEquals(1, none.size());
        assertTrue(!none.contains(OTHER_HOST) && !none.contains(OWN_HOST));

        // not limited: request passes through unchanged
        assertNull(traceFilters(ADMIN, Collections.emptyList()).get(AgentQueryRecord.F_SERVICE_ID_KW));
        assertEquals(Collections.singletonList(OTHER_HOST),
            traceFilters(THREAT_ENGINEER_ALL, Collections.singletonList(OTHER_HOST)).get(AgentQueryRecord.F_SERVICE_ID_KW));
    }

    // ── Users, devices, posture, insights: empty for limited users ─────────────

    @Test
    public void testUsersAndDevicesEmptyForLimitedUsers() {
        ModuleInfo device = new ModuleInfo();
        device.setId("device-1");
        device.setName("laptop1");
        device.setModuleType(ModuleType.MCP_ENDPOINT_SHIELD);
        ModuleInfoDao.instance.insertOne(device);

        as(ADMIN, CONTEXT_SOURCE.AGENTIC);
        ModuleInfoAction adminModuleInfo = new ModuleInfoAction();
        adminModuleInfo.setSession(session(ADMIN));
        assertEquals(SUCCESS, adminModuleInfo.fetchEndpointShieldUserMetadata());
        assertEquals(1, adminModuleInfo.getModuleInfos().size());

        as(TEAM_A, CONTEXT_SOURCE.AGENTIC);
        ModuleInfoAction moduleInfo = new ModuleInfoAction();
        moduleInfo.setSession(session(TEAM_A));
        assertEquals(SUCCESS, moduleInfo.fetchAgenticUsers());
        assertTrue(moduleInfo.getAgenticUsers().isEmpty());
        assertEquals(SUCCESS, moduleInfo.fetchEndpointShieldUserMetadata());
        assertTrue(moduleInfo.getModuleInfos().isEmpty());
        assertEquals(SUCCESS, moduleInfo.fetchEndpointShieldFilterOptions());
        for (Object values : moduleInfo.getFilterOptions().values()) {
            assertTrue(((List<?>) values).isEmpty());
        }

        EndpointShieldAgentAction userAnalysis = new EndpointShieldAgentAction();
        userAnalysis.setSession(session(TEAM_A));
        assertEquals(SUCCESS, userAnalysis.fetchUserAnalysisList());
        assertTrue(userAnalysis.getUserAnalysisList().isEmpty());
        userAnalysis.setUsername("someone");
        assertEquals(SUCCESS, userAnalysis.fetchUserAnalysis());
        assertNull(userAnalysis.getUserAnalysis());
    }

    @Test
    public void testPostureAndInsightsEmptyForLimitedUsers() {
        as(TEAM_A, CONTEXT_SOURCE.AGENTIC);
        SecurityPostureAction posture = new SecurityPostureAction();
        posture.setSession(session(TEAM_A));
        assertEquals(SUCCESS, posture.fetchPostureSummary());
        assertTrue(posture.getResponse().isEmpty());
        assertEquals(SUCCESS, posture.fetchPostureDrill());
        assertNull(posture.getPostureDrill());

        InsightsAction insights = new InsightsAction();
        insights.setSession(session(TEAM_A));
        assertEquals(SUCCESS, insights.fetchInsightsList());
        assertTrue(insights.getInsights().isEmpty());
        insights.setInsightId("ANY");
        assertEquals(SUCCESS, insights.fetchInsightDetail());
        assertNull(insights.getInsight());
    }
}
