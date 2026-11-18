package com.akto.service.atlas;

import com.akto.dao.AgentUsersDao;
import com.akto.dao.AllowVendorAllowlistDao;
import com.akto.dao.McpAllowlistDao;
import com.akto.dao.context.Context;
import com.akto.dto.AgenticUsers;
import com.akto.dto.DeviceTag;
import com.akto.dto.McpAllowlist;
import com.akto.service.insights.InsightVendorNames;
import com.akto.utils.TtlCache;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The account-wide inputs discovery needs besides its tool rows: the allowlist and each user's team. A failed read throws, so it is never taken for "none". */
public class AtlasPeopleSource {

    private static final long CACHE_TTL_MS = 60_000;
    private static final int CACHE_MAX_ENTRIES = 1000;
    private static final String TEAM_TAG_KEY = "team";
    private static final TtlCache<Integer, Snapshot> CACHE = new TtlCache<>(CACHE_TTL_MS, CACHE_MAX_ENTRIES);

    @Getter
    @AllArgsConstructor
    public static class Snapshot {
        private final Set<String> allowlistNamesLower;
        private final Map<String, String> teamByUser;
    }

    public Snapshot load() {
        Integer account = Context.accountId.get();
        Snapshot cached = CACHE.get(account);
        if (cached != null) return cached;
        // Read outside any lock; two concurrent loads at worst both read and the later put wins.
        Snapshot snapshot = new Snapshot(readAllowlistNames(), readTeams());
        CACHE.put(account, snapshot);
        return snapshot;
    }

    private static Set<String> readAllowlistNames() {
        Bson projection = Projections.include(McpAllowlist.NAME, McpAllowlist.ENTRY_TYPE);
        List<McpAllowlist> rows = new ArrayList<>(McpAllowlistDao.instance.findAll(
                Filters.ne(McpAllowlist.ENTRY_TYPE, McpAllowlist.ENTRY_TYPE_VENDOR), projection));
        rows.addAll(AllowVendorAllowlistDao.instance.findAll(Filters.empty(), projection));
        return allowlistNames(rows);
    }

    // A vendor entry is matched by its canonical name, the way observed traffic is named; a server entry by its own name.
    static Set<String> allowlistNames(List<McpAllowlist> rows) {
        Set<String> names = new HashSet<>();
        for (McpAllowlist row : rows) {
            if (row.getName() == null) continue;
            String nameLower = row.getName().toLowerCase(Locale.ROOT);
            boolean vendor = McpAllowlist.ENTRY_TYPE_VENDOR.equals(row.getEntryType());
            names.add(vendor ? InsightVendorNames.canonical(nameLower).toLowerCase(Locale.ROOT) : nameLower);
        }
        return names;
    }

    private static Map<String, String> readTeams() {
        return teamsOf(AgentUsersDao.instance.findAll(Filters.empty(),
                Projections.include(AgenticUsers.USER_NAME, AgenticUsers.DEVICE_TAGS)));
    }

    // "team" is a tag convention, not a typed field: a user's team is their first non-blank team tag.
    static Map<String, String> teamsOf(List<AgenticUsers> users) {
        Map<String, String> teams = new HashMap<>();
        for (AgenticUsers user : users) {
            if (user.getUserName() == null || user.getDeviceTags() == null) continue;
            for (DeviceTag tag : user.getDeviceTags()) {
                if (TEAM_TAG_KEY.equalsIgnoreCase(tag.getKey()) && StringUtils.isNotBlank(tag.getValue())) {
                    teams.put(user.getUserName(), tag.getValue().trim());
                    break;
                }
            }
        }
        return teams;
    }
}
