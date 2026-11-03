package com.akto.merging;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.filter.DictionaryFilter;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.*;
import java.util.regex.Pattern;

import static com.akto.dto.type.KeyTypes.patternToSubType;
import static com.akto.runtime.RuntimeUtil.isAlphanumericString;
import static com.akto.runtime.RuntimeUtil.isValidLocaleToken;
import static com.akto.runtime.RuntimeUtil.isValidVersionToken;

/**
 * O(n * tokenLength) static-to-static URL merging replacing O(n^2) brute force.
 *
 * Tier 1 — Signature grouping: tag free-merge tokens (INT/UUID/VER/LOC),
 *          keep ALNUM/OPAQUE/literals as-is. Same sig = same bucket = instant merge.
 *
 * Tier 2 — Skip-1 grouping: for remaining singletons, skip one ALNUM/OPAQUE position
 *          and tag it. Handles exactly-one opaque-string diff + any typed diffs.
 *
 * Absorption — remaining URLs matched against newly discovered templates.
 */
class OptimizedMerger {

    private static final LoggerMaker loggerMaker = new LoggerMaker(OptimizedMerger.class, LogDb.DB_ABS);
    private static final Pattern UUID_PATTERN = patternToSubType.get(SingleTypeInfo.UUID);

    // ---- Token classification ----

    enum TokenTag { INT, UUID, VER, LOC, ALNUM, OPAQUE }

    /**
     * Classify a token. Returns null for literals (English words or
     * tokens that can't merge given the flags).
     * Priority mirrors tryMergeUrls: English > numeric > UUID > version > locale > alphanumeric > opaque.
     */
    static TokenTag classifyToken(String token, boolean allowStringMerging, boolean allowMergingOnVersions) {
        if (DictionaryFilter.isEnglishWord(token)) return null;
        if (NumberUtils.isParsable(token)) return TokenTag.INT;
        if (allowStringMerging && UUID_PATTERN.matcher(token).matches()) return TokenTag.UUID;
        if (allowMergingOnVersions && isValidVersionToken(token)) return TokenTag.VER;
        if (isValidLocaleToken(token)) return TokenTag.LOC;
        if (allowStringMerging && isAlphanumericString(token)) return TokenTag.ALNUM;
        if (token.equalsIgnoreCase("api")) return null;
        if (allowStringMerging) return TokenTag.OPAQUE;
        return null;
    }

    /** INT/UUID/VER/LOC don't increment templatizedStrTokens in tryMergeUrls. */
    private static boolean isFreeMergeTag(TokenTag tag) {
        return tag == TokenTag.INT || tag == TokenTag.UUID
                || tag == TokenTag.VER || tag == TokenTag.LOC;
    }

    private static SingleTypeInfo.SuperType tagToSuperType(TokenTag tag) {
        switch (tag) {
            case INT: return SingleTypeInfo.SuperType.INTEGER;
            case VER: return SingleTypeInfo.SuperType.VERSIONED;
            case LOC: return SingleTypeInfo.SuperType.LOCALE;
            default:  return SingleTypeInfo.SuperType.STRING; // UUID, ALNUM, OPAQUE
        }
    }

    // ---- Parsed URL ----

    static class ParsedUrl {
        final String key;       // "GET /api/users/123"
        final String method;
        final String[] tokens;  // ["api", "users", "123"]
        final TokenTag[] tags;  // [null,  null,    INT]

        ParsedUrl(String key, String method, String[] tokens, TokenTag[] tags) {
            this.key = key;
            this.method = method;
            this.tokens = tokens;
            this.tags = tags;
        }
    }

    static List<ParsedUrl> parseUrls(Set<String> urls, boolean allowStringMerging, boolean allowMergingOnVersions) {
        List<ParsedUrl> result = new ArrayList<>(urls.size());
        for (String key : urls) {
            if (key == null || key.isEmpty() || !key.contains(" ")) continue;
            String[] parts = key.split(" ", 2);
            if (parts.length < 2 || parts[1].isEmpty() || parts[1].contains("//")) continue;
            String[] tokens = MergingLogic.tokenize(parts[1]);
            if (tokens.length <= 1) continue;

            TokenTag[] tags = new TokenTag[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                tags[i] = classifyToken(tokens[i], allowStringMerging, allowMergingOnVersions);
            }
            result.add(new ParsedUrl(key, parts[0], tokens, tags));
        }
        return result;
    }

    // ---- Tier 1: signature grouping ----

    /**
     * Free-merge tags → "#TAG", everything else → literal value.
     * Example: "GET|4|api|#INT|users|profile"
     */
    static String buildTier1Signature(ParsedUrl url) {
        StringBuilder sb = new StringBuilder(url.method.length() + url.tokens.length * 8);
        sb.append(url.method).append('|').append(url.tokens.length);
        for (int i = 0; i < url.tokens.length; i++) {
            sb.append('|');
            if (isFreeMergeTag(url.tags[i])) {
                sb.append('#').append(url.tags[i].name());
            } else {
                sb.append(url.tokens[i]);
            }
        }
        return sb.toString();
    }

    static URLTemplate buildTier1Template(ParsedUrl representative) {
        int len = representative.tokens.length;
        String[] tplTokens = new String[len];
        SingleTypeInfo.SuperType[] types = new SingleTypeInfo.SuperType[len];
        for (int i = 0; i < len; i++) {
            if (isFreeMergeTag(representative.tags[i])) {
                types[i] = tagToSuperType(representative.tags[i]);
            } else {
                tplTokens[i] = representative.tokens[i];
            }
        }
        return new URLTemplate(tplTokens, types, URLMethods.Method.fromString(representative.method));
    }

    // ---- Tier 2: skip-1 key grouping ----

    /**
     * Skip one ALNUM/OPAQUE position → tag it. Free-merge positions → tagged.
     * Rest → literal. Encodes skipPos to prevent cross-position collisions.
     */
    static String buildTier2Key(ParsedUrl url, int skipPos) {
        if (url.tags[skipPos] == null) return null;
        StringBuilder sb = new StringBuilder(url.method.length() + url.tokens.length * 8 + 10);
        sb.append(url.method).append('|').append(url.tokens.length).append("|skip=").append(skipPos);
        for (int i = 0; i < url.tokens.length; i++) {
            sb.append('|');
            if (i == skipPos) {
                sb.append('#').append(url.tags[i].name());
            } else if (isFreeMergeTag(url.tags[i])) {
                sb.append('#').append(url.tags[i].name());
            } else {
                sb.append(url.tokens[i]);
            }
        }
        return sb.toString();
    }

    static URLTemplate buildTier2Template(ParsedUrl representative, int skipPos) {
        int len = representative.tokens.length;
        String[] tplTokens = new String[len];
        SingleTypeInfo.SuperType[] types = new SingleTypeInfo.SuperType[len];
        for (int i = 0; i < len; i++) {
            if (i == skipPos || isFreeMergeTag(representative.tags[i])) {
                types[i] = tagToSuperType(representative.tags[i]);
            } else {
                tplTokens[i] = representative.tokens[i];
            }
        }
        return new URLTemplate(tplTokens, types, URLMethods.Method.fromString(representative.method));
    }

    /** ALNUM → instant (≥2 URLs). OPAQUE → body match (≥11 URLs). */
    private static int tier2Threshold(TokenTag skipTag) {
        return skipTag == TokenTag.OPAQUE ? MergingLogic.STRING_MERGING_THRESHOLD + 1 : 2;
    }

    // ---- Main entry ----

    static MergingLogic.ApiMergerResult mergeStaticUrls(
            Set<String> staticUrls,
            boolean allowStringMerging,
            boolean allowMergingOnVersions,
            int apiCollectionId) {

        Map<URLTemplate, Set<String>> result = new HashMap<>();
        List<ParsedUrl> allUrls = parseUrls(staticUrls, allowStringMerging, allowMergingOnVersions);

        if (allUrls.size() < 2) {
            return new MergingLogic.ApiMergerResult(result);
        }

        Set<String> consumed = new HashSet<>();

        // Group by token count
        Map<Integer, List<ParsedUrl>> byTokenCount = new HashMap<>();
        for (ParsedUrl url : allUrls) {
            byTokenCount.computeIfAbsent(url.tokens.length, k -> new ArrayList<>()).add(url);
        }

        for (List<ParsedUrl> group : byTokenCount.values()) {
            runTier1(group, result, consumed, apiCollectionId);

            List<ParsedUrl> remaining = new ArrayList<>();
            for (ParsedUrl u : group) {
                if (!consumed.contains(u.key)) remaining.add(u);
            }
            runTier2(remaining, result, consumed, apiCollectionId);
        }

        loggerMaker.infoAndAddToDb("OptimizedMerger: " + result.size() + " new templates, "
                + consumed.size() + " URLs consumed for collection " + apiCollectionId, LogDb.DB_ABS);

        return new MergingLogic.ApiMergerResult(result);
    }

    private static void runTier1(
            List<ParsedUrl> group,
            Map<URLTemplate, Set<String>> result,
            Set<String> consumed,
            int apiCollectionId) {

        Map<String, List<ParsedUrl>> buckets = new HashMap<>();
        for (ParsedUrl url : group) {
            buckets.computeIfAbsent(buildTier1Signature(url), k -> new ArrayList<>()).add(url);
        }

        for (List<ParsedUrl> bucket : buckets.values()) {
            if (bucket.size() < 2) continue;
            URLTemplate template = buildTier1Template(bucket.get(0));
            if (!hasAnyType(template)) continue;
            if (MergingLogic.isDemergedUrl(template, apiCollectionId)) continue;

            Set<String> urlSet = new HashSet<>();
            for (ParsedUrl u : bucket) urlSet.add(u.key);
            result.computeIfAbsent(template, k -> new HashSet<>()).addAll(urlSet);
            consumed.addAll(urlSet);
        }
    }

    private static void runTier2(
            List<ParsedUrl> remaining,
            Map<URLTemplate, Set<String>> result,
            Set<String> consumed,
            int apiCollectionId) {

        if (remaining.size() < 2) return;

        Map<String, List<ParsedUrl>> buckets = new HashMap<>();
        Map<String, Integer> skipPosMap = new HashMap<>();

        for (ParsedUrl url : remaining) {
            for (int i = 0; i < url.tokens.length; i++) {
                if (url.tags[i] != TokenTag.ALNUM && url.tags[i] != TokenTag.OPAQUE) continue;
                String key = buildTier2Key(url, i);
                if (key == null) continue;
                buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(url);
                skipPosMap.putIfAbsent(key, i);
            }
        }

        for (Map.Entry<String, List<ParsedUrl>> entry : buckets.entrySet()) {
            List<ParsedUrl> bucket = entry.getValue();
            bucket.removeIf(u -> consumed.contains(u.key));
            if (bucket.size() < 2) continue;

            int skipPos = skipPosMap.get(entry.getKey());
            TokenTag skipTag = bucket.get(0).tags[skipPos];

            int threshold = tier2Threshold(skipTag);
            if (bucket.size() < threshold) continue;
            if (skipTag == TokenTag.OPAQUE) continue; // needs body match — conservative skip

            URLTemplate template = buildTier2Template(bucket.get(0), skipPos);
            if (!hasAnyType(template)) continue;
            if (MergingLogic.isDemergedUrl(template, apiCollectionId)) continue;

            Set<String> urlSet = new HashSet<>();
            for (ParsedUrl u : bucket) urlSet.add(u.key);
            result.computeIfAbsent(template, k -> new HashSet<>()).addAll(urlSet);
            consumed.addAll(urlSet);
        }
    }

    private static boolean hasAnyType(URLTemplate template) {
        for (SingleTypeInfo.SuperType t : template.getTypes()) {
            if (t != null) return true;
        }
        return false;
    }
}
