package com.akto.threat.detection.hyperscan;

import com.gliwka.hyperscan.wrapper.Database;
import com.gliwka.hyperscan.wrapper.Expression;
import com.gliwka.hyperscan.wrapper.ExpressionFlag;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hyperscan-based threat pattern matcher for high-performance regex scanning with location awareness.
 *
 * Compiles regex patterns into a single Hyperscan database for efficient single-pass scanning.
 *
 * Pattern file format: prefix::locations::regex_pattern
 * Locations: REQUEST_URL, REQUEST_HEADERS, REQUEST_BODY, RESPONSE_BODY, or ALL
 * Backward compatible: prefix::regex_pattern (defaults to ALL locations)
 */
public class HyperscanThreatMatcher {

    private static final LoggerMaker logger = new LoggerMaker(HyperscanThreatMatcher.class, LogDb.THREAT_DETECTION);
    private static final java.util.regex.Pattern NAMED_GROUP_PATTERN =
            java.util.regex.Pattern.compile("\\(\\?P<([^>]+)>");

    private static HyperscanThreatMatcher INSTANCE;

    private Database hyperscanDatabase;
    private Map<Integer, PatternInfo> patternMap;
    private Map<String, List<Integer>> categoryToPatternIds;
    private boolean initialized = false;
    private ThreadLocal<com.gliwka.hyperscan.wrapper.Scanner> scannerThreadLocal;

    public enum ScanLocation {
        REQUEST_URL, REQUEST_HEADERS, REQUEST_BODY,
        RESPONSE_HEADERS, RESPONSE_BODY, ALL
    }

    public static class PatternInfo {
        public final String prefix;
        public final int patternId;
        public final String category;
        public final Set<ScanLocation> locations;

        public PatternInfo(String prefix, int patternId, String groupName, Set<ScanLocation> locations) {
            this.prefix = prefix;
            this.patternId = patternId;
            this.category = deriveCategory(prefix, groupName);
            this.locations = locations != null ? locations : Collections.singleton(ScanLocation.ALL);
        }

        private static String deriveCategory(String prefix, String groupName) {
            if (groupName != null && !groupName.isEmpty()) return groupName.toLowerCase();
            String[] parts = prefix.split("_");
            return parts.length >= 2 ? parts[1].toLowerCase() : "unknown";
        }
    }

    public static class MatchResult {
        public final String prefix;
        public final String category;
        public final long startOffset;
        public final long endOffset;
        public final String matchedText;
        public final String location;

        public MatchResult(String prefix, String category, long start, long end, String text, String location) {
            this.prefix = prefix;
            this.category = category;
            this.startOffset = start;
            this.endOffset = end;
            this.matchedText = text;
            this.location = location;
        }
    }

    private HyperscanThreatMatcher() {}

    public static synchronized HyperscanThreatMatcher getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new HyperscanThreatMatcher();
        }
        return INSTANCE;
    }

    public synchronized boolean initialize(String patternFilePath) {
        if (initialized) return true;
        try {
            File patternFile = new File(patternFilePath);
            if (!patternFile.exists()) {
                logger.errorAndAddToDb("Pattern file not found: " + patternFilePath);
                return false;
            }
            return initializeFromLines(Files.readAllLines(Paths.get(patternFilePath)));
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Failed to initialize Hyperscan matcher: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean initializeFromClasspath(String resourcePath) {
        if (initialized) return true;
        try {
            InputStream is = HyperscanThreatMatcher.class.getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                logger.errorAndAddToDb("Classpath resource not found: " + resourcePath);
                return false;
            }
            List<String> lines = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.toList());
            return initializeFromLines(lines);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Failed to initialize Hyperscan from classpath: " + e.getMessage());
            return false;
        }
    }

    // --- Pattern parsing helpers ---

    private static String extractGroupName(String pattern) {
        java.util.regex.Matcher m = NAMED_GROUP_PATTERN.matcher(pattern);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Remove (?P&lt;name&gt;....) wrapper — strips the opening tag and one trailing ')' per group.
     */
    static String removeNamedGroups(String pattern) {
        int count = 0;
        java.util.regex.Matcher m = NAMED_GROUP_PATTERN.matcher(pattern);
        while (m.find()) count++;
        String result = NAMED_GROUP_PATTERN.matcher(pattern).replaceAll("");
        for (int i = 0; i < count && result.endsWith(")"); i++) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static Set<ScanLocation> parseLocations(String str) {
        if (str == null || str.isEmpty() || str.equalsIgnoreCase("ALL")) {
            return Collections.singleton(ScanLocation.ALL);
        }
        Set<ScanLocation> locs = new HashSet<>();
        for (String part : str.split(",")) {
            try { locs.add(ScanLocation.valueOf(part.trim().toUpperCase())); }
            catch (IllegalArgumentException ignored) {}
        }
        return locs.isEmpty() ? Collections.singleton(ScanLocation.ALL) : locs;
    }

    // --- Initialization ---

    private synchronized boolean initializeFromLines(List<String> lines) {
        try {
            patternMap = new ConcurrentHashMap<>();
            categoryToPatternIds = new ConcurrentHashMap<>();

            List<Expression> expressions = parsePatternLines(lines);
            if (expressions.isEmpty()) {
                logger.errorAndAddToDb("No valid patterns found");
                return false;
            }

            compileDatabase(expressions);

            logger.infoAndAddToDb(String.format("Hyperscan compiled: %d patterns, %d categories",
                    patternMap.size(), categoryToPatternIds.size()));
            return true;
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Failed to compile Hyperscan database: " + e.getMessage());
            return false;
        }
    }

    private List<Expression> parsePatternLines(List<String> lines) {
        List<Expression> expressions = new ArrayList<>();
        int patternId = 0;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("::", 3);
            if (parts.length < 2) continue;

            String prefix = parts[0].trim();
            String locationsStr = parts.length == 3 ? parts[1].trim() : "ALL";
            String originalPattern = parts.length == 3 ? parts[2].trim() : parts[1].trim();

            try {
                String hsPattern = removeNamedGroups(originalPattern);
                expressions.add(new Expression(hsPattern,
                        EnumSet.of(ExpressionFlag.CASELESS, ExpressionFlag.DOTALL, ExpressionFlag.SOM_LEFTMOST),
                        patternId));

                String groupName = extractGroupName(originalPattern);
                PatternInfo info = new PatternInfo(prefix, patternId, groupName, parseLocations(locationsStr));
                patternMap.put(patternId, info);
                categoryToPatternIds.computeIfAbsent(info.category, k -> new ArrayList<>()).add(patternId);
                patternId++;
            } catch (Exception e) {
                logger.warnAndAddToDb("Failed to compile pattern '" + prefix + "': " + e.getMessage());
            }
        }
        return expressions;
    }

    private void compileDatabase(List<Expression> expressions) throws Exception {
        long start = System.currentTimeMillis();
        hyperscanDatabase = Database.compile(expressions);
        long compileMs = System.currentTimeMillis() - start;
        logger.debugAndAddToDb("Hyperscan database compiled in " + compileMs + "ms");

        initialized = true;
        scannerThreadLocal = ThreadLocal.withInitial(() -> {
            try {
                com.gliwka.hyperscan.wrapper.Scanner s = new com.gliwka.hyperscan.wrapper.Scanner();
                s.allocScratch(hyperscanDatabase);
                return s;
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Failed to allocate scanner: " + e.getMessage());
                return null;
            }
        });
    }

    // --- Scanning ---

    public List<MatchResult> scan(String text, String location) {
        if (!initialized || text == null || text.isEmpty()) return Collections.emptyList();

        List<MatchResult> matches = new ArrayList<>();
        ScanLocation scanLoc = toScanLocation(location);

        try {
            com.gliwka.hyperscan.wrapper.Scanner scanner = scannerThreadLocal.get();
            if (scanner == null) return Collections.emptyList();

            scanner.scan(hyperscanDatabase, text, (expr, from, to) -> {
                PatternInfo info = patternMap.get(expr.getId());
                if (info != null && isLocationAllowed(info.locations, scanLoc)) {
                    int s = (int) Math.min(from, text.length());
                    int e = (int) Math.min(to, text.length());
                    // Hyperscan SOM_LEFTMOST may truncate the last 1-2 chars of the match.
                    // Extend by 2 to compensate, so the phrase can be found in the original text.
                    int eAdj = (int) Math.min(e + 2, text.length());
                    matches.add(new MatchResult(info.prefix, info.category, from, eAdj, text.substring(s, eAdj), location));
                }
                return true;
            });
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error during Hyperscan scan: " + e.getMessage());
        }
        return matches;
    }

    public Map<String, List<MatchResult>> scanRequest(String url, String headers, String body) {
        Map<String, List<MatchResult>> results = new HashMap<>();
        addIfNonEmpty(results, "url", scan(url, "url"));
        addIfNonEmpty(results, "headers", scan(headers, "headers"));
        addIfNonEmpty(results, "body", scan(body, "body"));
        return results;
    }

    public Map<String, List<MatchResult>> scanResponse(String headers, String body) {
        Map<String, List<MatchResult>> results = new HashMap<>();
        addIfNonEmpty(results, "resp_headers", scan(headers, "resp_headers"));
        addIfNonEmpty(results, "resp_body", scan(body, "resp_body"));
        return results;
    }

    // --- Accessors ---

    public int getPatternCount() { return patternMap != null ? patternMap.size() : 0; }
    public int getCategoryCount() { return categoryToPatternIds != null ? categoryToPatternIds.size() : 0; }
    public boolean isInitialized() { return initialized; }

    // --- Private helpers ---

    private static boolean isLocationAllowed(Set<ScanLocation> allowed, ScanLocation actual) {
        return allowed.contains(ScanLocation.ALL) || allowed.contains(actual);
    }

    private static ScanLocation toScanLocation(String loc) {
        switch (loc) {
            case "url": return ScanLocation.REQUEST_URL;
            case "headers": return ScanLocation.REQUEST_HEADERS;
            case "body": return ScanLocation.REQUEST_BODY;
            case "resp_headers": return ScanLocation.RESPONSE_HEADERS;
            case "resp_body": return ScanLocation.RESPONSE_BODY;
            default: return ScanLocation.ALL;
        }
    }

    private static void addIfNonEmpty(Map<String, List<MatchResult>> map, String key, List<MatchResult> list) {
        if (!list.isEmpty()) map.put(key, list);
    }
}
