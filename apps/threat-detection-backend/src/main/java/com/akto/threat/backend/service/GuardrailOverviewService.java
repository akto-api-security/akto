package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailActionCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailActionCountsResponse.ActionCounts;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailActionCountsResponse.DayCounts;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailHostPolicyCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailPolicyCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailTriageTimeResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.GuardrailOverviewRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.HostScope;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.dto.threat_detection_backend.MaliciousEventDto.CaseActivity;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.utils.ThreatUtils;
import com.akto.util.ThreatDetectionConstants;
import com.mongodb.client.MongoCursor;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// Aggregations behind the Guardrail overview page. Each panel filters with the violations list's own
// query builder, so its numbers match the list it links to.
public class GuardrailOverviewService {

  private static final long DAY_SECONDS = 86400L;
  // Never build more day buckets than a year of data, whatever range the caller sends.
  private static final int MAX_DAYS = 366;
  private static final int DEFAULT_PAGE = 20;
  private static final int MAX_PAGE = 200;
  // Host and policy pairs kept for the heatmap, most events first; the long tail is dropped.
  private static final int MAX_HOST_POLICY_ROWS = 10000;
  // Triaged events the median is taken over.
  private static final int MAX_TRIAGE_SAMPLE = 100000;

  private static final String BLOCKED = "blocked";
  private static final String ALERTED = "alerted";
  private static final String FLAGGED = "flagged";
  // Counts of an action list are kept in this order: blocked, alerted, flagged.
  private static final int ACTIONS = 3;

  private final MaliciousEventDao maliciousEventDao;
  private final MaliciousEventService maliciousEventService;

  public GuardrailOverviewService(MaliciousEventDao maliciousEventDao, MaliciousEventService maliciousEventService) {
    this.maliciousEventDao = maliciousEventDao;
    this.maliciousEventService = maliciousEventService;
  }

  // The filtered events of the request, as the violations list would match them.
  private Document eventsMatch(String accountId, GuardrailOverviewRequest request, String contextSource, Document hostScopeMatch) {
    Document match = maliciousEventService.buildListFilterQuery(accountId, request.getFilter(), contextSource);
    // Same partition the list's Active tab applies, so Atlas skill and config findings stay out.
    List<Document> partitions = ThreatUtils.evaluationModeConditions(contextSource, "exclude", "exclude");
    if (!partitions.isEmpty()) {
      List<Document> and = new ArrayList<>();
      and.add(match);
      and.addAll(partitions);
      match = new Document("$and", and);
    }
    ThreatUtils.andHostScope(match, hostScopeMatch);
    return match;
  }

  // Behaviour is written into the event metadata text, e.g. `behaviour: "block"` (the same text the
  // list's behaviour filter matches). Read once per event; anything but block / alert (warn, approval,
  // none recorded) counts as Flagged.
  private static Document actionExpr() {
    Document found = new Document("$regexFind", new Document("input", new Document("$ifNull", Arrays.asList("$metadata", "")))
        .append("regex", "behaviour\"?\\s*:\\s*\"([a-z_]+)\""));
    Document behaviour = new Document("$let", new Document("vars", new Document("m", found))
        .append("in", new Document("$arrayElemAt", Arrays.asList(new Document("$ifNull", Arrays.asList("$$m.captures", Collections.emptyList())), 0))));
    return new Document("$let", new Document("vars", new Document("b", behaviour))
        .append("in", new Document("$switch", new Document("branches", Arrays.asList(
            new Document("case", new Document("$eq", Arrays.asList("$$b", "block"))).append("then", BLOCKED),
            new Document("case", new Document("$eq", Arrays.asList("$$b", "alert"))).append("then", ALERTED)))
            .append("default", FLAGGED))));
  }

  // Events per day and action over the filter's time range.
  public FetchGuardrailActionCountsResponse fetchActionCounts(
      String accountId, GuardrailOverviewRequest request, String contextSource, Document hostScopeMatch) {

    Document match = eventsMatch(accountId, request, contextSource, hostScopeMatch);
    // UTC day start, in seconds, by plain arithmetic on detectedAt.
    Document dayExpr = new Document("$subtract", Arrays.asList("$detectedAt", new Document("$mod", Arrays.asList("$detectedAt", DAY_SECONDS))));

    List<Document> pipeline = Arrays.asList(
        new Document("$match", match),
        new Document("$group", new Document("_id", new Document("day", dayExpr).append("action", actionExpr()))
            .append("count", new Document("$sum", 1))));

    Map<Long, long[]> byDay = new HashMap<>();
    forEachRow(accountId, pipeline, doc -> {
      Document id = (Document) doc.get("_id");
      Number day = (Number) id.get("day");
      if (day == null) return;
      long[] counts = byDay.computeIfAbsent(day.longValue(), d -> new long[ACTIONS]);
      counts[actionIndex(id.getString("action"))] += ((Number) doc.get("count")).longValue();
    });

    return buildActionCounts(byDay, request);
  }

  private static int pageSkip(GuardrailOverviewRequest request) {
    return request.hasSkip() ? request.getSkip() : 0;
  }

  private static int pageSize(GuardrailOverviewRequest request) {
    int limit = request.hasLimit() ? request.getLimit() : 0;
    return limit <= 0 ? DEFAULT_PAGE : Math.min(limit, MAX_PAGE);
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  // Runs the pipeline over the account's events and hands each result row on, as the cursor yields it.
  private void forEachRow(String accountId, List<Document> pipeline, Consumer<Document> onRow) {
    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, pipeline).cursor()) {
      while (cursor.hasNext()) {
        onRow.accept(cursor.next());
      }
    }
  }

  // Policies ranked by their number of events, one page of them plus how many there are.
  public FetchGuardrailPolicyCountsResponse fetchPolicyCounts(
      String accountId, GuardrailOverviewRequest request, String contextSource, Document hostScopeMatch) {

    List<Document> pipeline = Arrays.asList(
        new Document("$match", eventsMatch(accountId, request, contextSource, hostScopeMatch)),
        new Document("$group", new Document("_id", "$filterId").append("count", new Document("$sum", 1))),
        new Document("$sort", new Document("count", -1).append("_id", 1)),
        new Document("$facet", new Document("page", Arrays.asList(
                new Document("$skip", pageSkip(request)), new Document("$limit", pageSize(request))))
            .append("total", Collections.singletonList(new Document("$count", "n")))));

    FetchGuardrailPolicyCountsResponse.Builder resp = FetchGuardrailPolicyCountsResponse.newBuilder();
    // The $facet gives one row: the page and the total.
    forEachRow(accountId, pipeline, doc -> {
      for (Document row : doc.getList("page", Document.class, Collections.emptyList())) {
        resp.addPolicies(FetchGuardrailPolicyCountsResponse.PolicyCount.newBuilder()
            .setPolicy(orEmpty(row.getString("_id"))).setCount(((Number) row.get("count")).intValue()));
      }
      List<Document> total = doc.getList("total", Document.class, Collections.emptyList());
      resp.setTotal(total.isEmpty() ? 0 : ((Number) total.get(0).get("n")).intValue());
    });
    return resp.build();
  }

  // Events per host and policy. A host is not an agent: the dashboard knows which collection a host
  // belongs to, so it does the grouping into agents and the paging over them.
  public FetchGuardrailHostPolicyCountsResponse fetchHostPolicyCounts(
      String accountId, GuardrailOverviewRequest request, String contextSource, Document hostScopeMatch) {

    List<Document> pipeline = Arrays.asList(
        new Document("$match", eventsMatch(accountId, request, contextSource, hostScopeMatch)),
        new Document("$group", new Document("_id", new Document("host", "$host").append("policy", "$filterId"))
            .append("count", new Document("$sum", 1))),
        new Document("$sort", new Document("count", -1)),
        new Document("$limit", MAX_HOST_POLICY_ROWS));

    FetchGuardrailHostPolicyCountsResponse.Builder resp = FetchGuardrailHostPolicyCountsResponse.newBuilder();
    forEachRow(accountId, pipeline, doc -> {
      Document id = (Document) doc.get("_id");
      resp.addRows(FetchGuardrailHostPolicyCountsResponse.HostPolicyCount.newBuilder()
          .setHost(orEmpty(id.getString("host"))).setPolicy(orEmpty(id.getString("policy")))
          .setCount(((Number) doc.get("count")).intValue()));
    });
    return resp.build();
  }

  // Median time from detection to the first status or owner change of the case, over the triaged events.
  public FetchGuardrailTriageTimeResponse fetchTriageTime(
      String accountId, GuardrailOverviewRequest request, String contextSource, Document hostScopeMatch) {

    List<String> pickedUp = Arrays.asList(CaseActivity.ACTIVITY_TYPE_STATUS, CaseActivity.ACTIVITY_TYPE_OWNER);
    Document hasTriage = new Document("caseTimeline",
        new Document("$elemMatch", new Document(CaseActivity.ACTIVITY_TYPE, new Document("$in", pickedUp))));
    Document pickedUpEntries = new Document("$filter", new Document("input", "$caseTimeline").append("as", "c")
        .append("cond", new Document("$in", Arrays.asList("$$c." + CaseActivity.ACTIVITY_TYPE, pickedUp))));
    Document firstPickup = new Document("$min", new Document("$map", new Document("input", pickedUpEntries)
        .append("as", "c").append("in", "$$c." + CaseActivity.TIMESTAMP)));

    List<Document> pipeline = Arrays.asList(
        new Document("$match", new Document("$and", Arrays.asList(
            eventsMatch(accountId, request, contextSource, hostScopeMatch), hasTriage))),
        new Document("$project", new Document("elapsed", new Document("$subtract", Arrays.asList(firstPickup, "$detectedAt")))),
        new Document("$match", new Document("elapsed", new Document("$gte", 0))),
        new Document("$limit", MAX_TRIAGE_SAMPLE),
        new Document("$group", new Document("_id", null).append("elapsed", new Document("$push", "$elapsed"))));

    List<Long> elapsed = new ArrayList<>();
    // The last $group gives one row holding every elapsed time.
    forEachRow(accountId, pipeline, doc -> {
      for (Number n : doc.getList("elapsed", Number.class, Collections.emptyList())) {
        elapsed.add(n.longValue());
      }
    });
    Collections.sort(elapsed);
    int size = elapsed.size();
    long median = size == 0 ? 0 : (elapsed.get((size - 1) / 2) + elapsed.get(size / 2)) / 2;
    return FetchGuardrailTriageTimeResponse.newBuilder().setMedianSeconds((int) median).setTriaged(size).build();
  }

  // The active events, worst severity first, one page of them: the cases nobody has picked up yet. It is
  // the violations list's own query (Active tab), so grouping, ordering and the total behave as they do
  // there. The host scope replaces any host filter of the caller, so a limited user can never widen their
  // view with one.
  public ListMaliciousRequestsResponse fetchNeedsAttention(
      String accountId, GuardrailOverviewRequest request, String contextSource) {

    ListMaliciousRequestsRequest.Filter.Builder filter = request.getFilter().toBuilder()
        .setStatusFilter(ThreatDetectionConstants.ACTIVE)
        .setSortBySeverity(true);
    if (request.hasHostScope()) {
      HostScope scope = request.getHostScope();
      if (scope.getHostsCount() == 0 && scope.getLooseHostKeysCount() == 0 && scope.getClaudeDeviceIdsCount() == 0) {
        return ListMaliciousRequestsResponse.getDefaultInstance();
      }
      filter.clearHosts().clearLooseHostKeys().clearClaudeDeviceIds().clearMatchClaudeConfig()
          .addAllHosts(scope.getHostsList())
          .addAllLooseHostKeys(scope.getLooseHostKeysList())
          .addAllClaudeDeviceIds(scope.getClaudeDeviceIdsList());
    }
    ListMaliciousRequestsRequest list = ListMaliciousRequestsRequest.newBuilder()
        .setSkip(pageSkip(request))
        .setLimit(pageSize(request))
        .setFilter(filter)
        .build();
    return maliciousEventService.listMaliciousRequests(accountId, list, contextSource, "exclude", "exclude");
  }

  private static int actionIndex(String action) {
    switch (action) {
      case BLOCKED: return 0;
      case ALERTED: return 1;
      default: return 2;
    }
  }

  private static ActionCounts toActionCounts(long[] c) {
    return ActionCounts.newBuilder().setBlocked((int) c[0]).setAlerted((int) c[1]).setFlagged((int) c[2]).build();
  }

  // One entry per day of the range, so a chart gets a continuous series. The total covers every event.
  private static FetchGuardrailActionCountsResponse buildActionCounts(Map<Long, long[]> byDay, GuardrailOverviewRequest request) {
    long[] total = new long[ACTIONS];
    for (long[] counts : byDay.values()) {
      for (int i = 0; i < ACTIONS; i++) total[i] += counts[i];
    }

    long start = request.getFilter().getDetectedAtTimeRange().getStart();
    long end = request.getFilter().getDetectedAtTimeRange().getEnd();
    FetchGuardrailActionCountsResponse.Builder resp = FetchGuardrailActionCountsResponse.newBuilder().setTotal(toActionCounts(total));
    if (byDay.isEmpty() && (start <= 0 || end <= 0)) {
      return resp.build();
    }
    long lastDay = end > 0 ? end - (end % DAY_SECONDS) : Collections.max(byDay.keySet());
    // "All time" ends a year ahead. Counted back from a day that far out, the window would hold no events, so it stops today.
    long now = System.currentTimeMillis() / 1000;
    lastDay = Math.min(lastDay, now - (now % DAY_SECONDS));
    long firstDay = start > 0 ? start - (start % DAY_SECONDS) : Collections.min(byDay.keySet());
    firstDay = Math.max(firstDay, lastDay - (MAX_DAYS - 1) * DAY_SECONDS);

    for (long day = firstDay; day <= lastDay; day += DAY_SECONDS) {
      resp.addDays(DayCounts.newBuilder().setTs((int) day).setCounts(toActionCounts(byDay.getOrDefault(day, new long[ACTIONS]))).build());
    }
    return resp.build();
  }
}
