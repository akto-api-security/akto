package com.akto.hybrid_runtime.policies;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class UserAgentClassifier {

    public enum Category {
        INFRA, BOT, AI_AGENT, MOBILE_APP, BROWSER, API_CLIENT, UNKNOWN
    }

    private static class Rule {
        final Category category;
        final List<Pattern> patterns;

        Rule(Category category, String... regexes) {
            this.category = category;
            List<Pattern> compiled = new ArrayList<>();
            for (String p : regexes) {
                compiled.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
            }
            this.patterns = Collections.unmodifiableList(compiled);
        }

        boolean matches(String ua) {
            for (Pattern p : patterns) {
                if (p.matcher(ua).find()) return true;
            }
            return false;
        }
    }

    private static final List<Rule> RULES;

    static {
        List<Rule> rules = new ArrayList<>();
        rules.add(new Rule(Category.INFRA,
            "\\bkube-probe\\b", "\\belb-healthchecker\\b", "\\bgooglehc\\b",
            "\\bgce-health-check\\b", "\\bconsul\\b", "\\bprometheus\\b",
            "\\bgrafana-agent\\b", "\\bdatadog\\b", "\\bnewrelic\\b",
            "\\bpingdom\\b", "\\buptimerobot\\b", "\\bnagios\\b",
            "\\bzabbix\\b", "\\bcatchpoint\\b", "\\benvoy\\b",
            "\\bistio\\b", "\\blinkerd\\b", "\\bcloudflare\\b",
            "\\bakamai\\b", "\\bfastly\\b"
        ));
        rules.add(new Rule(Category.BOT,
            "\\bgooglebot\\b", "\\bbingbot\\b", "\\bapplebot\\b",
            "\\byandexbot\\b", "\\bduckduckbot\\b", "\\bbytespider\\b",
            "\\bfacebookexternalhit\\b", "\\btwitterbot\\b", "\\blinkedinbot\\b",
            "\\bslackbot\\b", "\\bdiscordbot\\b", "\\btelegrambot\\b",
            "\\bahrefsbot\\b", "\\bsemrushbot\\b", "\\bmj12bot\\b",
            "\\bcrawl\\b", "\\bspider\\b", "\\bslurp\\b", "\\bbot\\b"
        ));
        rules.add(new Rule(Category.AI_AGENT,
            "\\blangchain\\b", "\\bllamaindex\\b", "\\blitellm\\b",
            "\\bautogpt\\b", "\\bcrewai\\b", "\\bsemantic-kernel\\b",
            "\\bopenai-python\\b", "\\banthropic-sdk\\b", "\\bvercel ai\\b"
        ));
        rules.add(new Rule(Category.MOBILE_APP,
            "android.*okhttp", "dalvik.*okhttp", "android", "dalvik",
            "cfnetwork.*darwin", "iphone", "ipad", "ios", "nsurlsession",
            "\\bflutter\\b", "\\bdart/\\b", "\\breactnative\\b", "\\bexpo\\b"
        ));
        rules.add(new Rule(Category.BROWSER,
            "\\bedg/\\d+", "\\bchrome/\\d+", "\\bfirefox/\\d+",
            "\\bsafari/\\d+", "\\bopr/\\d+", "\\bchromium/\\d+"
        ));
        rules.add(new Rule(Category.API_CLIENT,
            "\\bpython-requests\\b", "\\bhttpx\\b", "\\baiohttp\\b",
            "\\burllib\\b", "\\baxios\\b", "\\bnode-fetch\\b",
            "\\bfetch\\b", "\\bgot/\\b", "\\bcurl/\\d+", "\\bwget/\\d+",
            "\\bgo-http-client\\b", "\\bokhttp/\\d+", "\\bapache-httpclient\\b",
            "\\bjava/\\d+", "\\bjava-http-client\\b", "\\bpostmanruntime\\b",
            "\\binsomnia\\b", "\\bhttpie\\b", "\\bruby\\b", "\\bphp\\b", "\\bgrpc\\b"
        ));
        RULES = Collections.unmodifiableList(rules);
    }

    public static Category classify(String ua) {
        if (ua == null || ua.trim().isEmpty()) return Category.UNKNOWN;
        String normalized = ua.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (rule.matches(normalized)) return rule.category;
        }
        return Category.UNKNOWN;
    }

    public static String extractRefererHost(String referer) {
        if (referer == null || referer.trim().isEmpty()) return null;
        try {
            String host = new URI(referer).getHost();
            return (host != null && !host.trim().isEmpty()) ? host : null;
        } catch (Exception e) {
            return null;
        }
    }
}
