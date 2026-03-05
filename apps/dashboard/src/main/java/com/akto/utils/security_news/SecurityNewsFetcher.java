package com.akto.utils.security_news;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches agentic AI / MCP security news from configurable RSS URLs.
 * Uses env SECURITY_NEWS_RSS_URLS (comma-separated) if set; otherwise uses default feeds below.
 */
public class SecurityNewsFetcher {

    private static final LoggerMaker logger = new LoggerMaker(SecurityNewsFetcher.class, LogDb.DASHBOARD);
    private static final String ENV_RSS_URLS = "SECURITY_NEWS_RSS_URLS";

    private static final String[] DEFAULT_RSS_URLS = new String[]{
            "https://feeds.feedburner.com/TheHackersNews",
            "https://www.darkreading.com/rss.xml",
            "https://krebsonsecurity.com/feed/",
            "https://threatpost.com/feed",
            "https://www.helpnetsecurity.com/feed/",
            "https://www.bleepingcomputer.com/feed/"
    };
    private static final OkHttpClient HTTP_CLIENT = CoreHTTPClient.client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final Pattern ITEM_PATTERN = Pattern.compile("<item[^>]*>(.*?)</item>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>\\s*<!\\[CDATA\\[\\s*(.*?)\\s*\\]\\]>\\s*</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_SIMPLE_PATTERN = Pattern.compile("<title[^>]*>\\s*(.*?)\\s*</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_PATTERN = Pattern.compile("<link[^>]*>\\s*(.*?)\\s*</link>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern DESC_PATTERN = Pattern.compile("<description[^>]*>\\s*<!\\[CDATA\\[\\s*(.*?)\\s*\\]\\]>\\s*</description>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern DESC_SIMPLE_PATTERN = Pattern.compile("<description[^>]*>\\s*(.*?)\\s*</description>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final String[] KEYWORDS = new String[]{
            "MCP", "model context protocol", "agentic", "LLM", "prompt injection",
            "guardrail", "AI security", "AI vulnerability", "jailbreak", "tool abuse"
    };

    public static class RawNewsItem {
        private final String title;
        private final String link;
        private final String description;

        public RawNewsItem(String title, String link, String description) {
            this.title = title != null ? title.trim() : "";
            this.link = link != null ? link.trim() : "";
            this.description = description != null ? stripHtml(description).trim() : "";
        }

        public String getTitle() { return title; }
        public String getLink() { return link; }
        public String getDescription() { return description; }

        private static String stripHtml(String s) {
            if (s == null) return "";
            return s.replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&")
                    .replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", "\"").trim();
        }
    }

    public static List<RawNewsItem> fetch() {
        String[] urlsToFetch;
        String urlsEnv = System.getenv(ENV_RSS_URLS);
        if (urlsEnv != null && !urlsEnv.trim().isEmpty()) {
            urlsToFetch = urlsEnv.split(",");
        } else {
            urlsToFetch = DEFAULT_RSS_URLS;
        }
        List<RawNewsItem> all = new ArrayList<>();
        for (String url : urlsToFetch) {
            String u = url.trim();
            if (u.isEmpty()) continue;
            try {
                all.addAll(fetchFromUrl(u));
            } catch (Exception e) {
                logger.errorAndAddToDb("SecurityNewsFetcher failed for " + u + ": " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
        return all;
    }

    private static List<RawNewsItem> fetchFromUrl(String feedUrl) throws IOException {
        Request request = new Request.Builder().url(feedUrl).get().build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                return new ArrayList<>();
            }
            String xml = body.string();
            return parseRssItems(xml);
        }
    }

    private static List<RawNewsItem> parseRssItems(String xml) {
        List<RawNewsItem> items = new ArrayList<>();
        Matcher itemMatcher = ITEM_PATTERN.matcher(xml);
        while (itemMatcher.find()) {
            String block = itemMatcher.group(1);
            String title = extractGroup(block, TITLE_PATTERN);
            if (title == null) title = extractGroup(block, TITLE_SIMPLE_PATTERN);
            String link = extractGroup(block, LINK_PATTERN);
            String desc = extractGroup(block, DESC_PATTERN);
            if (desc == null) desc = extractGroup(block, DESC_SIMPLE_PATTERN);
            if (title != null && link != null && matchesKeywords(title + " " + (desc != null ? desc : ""))) {
                items.add(new RawNewsItem(title, link, desc));
            }
        }
        return items;
    }

    private static String extractGroup(String block, Pattern p) {
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1).trim() : null;
    }

    private static boolean matchesKeywords(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        for (String kw : KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }
}
