package com.akto.action.threat_detection;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailActionCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailActionCountsResponse.ActionCounts;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailHostPolicyCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailPolicyCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailTriageTimeResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.service.guardrails.GuardrailOverviewHeatmap;
import com.akto.service.guardrails.GuardrailOverviewHeatmap.Agent;
import com.akto.service.insights.HostCollectionResolver;
import com.akto.service.insights.InsightUtil;
import com.akto.service.posture.ArgusPostureUtils;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Data for the Guardrail overview page. Every panel takes the date range plus the violations list's own
// filter keys (policies, hosts, severity, ...), so the numbers match the list a panel links to. The panels
// that list rows also take skip and limit and answer with the page and the total.
public class GuardrailOverviewAction extends AbstractThreatDetectionAction {

  private static final String UNKNOWN_AGENT = "Unknown agent";

  @Getter @Setter private int startTimestamp;
  @Getter @Setter private int endTimestamp;
  @Getter @Setter private Map<String, Object> filters;
  @Getter @Setter private int skip;
  @Getter @Setter private int limit;

  // action counts
  @Getter private List<Map<String, Object>> days = new ArrayList<>();
  @Getter private Map<String, Object> total = actionCounts(ActionCounts.getDefaultInstance());

  // the panels that list rows: top policies, heatmap and needs attention
  @Getter private List<Map<String, Object>> rows = new ArrayList<>();
  @Getter private long totalRows;
  @Getter private List<String> columns = new ArrayList<>();
  @Getter private List<String> otherPolicies = new ArrayList<>();

  // median time to triage
  @Getter private Map<String, Object> triage = triageTime(0, 0);

  // A user limited to specific collections only sees their own agents. When the backend cannot scope to
  // them there is nothing safe to show, so the panels come back empty rather than account-wide.
  private boolean seesNothing(Map<String, Object> hostScope) {
    return hostScope == null && isLimitedToOwnAgents();
  }

  private Map<String, Object> overviewBody(Map<String, Object> hostScope) {
    return overviewBody(hostScope, true);
  }

  private Map<String, Object> overviewBody(Map<String, Object> hostScope, boolean paged) {
    Map<String, Object> range = new HashMap<>();
    range.put("start", startTimestamp);
    range.put("end", endTimestamp);

    Map<String, Object> filter = new HashMap<>();
    if (filters != null) {
      filter.putAll(filters);
    }
    filter.put("detected_at_time_range", range);

    Map<String, Object> body = new HashMap<>();
    body.put("filter", filter);
    if (paged && skip > 0) {
      body.put("skip", skip);
    }
    if (paged && limit > 0) {
      body.put("limit", limit);
    }
    if (hostScope != null) {
      body.put("hostScope", hostScope);
    }
    return body;
  }

  private String couldNotLoad(String panel) {
    addActionError("Could not load guardrail " + panel);
    return ERROR.toUpperCase();
  }

  private static Map<String, Object> actionCounts(ActionCounts counts) {
    Map<String, Object> map = new HashMap<>();
    map.put("blocked", counts.getBlocked());
    map.put("alerted", counts.getAlerted());
    map.put("flagged", counts.getFlagged());
    return map;
  }

  private static Map<String, Object> triageTime(int medianSeconds, int triaged) {
    Map<String, Object> map = new HashMap<>();
    map.put("medianSeconds", medianSeconds);
    map.put("triaged", triaged);
    return map;
  }

  private static Map<String, Object> nameAndCount(String name, int count) {
    Map<String, Object> row = new HashMap<>();
    row.put("name", name);
    row.put("count", count);
    return row;
  }

  // Events per day, split by what the guardrail did with them (blocked, alerted, flagged),
  // and the same split over the whole range.
  public String fetchGuardrailActionCounts() {
    Map<String, Object> hostScope = backendHostScope();
    if (seesNothing(hostScope)) {
      return SUCCESS.toUpperCase();
    }

    FetchGuardrailActionCountsResponse resp =
        postDashboardApi("get_guardrail_action_counts", overviewBody(hostScope), FetchGuardrailActionCountsResponse.class);
    if (resp == null) {
      return couldNotLoad("action counts");
    }

    List<Map<String, Object>> dayRows = new ArrayList<>();
    for (FetchGuardrailActionCountsResponse.DayCounts day : resp.getDaysList()) {
      Map<String, Object> row = actionCounts(day.getCounts());
      row.put("ts", day.getTs());
      dayRows.add(row);
    }
    this.days = dayRows;
    this.total = actionCounts(resp.getTotal());
    return SUCCESS.toUpperCase();
  }

  // The policies with the most events: a page of {name, count}, and how many policies had any.
  public String fetchGuardrailTopPolicies() {
    Map<String, Object> hostScope = backendHostScope();
    if (seesNothing(hostScope)) {
      return SUCCESS.toUpperCase();
    }

    FetchGuardrailPolicyCountsResponse resp =
        postDashboardApi("get_guardrail_policy_counts", overviewBody(hostScope), FetchGuardrailPolicyCountsResponse.class);
    if (resp == null) {
      return couldNotLoad("top policies");
    }

    List<Map<String, Object>> policyRows = new ArrayList<>();
    for (FetchGuardrailPolicyCountsResponse.PolicyCount policy : resp.getPoliciesList()) {
      policyRows.add(nameAndCount(policy.getPolicy(), policy.getCount()));
    }
    this.rows = policyRows;
    this.totalRows = resp.getTotal();
    return SUCCESS.toUpperCase();
  }

  // Events per agent and policy: the busiest policies as columns, a page of agents ranked by their events
  // as rows, and how many agents there are. The threat backend counts per host; the agent of a host is
  // the collection it belongs to, which only the dashboard knows.
  public String fetchGuardrailHeatmap() {
    Map<String, Object> hostScope = backendHostScope();
    if (seesNothing(hostScope)) {
      return SUCCESS.toUpperCase();
    }

    // The agents are grouped over every host, then paged here, so the backend must not page its rows.
    FetchGuardrailHostPolicyCountsResponse resp =
        postDashboardApi("get_guardrail_host_policy_counts", overviewBody(hostScope, false), FetchGuardrailHostPolicyCountsResponse.class);
    if (resp == null) {
      return couldNotLoad("heatmap");
    }

    GuardrailOverviewHeatmap.Result heatmap =
        GuardrailOverviewHeatmap.build(resp.getRowsList(), agentResolver(), skip, limit);
    this.columns = heatmap.columns;
    this.otherPolicies = heatmap.otherPolicies;
    this.rows = heatmap.rows;
    this.totalRows = heatmap.totalRows;
    return SUCCESS.toUpperCase();
  }

  // A host belongs to the collection it is attributed to, shown under the collection's own name and
  // environment. A host no collection claims is its own agent, named by the host.
  protected GuardrailOverviewHeatmap.AgentResolver agentResolver() {
    List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
        Filters.empty(),
        Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING));
    Map<Integer, ApiCollection> byId = new HashMap<>();
    for (ApiCollection c : collections) {
      byId.put(c.getId(), c);
    }
    HostCollectionResolver resolver = new HostCollectionResolver(collections);

    return host -> {
      List<Integer> ids = resolver.resolve(host);
      ApiCollection collection = ids.isEmpty() ? null : byId.get(ids.get(0));
      if (collection != null) {
        return new Agent("collection:" + collection.getId(), ArgusPostureUtils.agentDisplayName(collection),
            ArgusPostureUtils.envBucket(InsightUtil.envTagValue(collection)));
      }
      return new Agent("host:" + host, StringUtils.defaultIfBlank(host, UNKNOWN_AGENT), "");
    };
  }

  // Median time from detection to someone picking the case up (a status or owner change).
  public String fetchGuardrailTriageTime() {
    Map<String, Object> hostScope = backendHostScope();
    if (seesNothing(hostScope)) {
      return SUCCESS.toUpperCase();
    }

    FetchGuardrailTriageTimeResponse resp =
        postDashboardApi("get_guardrail_triage_time", overviewBody(hostScope), FetchGuardrailTriageTimeResponse.class);
    if (resp == null) {
      return couldNotLoad("triage time");
    }
    this.triage = triageTime(resp.getMedianSeconds(), resp.getTriaged());
    return SUCCESS.toUpperCase();
  }

  // The active cases nobody has picked up yet, worst severity first: a page of them and the total.
  public String fetchGuardrailNeedsAttention() {
    Map<String, Object> hostScope = backendHostScope();
    if (seesNothing(hostScope)) {
      return SUCCESS.toUpperCase();
    }

    ListMaliciousRequestsResponse resp =
        postDashboardApi("get_guardrail_needs_attention", overviewBody(hostScope), ListMaliciousRequestsResponse.class);
    if (resp == null) {
      return couldNotLoad("cases that need attention");
    }

    List<Map<String, Object>> cases = new ArrayList<>();
    for (ListMaliciousRequestsResponse.MaliciousEvent e : resp.getMaliciousEventsList()) {
      Map<String, Object> row = new HashMap<>();
      row.put("id", e.getId());
      // A grouped row stands for every event of its group.
      row.put("eventIds", e.getGroupEventIdsCount() > 0 ? new ArrayList<>(e.getGroupEventIdsList()) : java.util.Collections.singletonList(e.getId()));
      row.put("severity", StringUtils.defaultIfBlank(e.getSeverity(), "HIGH").toUpperCase());
      row.put("policy", e.getFilterId());
      row.put("host", e.getHost());
      row.put("collectionId", e.getApiCollectionId());
      row.put("count", Math.max(1, e.getGroupCount()));
      row.put("ts", e.getDetectedAt());
      row.put("owner", e.getCaseOwner());
      cases.add(row);
    }
    this.rows = cases;
    this.totalRows = resp.getTotal();
    return SUCCESS.toUpperCase();
  }
}
