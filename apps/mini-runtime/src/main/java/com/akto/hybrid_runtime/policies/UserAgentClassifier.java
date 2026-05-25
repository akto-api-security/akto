package com.akto.hybrid_runtime.policies;

import java.util.Arrays;
import java.util.List;

public class UserAgentClassifier {

    // Priority order: Infra → Bot → AI Agent → Mobile App → Browser → Service
    // Each entry: { category, keyword1, keyword2, ... }
    private static final List<String[]> UA_RULES = Arrays.asList(
        new String[]{"Infra",       "kube-probe", "elb-healthchecker", "googlehc", "consul", "prometheus", "datadog", "newrelic", "pingdom", "uptimerobot", "nagios", "zabbix", "catchpoint"},
        new String[]{"Bot",         "bot", "crawl", "spider", "slurp", "facebookexternalhit", "twitterbot", "ahrefsbot", "semrushbot", "duckduckbot", "mj12bot", "screaming frog"},
        new String[]{"AI Agent",    "langchain", "llamaindex", "openai", "anthropic", "cohere", "huggingface", "llama", "litellm", "autogpt"},
        new String[]{"Mobile App",  "okhttp", "alamofire", "cfnetwork", "nsurlsession", "dart/", "flutter", "reactnative", "expo", "retrofit"},
        new String[]{"Browser",     "mozilla/", "chrome/", "firefox/", "safari/", "edge/", "opera/", "chromium/"},
        new String[]{"Service",     "python-requests", "python-httpx", "axios", "curl/", "wget/", "go-http-client", "java/", "node-fetch", "got/", "ruby", "php", "httpie", "insomnia", "postman"}
    );

    public static String classify(String ua) {
        if (ua == null || ua.isEmpty()) return null;
        String lower = ua.toLowerCase();
        for (String[] rule : UA_RULES) {
            for (int i = 1; i < rule.length; i++) {
                if (lower.contains(rule[i])) return rule[0];
            }
        }
        return null;
    }

    public static String classifyReferer(String referer, String requestHost) {
        if (referer == null || referer.isEmpty()) return null;
        if (requestHost != null && referer.contains(requestHost)) return "Same-origin";
        return "External";
    }
}
