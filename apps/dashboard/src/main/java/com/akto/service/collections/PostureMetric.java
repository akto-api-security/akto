package com.akto.service.collections;

import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiType;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;

import static com.akto.service.collections.AggregationExpressions.*;

/**
 * One summed property of an api_info document (mostly a 1 for the documents a condition holds for, so
 * a count), the single definition shared by everything that touches the posture numbers: the per
 * collection recount sums it per collection, and every read sums these fields up. The conditions are the
 * pipeline form of ApiInfo.addStats / findActualAccessType / findSeverity and of the action item
 * filters of the home dashboard.
 *
 * Where ApiInfo picks "the first" of a set (the actual auth type, the access type of a collection
 * holding only one kind) the order of a java HashSet decides; the first element of the stored array
 * is the equivalent here.
 */
@Getter
@AllArgsConstructor
final class PostureMetric {

    private final String field;
    /** What each document adds to the sum, built for a given "now" (epoch seconds), which the tested-recently metrics need. */
    private final IntFunction<Object> sumExpression;

    /** A count: a 1 for each document the condition holds for. */
    private static PostureMetric counting(String field, IntFunction<Object> condition) {
        return new PostureMetric(field, now -> {
            Object c = condition.apply(now);
            return c instanceof Boolean ? ((Boolean) c ? 1 : 0) : cond(c, 1, 0);
        });
    }

    static final String TOTAL = "total";
    /*
     * Sensitive is not a property of an api_info document (isSensitive is response only and lags its
     * cron): it is single_type_info's, so these two are not in ALL. "sensitive" is the sum of the stats
     * rows' sensitiveApisCount (kept by the sensitive refresh), "sensitiveUnauthenticated" is counted
     * with a lookup into single_type_info by PosturePartialsRefresh, over the unauthenticated apis only.
     */
    static final String SENSITIVE = "sensitive";
    static final String SENSITIVE_UNAUTHENTICATED = "sensitiveUnauthenticated";
    /** The stats row's sensitiveApisCount a collection's posture was counted with: a different one means a recount. */
    static final String SENSITIVE_APIS_SEEN = "sensitiveApisSeen";
    /** The stats row's riskScore (the collection's highest, times RISK_SCORE_SCALE) a collection's posture was counted with. */
    static final String RISK_SEEN = "riskSeen";
    /** apis any of whose access types is third party (not the "actual" one the access chart counts) */
    static final String THIRD_PARTY = "thirdParty";
    /** The sum of the risk scores, times {@link #RISK_SCORE_SCALE} so that it stays an integer. */
    static final String RISK_SCORE_SUM = "riskScoreSum";
    static final int RISK_SCORE_SCALE = 1000;
    static final String HIGH_RISK = "highRisk";
    static final String HIGH_RISK_THIRD_PARTY = "highRiskThirdParty";
    static final String UNAUTHENTICATED = "unauthenticated";
    static final String NOT_TESTED = "notTested";
    static final String ONLY_ONCE_TESTED = "onlyOnceTested";
    static final String TESTED_IN_LOOKBACK = "testedInLookback";
    static final String AUTH_NOT_CALCULATED = "authNotCalculated";
    static final String ACCESS_NOT_CALCULATED = "accessNotCalculated";
    static final String API_TYPE_MISSING = "apiTypeMissing";
    static final String CUSTOM_AUTH = "authCustom";

    static final String RISK_PREFIX = "risk";
    static final String ACCESS_PREFIX = "access_";
    static final String API_TYPE_PREFIX = "apiType_";
    static final String AUTH_PREFIX = "auth_";
    static final String SEVERITY_PREFIX = "severity_";

    /** The rounded risk score of the ApiStats risk map goes from 0 to this; higher scores count in the last bucket. */
    static final int MAX_RISK_BUCKET = 5;

    private static final List<String> STANDARD_AUTH_TYPES = Collections.unmodifiableList(Arrays.asList(
            AuthType.UNAUTHENTICATED, AuthType.BASIC, AuthType.AUTHORIZATION_HEADER, AuthType.JWT,
            AuthType.API_TOKEN, AuthType.BEARER, AuthType.API_KEY, AuthType.MTLS, AuthType.SESSION_TOKEN));

    // allAuthTypesFound is an array of sets of auth types, one set per observed request
    private static final Object AUTH_SETS = ifNull(field(ApiInfo.ALL_AUTH_TYPES_FOUND), Collections.emptyList());
    private static final Object ALL_AUTH_TYPES = flatten(AUTH_SETS);
    private static final Object HAS_UNAUTHENTICATED = in(AuthType.UNAUTHENTICATED, ALL_AUTH_TYPES);
    // ApiInfo.calculateActualAuth: unauthenticated wins, otherwise the first auth type found
    private static final Object ACTUAL_AUTH = cond(HAS_UNAUTHENTICATED, AuthType.UNAUTHENTICATED, arrayElemAt(ALL_AUTH_TYPES, 0));

    private static final Object ACCESS_TYPES = ifNull(field(ApiInfo.API_ACCESS_TYPES), Collections.emptyList());
    // ApiInfo.findActualAccessType: public, then partner, then third party, then whatever else is there
    private static final Object ACTUAL_ACCESS = cond(in(ApiAccessType.PUBLIC.name(), ACCESS_TYPES), ApiAccessType.PUBLIC.name(),
            cond(in(ApiAccessType.PARTNER.name(), ACCESS_TYPES), ApiAccessType.PARTNER.name(),
                    cond(in(ApiAccessType.THIRD_PARTY.name(), ACCESS_TYPES), ApiAccessType.THIRD_PARTY.name(),
                            arrayElemAt(ACCESS_TYPES, 0))));

    // Math.round of the risk score, as the ApiStats risk map buckets it (floor(x + 0.5): half up, unlike $round)
    private static final Object RISK_BUCKET = min(floor(add(ifNull(field(ApiInfo.RISK_SCORE), 0), 0.5)), MAX_RISK_BUCKET);
    private static final Object RISK_SCORE = ifNull(field(ApiInfo.RISK_SCORE), 0);
    private static final Object SEVERITY_SCORE = ifNull(field(ApiInfo.SEVERITY_SCORE), 0);

    /** Every metric, in a stable order. */
    static final List<PostureMetric> ALL = Collections.unmodifiableList(build());


    private static List<PostureMetric> build() {
        List<PostureMetric> metrics = new ArrayList<>();
        metrics.add(PostureMetric.counting(TOTAL, now -> true));

        for (int bucket = 0; bucket <= MAX_RISK_BUCKET; bucket++) {
            int riskBucket = bucket;
            metrics.add(PostureMetric.counting(riskField(bucket), now -> eq(RISK_BUCKET, riskBucket)));
        }
        for (ApiAccessType type : ApiAccessType.values()) {
            metrics.add(PostureMetric.counting(ACCESS_PREFIX + type.name(), now -> eq(ACTUAL_ACCESS, type.name())));
        }
        for (ApiType type : ApiType.values()) {
            metrics.add(PostureMetric.counting(API_TYPE_PREFIX + type.name(), now -> eq(ifNull(field(ApiInfo.API_TYPE), null), type.name())));
        }
        for (String authType : STANDARD_AUTH_TYPES) {
            metrics.add(PostureMetric.counting(AUTH_PREFIX + authType, now -> eq(ACTUAL_AUTH, authType)));
        }
        // ApiInfo.addStats counts every auth type that is not a standard one as CUSTOM
        metrics.add(PostureMetric.counting(CUSTOM_AUTH, now -> and(gt(size(ALL_AUTH_TYPES), 0),
                not(in(ACTUAL_AUTH, STANDARD_AUTH_TYPES)))));

        // ApiInfo.findSeverity
        metrics.add(PostureMetric.counting(SEVERITY_PREFIX + "CRITICAL", now -> gte(SEVERITY_SCORE, 1000)));
        metrics.add(PostureMetric.counting(SEVERITY_PREFIX + "HIGH", now -> and(gte(SEVERITY_SCORE, 100), lt(SEVERITY_SCORE, 1000))));
        metrics.add(PostureMetric.counting(SEVERITY_PREFIX + "MEDIUM", now -> and(gte(SEVERITY_SCORE, 10), lt(SEVERITY_SCORE, 100))));
        metrics.add(PostureMetric.counting(SEVERITY_PREFIX + "LOW", now -> and(gt(SEVERITY_SCORE, 1), lt(SEVERITY_SCORE, 10))));

        metrics.add(PostureMetric.counting(TESTED_IN_LOOKBACK,
                now -> gt(ifNull(field(ApiInfo.LAST_TESTED), 0), now - Constants.ONE_MONTH_TIMESTAMP)));
        // never tested: no lastTested, or the 0 the ApiInfo pojo writes for it (the old action item filter only
        // matched a missing field, which a pojo written document never has)
        metrics.add(PostureMetric.counting(NOT_TESTED, now -> not(gt(ifNull(field(ApiInfo.LAST_TESTED), 0), 0))));
        metrics.add(PostureMetric.counting(ONLY_ONCE_TESTED, now -> and(ne(type(field(ApiInfo.LAST_TESTED)), "missing"),
                eq(field(ApiInfo.TOTAL_TESTED_COUNT), 1))));

        metrics.add(PostureMetric.counting(HIGH_RISK, now -> gt(RISK_SCORE, 3)));
        metrics.add(PostureMetric.counting(HIGH_RISK_THIRD_PARTY, now -> and(gte(RISK_SCORE, 4),
                in(ApiAccessType.THIRD_PARTY.name(), ACCESS_TYPES))));
        metrics.add(PostureMetric.counting(UNAUTHENTICATED, now -> HAS_UNAUTHENTICATED));
        metrics.add(PostureMetric.counting(THIRD_PARTY, now -> in(ApiAccessType.THIRD_PARTY.name(), ACCESS_TYPES)));
        metrics.add(new PostureMetric(RISK_SCORE_SUM, now -> round(multiply(RISK_SCORE, RISK_SCORE_SCALE))));

        metrics.add(PostureMetric.counting(AUTH_NOT_CALCULATED, now -> eq(size(AUTH_SETS), 0)));
        metrics.add(PostureMetric.counting(ACCESS_NOT_CALCULATED, now -> eq(size(ACCESS_TYPES), 0)));
        metrics.add(PostureMetric.counting(API_TYPE_MISSING, now -> eq(ifNull(field(ApiInfo.API_TYPE), null), null)));
        return metrics;
    }

    static PostureMetric of(String field) {
        return ALL.stream().filter(metric -> metric.field.equals(field)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown posture metric " + field));
    }

    /** Whether the api_info document is one this metric counts, as a $match condition. */
    Bson filter(int now) {
        return Filters.expr(eq(sumExpression.apply(now), 1));
    }

    /**
     * The condition of the sensitive and unauthenticated action item: one of the api's observed auth sets
     * is exactly {UNAUTHENTICATED} (not just one that includes it), as the dashboard always counted it.
     */
    static Bson onlyUnauthenticatedFilter() {
        return Filters.expr(in(literal(Collections.singletonList(AuthType.UNAUTHENTICATED)), AUTH_SETS));
    }

    static String riskField(int bucket) {
        return RISK_PREFIX + bucket;
    }

}
