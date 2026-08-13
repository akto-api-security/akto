package com.akto.util.http_request;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.http.HttpHeaders;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Okta Management API client for the periodic user-sync cron. Separate from
 * {@link CustomHttpRequest}'s single-page helpers (used by the login-time fallback in
 * SignupAction) because bulk sync needs two things those don't have: pagination (Okta's
 * Link response header) and basic 429 backoff — both required once you're listing an
 * entire org's users/groups instead of one user's groups at login.
 */
public class OktaApiClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(OktaApiClient.class, LogDb.DASHBOARD);
    private static final OkHttpClient httpClient = CoreHTTPClient.client;
    private static final Type LIST_OF_MAPS = new TypeToken<List<Map<String, Object>>>() {}.getType();

    // Safety caps so a misbehaving org/token can't spin this forever.
    private static final int MAX_PAGES = 500;
    private static final int MAX_RETRIES_ON_429 = 4;
    private static final int PAGE_SIZE = 200;

    public static class OktaGroupRef {
        public final String id;
        public final String name;
        public OktaGroupRef(String id, String name) { this.id = id; this.name = name; }
    }

    public static class OktaUserRef {
        public final String id;
        public final String login;
        public final String email;
        public OktaUserRef(String id, String login, String email) {
            this.id = id;
            this.login = login;
            this.email = email;
        }
    }

    /** GET /api/v1/groups — every group in the org, id + name, fully paginated. */
    public static List<OktaGroupRef> fetchAllGroups(String managementBaseUrl, String apiToken) {
        List<OktaGroupRef> groups = new ArrayList<>();
        String url = managementBaseUrl + "/api/v1/groups?limit=" + PAGE_SIZE;
        int pages = 0;
        while (url != null && pages < MAX_PAGES) {
            PagedResponse resp = getPaged(url, apiToken);
            if (resp == null) break;
            for (Map<String, Object> item : resp.items) {
                String id = asString(item.get("id"));
                Object profileObj = item.get("profile");
                String name = profileObj instanceof Map ? asString(((Map<?, ?>) profileObj).get("name")) : null;
                if (id != null && name != null) groups.add(new OktaGroupRef(id, name));
            }
            url = resp.nextUrl;
            pages++;
        }
        return groups;
    }

    /** GET /api/v1/groups/{groupId}/users — every member of one group, fully paginated. */
    public static List<OktaUserRef> fetchGroupMembers(String managementBaseUrl, String apiToken, String groupId) {
        return fetchUserPages(managementBaseUrl + "/api/v1/groups/" + groupId + "/users?limit=" + PAGE_SIZE, apiToken);
    }

    /**
     * GET /api/v1/users — every active user in the org, fully paginated. Needed alongside
     * fetchGroupMembers: upsertDeviceTags does a full-replace-by-source, so a user who dropped
     * out of every group still needs to be visited (with an empty group list) for their stale
     * "okta"-sourced tag to actually clear — group rosters alone would just silently skip them.
     */
    public static List<OktaUserRef> fetchAllUsers(String managementBaseUrl, String apiToken) {
        String filter = "status eq \"ACTIVE\"";
        String url = managementBaseUrl + "/api/v1/users?limit=" + PAGE_SIZE + "&filter=" + urlEncode(filter);
        return fetchUserPages(url, apiToken);
    }

    private static List<OktaUserRef> fetchUserPages(String startUrl, String apiToken) {
        List<OktaUserRef> users = new ArrayList<>();
        String url = startUrl;
        int pages = 0;
        while (url != null && pages < MAX_PAGES) {
            PagedResponse resp = getPaged(url, apiToken);
            if (resp == null) break;
            for (Map<String, Object> item : resp.items) {
                users.add(toUserRef(item));
            }
            url = resp.nextUrl;
            pages++;
        }
        return users;
    }

    private static OktaUserRef toUserRef(Map<String, Object> item) {
        String id = asString(item.get("id"));
        Object profileObj = item.get("profile");
        String login = null, email = null;
        if (profileObj instanceof Map) {
            Map<?, ?> profile = (Map<?, ?>) profileObj;
            login = asString(profile.get("login"));
            email = asString(profile.get("email"));
        }
        return new OktaUserRef(id, login, email);
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static class PagedResponse {
        List<Map<String, Object>> items;
        String nextUrl;
    }

    private static PagedResponse getPaged(String url, String apiToken) {
        Request request = new Request.Builder()
                .url(url)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, "SSWS " + apiToken)
                .build();

        for (int attempt = 0; attempt <= MAX_RETRIES_ON_429; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 429) {
                    long backoffMs = retryAfterMillis(response, attempt);
                    loggerMaker.infoAndAddToDb("[Okta] 429 rate-limited on " + url + ", backing off " + backoffMs + "ms", LogDb.DASHBOARD);
                    sleep(backoffMs);
                    continue;
                }
                if (!response.isSuccessful()) {
                    loggerMaker.errorAndAddToDb("[Okta] request failed (" + response.code() + "): " + url, LogDb.DASHBOARD);
                    return null;
                }
                String body = response.body() != null ? response.body().string() : "[]";
                List<Map<String, Object>> items = new Gson().fromJson(body, LIST_OF_MAPS);
                PagedResponse result = new PagedResponse();
                result.items = items != null ? items : Collections.emptyList();
                result.nextUrl = parseNextLink(response.header("Link"));
                return result;
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "[Okta] error calling " + url + ": " + e.getMessage(), LogDb.DASHBOARD);
                return null;
            }
        }
        loggerMaker.errorAndAddToDb("[Okta] giving up after repeated 429s: " + url, LogDb.DASHBOARD);
        return null;
    }

    /** Okta's Link header looks like: {@code <url1>; rel="self", <url2>; rel="next"}. */
    private static String parseNextLink(String linkHeader) {
        if (linkHeader == null) return null;
        for (String part : linkHeader.split(",")) {
            if (part.contains("rel=\"next\"")) {
                int start = part.indexOf('<');
                int end = part.indexOf('>');
                if (start >= 0 && end > start) return part.substring(start + 1, end);
            }
        }
        return null;
    }

    private static long retryAfterMillis(Response response, int attempt) {
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                // fall through to exponential backoff
            }
        }
        return (long) Math.pow(2, attempt) * 1000L;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
