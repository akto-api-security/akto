package com.akto.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.User;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

/*
 * Scopes Argus data (guardrail policies, guardrail activity, traces) for users limited to specific
 * collections (Advanced RBAC). Every method keeps the current behaviour when the user is not
 * limited - admins, users without assigned collections, accounts without the RBAC feature, and
 * products other than Argus.
 */
public class ArgusCollectionScope {

    /** True only for Argus requests. Atlas (ENDPOINT), API Security and DAST are never affected. */
    public static boolean isArgusContext() {
        CONTEXT_SOURCE contextSource = Context.contextSource.get();
        return contextSource == CONTEXT_SOURCE.AGENTIC || contextSource == CONTEXT_SOURCE.MCP
                || contextSource == CONTEXT_SOURCE.GEN_AI;
    }

    /** Collection ids the user is limited to, or null if the user is not limited. */
    public static List<Integer> getRestrictedCollectionIds(User user) {
        Integer accountId = Context.accountId.get();
        // Same as roleAccessInterceptor: roles are only enforced on metered (SaaS / on-prem) dashboards with the RBAC feature
        if (accountId == null || user == null || !isArgusContext() || !DashboardMode.isMetered()
                || !UsageMetricCalculator.isRbacFeatureAvailable(accountId)) {
            return null;
        }
        List<Integer> userCollectionIds = RBACDao.instance.getUserCollectionsById(user.getId(), accountId);
        return (userCollectionIds == null || userCollectionIds.isEmpty()) ? null : userCollectionIds;
    }

    public static boolean isLimited(User user) {
        return getRestrictedCollectionIds(user) != null;
    }

    /** Collections the user is limited to, or null if the user is not limited. */
    public static List<ApiCollection> getRestrictedCollections(User user) {
        List<Integer> collectionIds = getRestrictedCollectionIds(user);
        return collectionIds == null ? null : ApiCollectionsDao.instance.getMetaForIds(collectionIds);
    }

    /** Host names of the collections the user is limited to (can be empty), or null if the user is not limited. */
    public static Set<String> getRestrictedHosts(User user) {
        List<ApiCollection> collections = getRestrictedCollections(user);
        if (collections == null) {
            return null;
        }
        Set<String> hosts = new LinkedHashSet<>();
        for (ApiCollection collection : collections) {
            if (collection.getHostName() != null && !collection.getHostName().isEmpty()) {
                hosts.add(collection.getHostName());
            }
        }
        return hosts;
    }

    /*
     * Narrows a guardrail activity (malicious events) request filter to the user's own agents, in place.
     * Events are matched the same three ways the UI matches them to an asset - exact host,
     * "<firstSegment> <lastSegment>" loose key, and claude config events by device id - and the
     * threat backend ORs these, so all three are rebuilt from the user's own hosts only: what was
     * requested narrowed to them, or all of them when nothing host-specific was requested.
     * Leaves the filter unchanged if the user is not limited.
     * Returns false if nothing is visible to the user - callers must return no results.
     */
    public static boolean scopeActivityFilters(User user, Map<String, Object> filters) {
        Set<String> allowedHosts = getRestrictedHosts(user);
        if (allowedHosts == null) {
            return true;
        }
        Set<String> allowedLooseKeys = new LinkedHashSet<>();
        Set<String> allowedClaudeDeviceIds = new LinkedHashSet<>();
        for (String host : allowedHosts) {
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                allowedLooseKeys.add(parts[0] + " " + parts[parts.length - 1]);
                if ("claude".equalsIgnoreCase(parts[parts.length - 1]) && !parts[0].isEmpty()) {
                    allowedClaudeDeviceIds.add(parts[0]);
                }
            }
        }

        List<String> requestedHosts = toStringList(filters.get("hosts"));
        List<String> requestedLooseKeys = toStringList(filters.get("looseHostKeys"));
        List<String> requestedClaudeDeviceIds = toStringList(filters.get("claudeDeviceIds"));
        boolean matchClaudeConfig = Boolean.TRUE.equals(filters.get("matchClaudeConfig"));
        boolean anyRequested = !requestedHosts.isEmpty() || !requestedLooseKeys.isEmpty()
                || !requestedClaudeDeviceIds.isEmpty() || matchClaudeConfig;

        List<String> hosts = anyRequested ? intersect(requestedHosts, allowedHosts) : new ArrayList<>(allowedHosts);
        List<String> looseKeys = anyRequested ? intersect(requestedLooseKeys, allowedLooseKeys) : new ArrayList<>(allowedLooseKeys);
        List<String> claudeDeviceIds = (!anyRequested || matchClaudeConfig)
                ? new ArrayList<>(allowedClaudeDeviceIds) : intersect(requestedClaudeDeviceIds, allowedClaudeDeviceIds);

        filters.put("hosts", hosts);
        filters.put("looseHostKeys", looseKeys);
        filters.put("claudeDeviceIds", claudeDeviceIds);
        filters.remove("matchClaudeConfig");
        return !(hosts.isEmpty() && looseKeys.isEmpty() && claudeDeviceIds.isEmpty());
    }

    /** Values the user may filter on: requested narrowed to allowed, or all allowed when nothing was requested. */
    public static List<String> scopeValues(List<String> requested, Collection<String> allowed) {
        if (requested == null || requested.isEmpty()) {
            return new ArrayList<>(allowed);
        }
        return intersect(requested, allowed);
    }

    private static List<String> intersect(List<String> requested, Collection<String> allowed) {
        Set<String> allowedSet = new HashSet<>(allowed);
        List<String> result = new ArrayList<>();
        for (String value : requested) {
            if (value != null && allowedSet.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                if (item != null) result.add(item.toString());
            }
        }
        return result;
    }
}
