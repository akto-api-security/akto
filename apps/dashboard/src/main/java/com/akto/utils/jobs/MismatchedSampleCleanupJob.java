package com.akto.utils.jobs;

import com.akto.dao.MCollection;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.HttpRequestResponseUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds and deletes traffic data that the ingestion pipeline attributed to the wrong API (a whole
 * response swapped onto another API of the same collection). See {@code MismatchedSampleCleanupJob.md}
 * for the spec, the pilot that validated it, and the decision tables.
 *
 * <p>Work is done one api collection at a time: that collection's {@code sample_data} is loaded once
 * and turned into a per-endpoint "core shape" model (medoid + majority-shape samples), which is then
 * used by all three passes:
 * <ol>
 *   <li><b>Pass 1</b> sensitive_sample_data: keep a response-side param only if its full path is in the
 *       endpoint's own <i>core</i> samples; delete only when the stored proof's response shape is foreign
 *       to the endpoint and matches another (non-same-API) endpoint in the collection.</li>
 *   <li><b>Pass 2</b> sample_data: pull individual samples whose response shape is foreign to a clear
 *       majority and matches another endpoint (donor evidence). Pulled by content, per exact doc _id.</li>
 *   <li><b>Pass 3</b> single_type_info orphans: sensitive-subtype STI rows with no sensitive doc whose
 *       param never occurs on the endpoint (not even the leaf name) but is native to another endpoint.</li>
 * </ol>
 * Request-side params are never touched. Every delete is first copied to a {@code *_mismatch_backup}
 * collection. {@code dryRun=true} writes nothing to Mongo; every decision is written to TSV reports.
 */
public class MismatchedSampleCleanupJob {

    private static final LoggerMaker logger = new LoggerMaker(MismatchedSampleCleanupJob.class, LogDb.DASHBOARD);

    private static final String QUERY_PARAM_SUFFIX = "_queryParam";

    // Core-shape model
    static final double CORE_SIMILARITY = 0.5;          // sample is "core" if J(sample, medoid) >= this
    static final int MAX_SAMPLES_FOR_MEDOID = 60;

    // Pass 1
    static final int MIN_SAMPLES_PASS1 = 3;
    static final double PASS1_MAX_OWN_SIMILARITY = 0.3; // proof vs endpoint medoid must be below this
    static final double PASS1_MIN_SIBLING_SIMILARITY = 0.6;
    static final double BORROWED_CORE_SIBLING_SIMILARITY = 0.9;
    static final int PROOF_SAMPLES = 3;
    static final double PASS1_STRONG_SIBLING_SIMILARITY = 0.8; // between MIN and STRONG needs extra evidence
    static final double PASS1_WEAK_BAND_MAX_OWN_SIMILARITY = 0.05;
    /** Donor in the same service (first two literal segments) shares conventions: require near-certain evidence. */
    static final double SAME_SERVICE_MAX_OWN_SIMILARITY = 0.05;
    static final double SAME_SERVICE_MIN_SIBLING_SIMILARITY = 0.9;
    /**
     * A JSON shape with fewer paths than this is typically an error/ack envelope ({returnCode, message})
     * shared by many APIs: it can neither prove a donor nor serve as the endpoint's reference shape.
     * An EMPTY shape (HTML/text endpoint) is still valid evidence.
     */
    static final int MIN_SHAPE_PATHS = 5;

    // Pass 2
    static final int MIN_SAMPLES_PASS2 = 5;
    static final double PASS2_OUTLIER_SIMILARITY = 0.1;
    static final double PASS2_MIN_DONOR_SIMILARITY = 0.8;

    // Pass 3
    static final int MIN_SAMPLES_PASS3 = 5;

    // Dynamic-parent detection (unchanged from v2)
    private static final int MIN_SAMPLES_FOR_DYNAMIC_PARENT = 5;
    private static final int MIN_DYNAMIC_PARENT_CHILDREN = 10;
    private static final double MIN_DYNAMIC_PARENT_CHURN = 0.5;
    private static final double MAX_DYNAMIC_PARENT_MAJORITY_SHAPE_RATIO = 0.7;
    private static final double MIN_DYNAMIC_PARENT_VALUE_SHAPE_RATIO = 0.8;

    static final String BACKUP_SUFFIX = "_mismatch_backup";

    public static class Report {
        public int collectionsScanned = 0;
        public int collectionsFailed = 0;
        public int collectionsSkippedByCap = 0;
        public long durationSec = 0;
        // Pass 1
        public int sensitiveDocsScanned = 0;
        public final Map<String, Integer> pass1Verdicts = new java.util.TreeMap<>();
        public int sensitiveDocsDeleted = 0;
        public int singleTypeInfoDocsDeleted = 0;
        // Pass 2
        public int sampleDataEndpointsScanned = 0;
        public int sampleDataEndpointsSkipped = 0;
        public int sampleDataSamplesScanned = 0;
        public int sampleDataSamplesFlagged = 0;
        public int sampleDataSamplesDeleted = 0;
        // Pass 3
        public int orphanStiScanned = 0;
        public int orphanStiFlagged = 0;
        public int orphanStiDeleted = 0;
        public final Map<String, Integer> pass3Verdicts = new java.util.TreeMap<>();
        public final Map<String, Integer> pass1DeleteBySubType = new java.util.TreeMap<>();
        public final Map<String, Integer> pass3DeleteBySubType = new java.util.TreeMap<>();

        @Override
        public String toString() {
            return "MismatchedSampleCleanupJob.Report{\n" +
                    "  collections=" + collectionsScanned + " failed=" + collectionsFailed +
                    " skippedByCap=" + collectionsSkippedByCap + "\n" +
                    "  pass1 sensitive_sample_data: scanned=" + sensitiveDocsScanned + " verdicts=" + pass1Verdicts +
                    " deleted=" + sensitiveDocsDeleted + " pairedStiDeleted=" + singleTypeInfoDocsDeleted + "\n" +
                    "  pass1 delete by subType=" + pass1DeleteBySubType + "\n" +
                    "  pass2 sample_data: endpoints=" + sampleDataEndpointsScanned +
                    " skipped=" + sampleDataEndpointsSkipped + " samples=" + sampleDataSamplesScanned +
                    " flagged=" + sampleDataSamplesFlagged + " deleted=" + sampleDataSamplesDeleted + "\n" +
                    "  pass3 orphan single_type_info: scanned=" + orphanStiScanned + " flagged=" + orphanStiFlagged +
                    " deleted=" + orphanStiDeleted + " bySubType=" + pass3DeleteBySubType + "\n" +
                    "  pass3 verdicts=" + pass3Verdicts + "\n" +
                    '}';
        }
    }

    // ------------------------------------------------------------------------------------------
    // Endpoint core-shape model
    // ------------------------------------------------------------------------------------------

    static final class StoredSample {
        final Document docId;   // exact sample_data _id holding this string
        final String raw;
        StoredSample(Document docId, String raw) { this.docId = docId; this.raw = raw; }
    }

    static final class Endpoint {
        final String key;       // "METHOD url" (url without leading slash)
        final String method;
        final String url;       // as stored (first seen)
        final List<StoredSample> samples = new ArrayList<>();     // unique sample strings
        final List<StoredSample> duplicates = new ArrayList<>();  // same string in another duplicate doc
        final PayloadPathIndex respIndex = new PayloadPathIndex();
        final List<Set<String>> respPaths = new ArrayList<>();
        final List<Set<String>> respHeaders = new ArrayList<>();
        final Set<String> allPaths = new HashSet<>();
        final Set<String> allLeaves = new HashSet<>();
        final Set<String> corePaths = new HashSet<>();
        final Set<String> coreHeaders = new HashSet<>();
        final Set<String> allHeaders = new HashSet<>();
        Set<String> medoidPaths = Collections.emptySet();
        Boolean reliableCore = null; // lazily computed, see isReliableCore
        final List<Integer> core = new ArrayList<>();
        int medoid = -1;

        Endpoint(String key, String method, String url) { this.key = key; this.method = method; this.url = url; }

        int n() { return samples.size(); }
        boolean coreIsMajority() { return core.size() * 2 > samples.size(); }
    }

    static final class CollectionModel {
        final int apiCollectionId;
        final Map<String, Endpoint> endpoints = new LinkedHashMap<>();
        /** url -> lowercase params that have a sensitive doc (covers APIs absent from sample_data). */
        final Map<String, Set<String>> sensitiveParamsByUrl = new HashMap<>();
        CollectionModel(int id) { this.apiCollectionId = id; }
    }

    static String endpointKey(String method, String url) {
        if (url == null) url = "";
        while (url.startsWith("/")) url = url.substring(1);
        return (method == null ? "" : method.toUpperCase(Locale.ROOT)) + " " + url;
    }

    static CollectionModel loadCollection(MongoCollection<Document> sampleData, int apiCollectionId) {
        CollectionModel model = new CollectionModel(apiCollectionId);
        Map<String, Set<String>> seen = new HashMap<>();
        try (MongoCursor<Document> cursor = sampleData.find(Filters.eq("_id.apiCollectionId", apiCollectionId)).cursor()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                Document id = doc.get("_id", Document.class);
                if (id == null) continue;
                String method = id.getString("method");
                String url = id.getString("url");
                if (method == null || url == null) continue;
                String key = endpointKey(method, url);
                Endpoint ep = model.endpoints.computeIfAbsent(key, k -> new Endpoint(k, method, url));
                Set<String> dedupe = seen.computeIfAbsent(key, k -> new HashSet<>());
                @SuppressWarnings("unchecked")
                List<String> samples = (List<String>) doc.get("samples");
                if (samples == null) continue;
                for (String s : samples) {
                    // Same sample string may live in several duplicate docs; it is modelled once
                    // (v2 bug: fetchAllSampleDataForApi double-counted the first doc) but every
                    // copy is remembered so Pass 2 can pull it from each exact doc.
                    if (s == null) continue;
                    if (dedupe.add(s)) ep.samples.add(new StoredSample(id, s));
                    else ep.duplicates.add(new StoredSample(id, s));
                }
            }
        }
        for (Endpoint ep : model.endpoints.values()) buildEndpoint(ep);
        return model;
    }

    private static void buildEndpoint(Endpoint ep) {
        List<String[]> parsed = new ArrayList<>(ep.samples.size());
        for (StoredSample s : ep.samples) {
            String[] p = parseSample(s.raw);
            parsed.add(p);
            ep.respIndex.observe(p == null ? null : p[1]);
        }
        ep.respIndex.finish();
        for (String[] p : parsed) {
            Set<String> paths = responsePaths(p == null ? null : p[1], ep.respIndex.dynamicParents);
            Set<String> headers = headerKeys(p == null ? null : p[2]);
            ep.respPaths.add(paths);
            ep.respHeaders.add(headers);
            ep.allPaths.addAll(paths);
            ep.allHeaders.addAll(headers);
            for (String path : paths) ep.allLeaves.add(finalPathSegment(path));
        }

        int n = ep.respPaths.size();
        if (n == 0) return;
        int limit = Math.min(n, MAX_SAMPLES_FOR_MEDOID);
        double best = -1;
        for (int i = 0; i < limit; i++) {
            double score = 0;
            for (int j = 0; j < limit; j++) score += jaccard(ep.respPaths.get(i), ep.respPaths.get(j));
            if (score > best) { best = score; ep.medoid = i; }
        }
        ep.medoidPaths = ep.respPaths.get(ep.medoid);
        for (int i = 0; i < n; i++) {
            if (jaccard(ep.respPaths.get(i), ep.medoidPaths) >= CORE_SIMILARITY) {
                ep.core.add(i);
                ep.corePaths.addAll(ep.respPaths.get(i));
                ep.coreHeaders.addAll(ep.respHeaders.get(i));
            }
        }
    }

    /** [requestPayload, responsePayload, responseHeaders] or null when the sample is not parseable. */
    static String[] parseSample(String raw) {
        if (raw == null) return null;
        try {
            BasicDBObject o = BasicDBObject.parse(raw);
            return new String[]{o.getString("requestPayload"), o.getString("responsePayload"), o.getString("responseHeaders")};
        } catch (Exception e) {
            return null;
        }
    }

    static Set<String> responsePaths(String payload, Set<String> dynamicParents) {
        Set<String> out = new HashSet<>();
        if (payload == null) return out;
        String trimmed = payload.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return out; // HTML/text: no JSON shape
        for (String path : HttpRequestResponseUtils.extractValuesFromPayload(trimmed).keySet()) {
            String normalized = normalizePath(path, dynamicParents);
            if (normalized != null && !normalized.isEmpty()) out.add(normalized);
        }
        return out;
    }

    static Set<String> headerKeys(String headers) {
        Set<String> out = new HashSet<>();
        if (headers == null) return out;
        try {
            for (String k : BasicDBObject.parse(headers).keySet()) if (k != null) out.add(k.toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Param name -> candidate normalized paths (top-level arrays are wrapped as "json" by the flattener). */
    static Set<String> paramPaths(String param, Set<String> dynamicParents) {
        Set<String> out = new HashSet<>();
        String p = stripQueryParamSuffix(param);
        if (p == null || p.isEmpty()) return out;
        out.add(normalizePath(p, dynamicParents));
        if (p.startsWith("$")) out.add(normalizePath("json#" + p, dynamicParents));
        if (p.startsWith("json#")) out.add(normalizePath(p.substring(5), dynamicParents));
        return out;
    }

    /** Exact path, or the param is an intermediate node of some path (e.g. "data" for "data#token"). */
    static boolean containsAny(Set<String> haystack, Set<String> needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        for (String h : haystack) {
            for (String n : needles) if (h.length() > n.length() && h.startsWith(n) && h.charAt(n.length()) == '#') return true;
        }
        return false;
    }


    // ------------------------------------------------------------------------------------------
    // Same-API family: a swap is between two *different* APIs. Template/slash/case/method variants
    // of the same path are the same API and never count as a donor.
    // ------------------------------------------------------------------------------------------

    static boolean sameApiFamily(String urlA, String urlB) {
        String[] a = segments(urlA), b = segments(urlB);
        if (a.length != b.length) return isPrefixedMount(a, b) || isPrefixedMount(b, a);
        int codeDiffs = 0;
        for (int i = 0; i < a.length; i++) {
            if (sameSegment(a[i], b[i])) continue;
            if (isUrlTemplateToken(a[i]) || isUrlTemplateToken(b[i])) continue;
            // un-templatised identifiers (files/INTEGER/AB12/ITEM/XYZ vs .../CD34/ITEM/PQR)
            if (isCodeLike(a[i]) && isCodeLike(b[i])) { codeDiffs++; continue; }
            return false;
        }
        return codeDiffs == 0 || (a.length >= 3 && codeDiffs <= Math.max(1, a.length / 3));
    }

    private static final java.util.regex.Pattern REGION_SUFFIX =
            java.util.regex.Pattern.compile("^([a-z][a-z0-9_-]{3,}?)(us|uk|ca|de|es|ie|br|fi|ae|bh|om|in|au|mx)$");

    /** Equal ignoring case and a trailing region code (orderserviceus == orderserviceuk). */
    static boolean sameSegment(String a, String b) {
        if (a.equalsIgnoreCase(b)) return true;
        String x = a.toLowerCase(Locale.ROOT), y = b.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher mx = REGION_SUFFIX.matcher(x), my = REGION_SUFFIX.matcher(y);
        return mx.matches() && my.matches() && mx.group(1).equals(my.group(1));
    }

    /** Short identifier-ish segment: <= 8 chars of [A-Za-z0-9%_-] with a digit, or all upper-case. */
    static boolean isCodeLike(String seg) {
        if (seg.isEmpty() || seg.length() > 8) return false;
        boolean digit = false, lower = false;
        for (int i = 0; i < seg.length(); i++) {
            char c = seg.charAt(i);
            if (Character.isDigit(c)) digit = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (!(Character.isUpperCase(c) || c == '%' || c == '_' || c == '-')) return false;
        }
        return digit || !lower;
    }

    /** {@code longer} = some prefix + {@code shorter} (>= 2 segments), e.g. /public/data/profile vs /data/profile. */
    private static boolean isPrefixedMount(String[] longer, String[] shorter) {
        if (shorter.length < 2 || longer.length <= shorter.length) return false;
        int off = longer.length - shorter.length;
        // The shorter (mounted) path must be concrete: >= 2 literal segments, no template tokens.
        // Tokens are only allowed on the longer side, where a wildcard bucket covers the concrete API.
        int literal = 0;
        for (int i = 0; i < shorter.length; i++) {
            String a = longer[off + i], b = shorter[i];
            if (sameSegment(a, b)) { if (!isUrlTemplateToken(b)) literal++; continue; }
            if (isUrlTemplateToken(a) && !isUrlTemplateToken(b)) continue; // wildcard on the longer side only
            return false;
        }
        return literal >= 2;
    }

    /** First two literal (non-token) segments equal, e.g. api/v1/... vs api/v1/... or svc/actions/... */
    static boolean sameService(String urlA, String urlB) {
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        for (String x : segments(urlA)) if (!isUrlTemplateToken(x)) a.add(x.toLowerCase(Locale.ROOT));
        for (String x : segments(urlB)) if (!isUrlTemplateToken(x)) b.add(x.toLowerCase(Locale.ROOT));
        if (a.size() >= 2 && b.size() >= 2 && sameSegment(a.get(0), b.get(0)) && sameSegment(a.get(1), b.get(1))) return true;
        // share a distinctive resource name (e.g. image-service, geo-lookup-ws, ...)
        for (String x : a) {
            if (x.length() < 8 || GENERIC_SEGMENTS.contains(x)) continue;
            for (String y : b) if (sameSegment(x, y)) return true;
        }
        return false;
    }

    private static final Set<String> GENERIC_SEGMENTS = new HashSet<>(Arrays.asList(
            "services", "internal", "register", "settings", "response", "requests", "details", "summary",
            "content", "configuration", "dashboard", "documents", "download", "metadata", "preferences",
            "notifications", "authenticate", "authorize", "management"));

    /** One URL is a path prefix of the other (/user/address vs /user/address/instructions). */
    static boolean isSubResource(String urlA, String urlB) {
        String[] a = segments(urlA), b = segments(urlB);
        String[] shorter = a.length <= b.length ? a : b, longer = shorter == a ? b : a;
        if (shorter.length == 0 || shorter.length == longer.length) return false;
        for (int i = 0; i < shorter.length; i++) {
            if (!shorter[i].equalsIgnoreCase(longer[i]) && !isUrlTemplateToken(shorter[i]) && !isUrlTemplateToken(longer[i])) return false;
        }
        return true;
    }

    private static String[] segments(String url) {
        if (url == null) return new String[0];
        int q = url.indexOf('?');
        String p = q < 0 ? url : url.substring(0, q);
        List<String> out = new ArrayList<>();
        for (String s : p.split("/")) if (!s.isEmpty()) out.add(s);
        return out.toArray(new String[0]);
    }

    static boolean hasTemplateToken(String url) {
        for (String s : segments(url)) if (isUrlTemplateToken(s)) return true;
        return false;
    }

    static final class Sibling {
        final Endpoint endpoint;
        final double similarity;
        Sibling(Endpoint endpoint, double similarity) { this.endpoint = endpoint; this.similarity = similarity; }
        @Override public String toString() {
            return endpoint == null ? "NONE" : endpoint.key + " score=" + String.format(Locale.US, "%.2f", similarity);
        }
    }

    /** Best endpoint of the collection (excluding {@code self}'s API family) whose medoid shape matches {@code rawPaths}. */
    static Sibling bestSibling(CollectionModel model, Endpoint self, String selfUrl, Set<String> rawPaths) {
        Endpoint best = null;
        double bestScore = 0;
        if (rawPaths.isEmpty()) return new Sibling(null, 0);
        for (Endpoint other : model.endpoints.values()) {
            if ((self != null && other == self) || other.medoidPaths.size() < MIN_SHAPE_PATHS) continue;
            if (other.url.isEmpty()) continue; // malformed endpoint with no URL is never a donor
            if (sameApiFamily(selfUrl, other.url)) continue;
            double score = jaccard(renormalize(rawPaths, other.respIndex.dynamicParents), other.medoidPaths);
            if (score > bestScore) { bestScore = score; best = other; }
        }
        return new Sibling(best, bestScore);
    }

    /** True when {@code selfUrl} is a template and some concrete API it covers natively has the param. */
    static boolean wildcardCovers(CollectionModel model, Endpoint self, String selfUrl, String param) {
        if (!hasTemplateToken(selfUrl)) return false;
        for (Endpoint other : model.endpoints.values()) {
            if ((self != null && other == self) || !sameApiFamily(selfUrl, other.url)) continue;
            if (!isReliableCore(model, other)) continue; // a borrowed/tiny cache cannot vouch
            if (containsAny(other.corePaths, paramPaths(param, other.respIndex.dynamicParents))) return true;
        }
        String prefix = parentPrefix(stripQueryParamSuffix(param).toLowerCase(Locale.ROOT));
        for (Map.Entry<String, Set<String>> e : model.sensitiveParamsByUrl.entrySet()) {
            if (e.getKey().equals(selfUrl) || hasTemplateToken(e.getKey()) || !sameApiFamily(selfUrl, e.getKey())) continue;
            // a concrete API under this template has a non-suspect sensitive param in the same parent object
            if (e.getValue().contains(prefix)) return true;
        }
        return false;
    }

    /** For a template URL, the lowercase path before the first token ("public/data/"); null for concrete URLs. */
    static String literalPrefix(String url) {
        if (!hasTemplateToken(url)) return null;
        StringBuilder sb = new StringBuilder();
        for (String seg : segments(url)) {
            if (isUrlTemplateToken(seg)) break;
            sb.append(seg.toLowerCase(Locale.ROOT)).append('/');
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Enough samples, a real majority, and the majority is not another API's response. */
    static boolean isReliableCore(CollectionModel model, Endpoint ep) {
        if (ep.reliableCore == null) {
            ep.reliableCore = ep.respPaths.size() >= MIN_SAMPLES_PASS1 && ep.coreIsMajority()
                    && bestSibling(model, ep, ep.url, ep.medoidPaths).similarity < BORROWED_CORE_SIBLING_SIMILARITY;
        }
        return ep.reliableCore;
    }

    /** First two '#' segments ("data#contactinfolist#email" -> "data#contactinfolist"). */
    static String parentPrefix(String param) {
        int first = param.indexOf('#');
        if (first < 0) return param;
        int second = param.indexOf('#', first + 1);
        return second < 0 ? param : param.substring(0, second);
    }

    static Set<String> renormalize(Set<String> paths, Set<String> dynamicParents) {
        if (dynamicParents.isEmpty()) return paths;
        Set<String> out = new HashSet<>();
        for (String p : paths) out.add(normalizePath(p, dynamicParents));
        return out;
    }

    // ------------------------------------------------------------------------------------------
    // Pass 1: sensitive_sample_data
    // ------------------------------------------------------------------------------------------

    static final class Pass1Decision {
        String verdict;
        String reason = "";
        int n, core, inAny;
        double ownSimilarity = -1;
        Sibling sibling = new Sibling(null, 0);
        boolean siblingHasParam;
        boolean delete() { return "DELETE".equals(verdict); }
    }

    static Pass1Decision decidePass1(CollectionModel model, Document idDoc, List<String> proofs) {
        Pass1Decision d = new Pass1Decision();
        String url = idDoc.getString("url");
        String method = idDoc.getString("method");
        String param = idDoc.getString("param");
        int responseCode = intOf(idDoc.get("responseCode"));
        boolean isHeader = Boolean.TRUE.equals(idDoc.getBoolean("isHeader"));

        if (responseCode == -1) { d.verdict = "KEEP_REQUEST_SIDE"; return d; }
        if (isUrlTemplateSegmentParam(stripQueryParamSuffix(param), url)) { d.verdict = "KEEP_URL_SEGMENT"; return d; }

        Endpoint ep = model.endpoints.get(endpointKey(method, url));
        if (ep == null || ep.respPaths.isEmpty()) {
            // Nothing to compare against: never deleted, but surfaced when the proof is clearly another API.
            Set<String> proofUnion = new HashSet<>();
            for (String raw : proofs) {
                String[] p = parseSample(raw);
                if (p != null) proofUnion.addAll(responsePaths(p[1], Collections.<String>emptySet()));
            }
            d.sibling = proofUnion.size() >= MIN_SHAPE_PATHS ? bestSibling(model, null, url, proofUnion) : new Sibling(null, 0);
            if (d.sibling.endpoint != null) d.siblingHasParam = containsAny(d.sibling.endpoint.corePaths,
                    paramPaths(param, d.sibling.endpoint.respIndex.dynamicParents));
            boolean swapLikely = d.sibling.similarity >= 0.9 && d.siblingHasParam && !wildcardCovers(model, null, url, param);
            d.verdict = swapLikely ? "REVIEW_NO_SAMPLES_SWAP_LIKELY" : "KEEP_NO_SAMPLES";
            if (swapLikely) d.reason = "no sample_data; proof is " + d.sibling;
            return d;
        }
        d.n = ep.respPaths.size();
        d.core = ep.core.size();

        Set<String> wanted = isHeader
                ? Collections.singleton(stripQueryParamSuffix(param).toLowerCase(Locale.ROOT))
                : paramPaths(param, ep.respIndex.dynamicParents);
        Set<String> coreSet = isHeader ? ep.coreHeaders : ep.corePaths;
        Set<String> allSet = isHeader ? ep.allHeaders : ep.allPaths;
        if (containsAny(coreSet, wanted)) {
            // Present in the endpoint's majority shape -- unless that "majority" is itself a borrowed
            // shape (minority core that is really another API's response).
            if (!ep.coreIsMajority() || d.n < MIN_SAMPLES_PASS1) {
                Sibling s = bestSibling(model, ep, url, ep.medoidPaths);
                if (s.similarity >= BORROWED_CORE_SIBLING_SIMILARITY) {
                    d.verdict = "REVIEW_BORROWED_CORE";
                    d.sibling = s;
                    d.reason = "core is " + d.core + "/" + d.n + " and medoid matches another API " + s;
                    return d;
                }
            }
            d.verdict = "KEEP_CONFIRMED";
            return d;
        }
        for (int i = 0; i < ep.respPaths.size(); i++) {
            if (containsAny(isHeader ? ep.respHeaders.get(i) : ep.respPaths.get(i), wanted)) d.inAny++;
        }

        // Proof shape: union of the stored proofs' response paths, own similarity = best single proof.
        Set<String> proofUnion = new HashSet<>();
        double own = 0;
        boolean anyProof = false;
        for (String raw : proofs) {
            String[] p = parseSample(raw);
            if (p == null) continue;
            anyProof = true;
            Set<String> paths = responsePaths(p[1], ep.respIndex.dynamicParents);
            proofUnion.addAll(paths);
            own = Math.max(own, jaccard(paths, ep.medoidPaths));
        }
        d.ownSimilarity = anyProof ? own : -1;
        d.sibling = bestSibling(model, ep, url, proofUnion);
        if (d.sibling.endpoint != null) {
            d.siblingHasParam = containsAny(d.sibling.endpoint.corePaths,
                    paramPaths(param, d.sibling.endpoint.respIndex.dynamicParents));
        }

        if (!anyProof) { d.verdict = "REVIEW_NO_PROOF"; return d; }
        if (wildcardCovers(model, ep, url, param)) { d.verdict = "KEEP_WILDCARD_COVERS"; return d; }
        if (proofUnion.size() < MIN_SHAPE_PATHS) {
            d.verdict = "REVIEW_TINY_SHAPE";
            d.reason = "proof has " + proofUnion.size() + " path(s); shape comparison not meaningful";
            return d;
        }
        boolean strongSibling = d.sibling.similarity >= PASS1_STRONG_SIBLING_SIMILARITY;
        boolean weakSiblingWithEvidence = d.sibling.similarity >= PASS1_MIN_SIBLING_SIMILARITY
                && own < PASS1_WEAK_BAND_MAX_OWN_SIMILARITY && d.siblingHasParam;
        if (d.n >= MIN_SAMPLES_PASS1 && own < PASS1_MAX_OWN_SIMILARITY && (strongSibling || weakSiblingWithEvidence)
                && sameService(url, d.sibling.endpoint.url)
                && !(own < SAME_SERVICE_MAX_OWN_SIMILARITY && d.sibling.similarity >= SAME_SERVICE_MIN_SIBLING_SIMILARITY)) {
            d.verdict = "REVIEW_SAME_SERVICE";
            d.reason = "donor is a sibling API of the same service; proof J=" + fmt(own) + " " + d.sibling;
            return d;
        }
        if (d.n >= MIN_SAMPLES_PASS1 && own < PASS1_MAX_OWN_SIMILARITY && (strongSibling || weakSiblingWithEvidence)) {
            d.verdict = "DELETE";
            d.reason = "param absent from core; proof J=" + fmt(own) + " vs endpoint, " + d.sibling;
            return d;
        }
        if (own >= 0.6) { d.verdict = "KEEP_SHAPE_MATCHES"; d.reason = "proof matches endpoint; optional field"; return d; }
        d.verdict = d.inAny > 0 ? "REVIEW_OUTLIER_ONLY" : "REVIEW_PARAM_ABSENT";
        d.reason = "proof J=" + fmt(own) + " sibling " + d.sibling + " n=" + d.n;
        return d;
    }

    // ------------------------------------------------------------------------------------------
    // Pass 2: sample_data outliers
    // ------------------------------------------------------------------------------------------

    static final class Pass2Flag {
        final Endpoint endpoint;
        final StoredSample sample;
        final double ownSimilarity;
        final Sibling donor;
        Pass2Flag(Endpoint e, StoredSample s, double own, Sibling donor) {
            this.endpoint = e; this.sample = s; this.ownSimilarity = own; this.donor = donor;
        }
    }

    static List<Pass2Flag> decidePass2(CollectionModel model, Endpoint ep) {
        List<Pass2Flag> flags = new ArrayList<>();
        if (ep.respPaths.size() < MIN_SAMPLES_PASS2 || !ep.coreIsMajority()) return flags;
        for (int i = 0; i < ep.respPaths.size(); i++) {
            Set<String> paths = ep.respPaths.get(i);
            if (paths.size() < MIN_SHAPE_PATHS) continue; // non-JSON or envelope-sized: no evidence of another API
            double own = jaccard(paths, ep.medoidPaths);
            if (own >= PASS2_OUTLIER_SIMILARITY) continue;
            Sibling donor = bestSibling(model, ep, ep.url, paths);
            if (donor.similarity < PASS2_MIN_DONOR_SIMILARITY) continue;
            if (isSubResource(ep.url, donor.endpoint.url)) continue; // parent/child resource of the same API
            if (sameService(ep.url, donor.endpoint.url)) continue; // sibling APIs of one service share shapes
            flags.add(new Pass2Flag(ep, ep.samples.get(i), own, donor));
        }
        return flags;
    }

    // ------------------------------------------------------------------------------------------
    // Pass 3: orphan single_type_info (sensitive subtype, no sensitive_sample_data doc)
    // ------------------------------------------------------------------------------------------

    static String decidePass3(CollectionModel model, Document sti, StringBuilder why) {
        String url = sti.getString("url");
        String method = sti.getString("method");
        String param = sti.getString("param");
        if (intOf(sti.get("responseCode")) == -1 || Boolean.TRUE.equals(sti.getBoolean("isHeader"))) return "KEEP_REQUEST_OR_HEADER";
        if (isUrlTemplateSegmentParam(stripQueryParamSuffix(param), url)) return "KEEP_URL_SEGMENT";
        Endpoint ep = model.endpoints.get(endpointKey(method, url));
        if (ep == null || ep.respPaths.size() < MIN_SAMPLES_PASS3) return "KEEP_FEW_SAMPLES";
        if (!ep.coreIsMajority()) {
            why.append("medoidPaths=").append(ep.medoidPaths.size()).append(" core=").append(ep.core.size()).append("/").append(ep.respPaths.size());
            return "KEEP_WEAK_REFERENCE_SHAPE";
        }
        Set<String> wanted = paramPaths(param, ep.respIndex.dynamicParents);
        if (containsAny(ep.allPaths, wanted)) return "KEEP_PRESENT";
        String leaf = finalPathSegment(stripQueryParamSuffix(param).toLowerCase(Locale.ROOT));
        if (ep.allLeaves.contains(leaf)) return "KEEP_LEAF_PRESENT";
        // An ancestor container (depth >= 2) is present as a leaf = empty/null on the sampled calls,
        // e.g. data#items = [] -> its items' fields are native when it is populated.
        for (String w : wanted) {
            for (int i = w.indexOf('#'), j; i >= 0 && (j = w.indexOf('#', i + 1)) >= 0; i = j) {
                if (ep.allPaths.contains(w.substring(0, j))) return "KEEP_EMPTY_PARENT_PRESENT";
            }
        }
        if (wildcardCovers(model, ep, url, param)) return "KEEP_WILDCARD_COVERS";
        String literalPrefix = literalPrefix(url);
        for (Endpoint other : model.endpoints.values()) {
            if (other == ep || sameApiFamily(url, other.url)) continue;
            // a minority/tiny cache (e.g. a wildcard endpoint with 2/10 borrowed samples) is not a native home;
            // the "borrowed" test is NOT applied: blobs like userConfig are legitimately shared by several APIs
            if (other.respPaths.size() < MIN_SAMPLES_PASS1 || !other.coreIsMajority()) continue;
            // a wildcard (/api/data/STRING) may legitimately cover deeper APIs under its literal prefix
            if (literalPrefix != null && endpointKey("", other.url).trim().toLowerCase(Locale.ROOT).startsWith(literalPrefix)) continue;
            if (containsAny(other.corePaths, paramPaths(param, other.respIndex.dynamicParents))) {
                why.append("native to ").append(other.key).append("; absent from ").append(ep.respPaths.size()).append(" samples");
                return "DELETE";
            }
        }
        return "KEEP_NO_NATIVE_HOME";
    }

    // ------------------------------------------------------------------------------------------
    // Driver
    // ------------------------------------------------------------------------------------------

    public static Report run(boolean dryRun) {
        return run(dryRun, null, null);
    }

    public static Report run(boolean dryRun, String reportDir, Set<Integer> onlyCollections) {
        return run(dryRun, reportDir, onlyCollections, 0, "manual");
    }

    // ------------------------------------------------------------------------------------------
    // Logging. Every line starts with "[mismatch-cleanup] run=<id>" so one grep gives a run's audit trail.
    //   RUN_START / RUN_END          once per account run (also stored in logs_dashboard)
    //   COLL                         per-collection summary (stored in logs_dashboard when it has findings)
    //   P1 PLAN / P1 APPLIED / P1 GONE   every sensitive doc the job deletes (or would delete)
    //   P1 REVIEW                    every sensitive doc kept but flagged for a human
    //   P2 PLAN / P2 APPLIED         every sample_data sample pulled (or would be pulled)
    //   P3 PLAN / P3 APPLIED         every orphan single_type_info row deleted (or would be deleted)
    //   COLL_SKIPPED_CAP / COLL_FAILED   collection not applied (delete cap exceeded / error)
    // ------------------------------------------------------------------------------------------

    private static void log(String runId, String msg) {
        logger.info("[mismatch-cleanup] run=" + runId + " " + msg);
    }

    private static void logDb(String runId, String msg) {
        logger.infoAndAddToDb("[mismatch-cleanup] run=" + runId + " " + msg, LogDb.DASHBOARD);
    }

    private static String q(Object o) {
        return "\"" + String.valueOf(o).replace("\"", "'").replace('\n', ' ') + "\"";
    }

    /** Everything decided for one collection before anything is written. */
    private static final class CollectionPlan {
        final List<Document> pass1Deletes = new ArrayList<>();
        final List<Pass2Flag> pass2Flags = new ArrayList<>();
        final Map<Endpoint, List<Pass2Flag>> pass2ByEndpoint = new LinkedHashMap<>();
        final List<Document> pass3Deletes = new ArrayList<>();
        int pass1Reviews = 0;
        int total() { return pass1Deletes.size() + pass2Flags.size() + pass3Deletes.size(); }
    }

    /**
     * @param reportDir                where the review TSVs go (null = no files)
     * @param onlyCollections          restrict to these apiCollectionIds (null/empty = whole account)
     * @param maxDeletesPerCollection  delete mode only: a collection whose planned deletes (pass 1+2+3)
     *                                 exceed this is logged and left untouched (0 = no cap)
     * @param runId                    tag put on every log line and backup row of this run
     */
    public static Report run(boolean dryRun, String reportDir, Set<Integer> onlyCollections,
                             int maxDeletesPerCollection, String runId) {
        Report report = new Report();
        long started = System.currentTimeMillis();
        MongoDatabase db = MCollection.clients[0].getDatabase(SensitiveSampleDataDao.instance.getDBName());
        // Writes (and the read-back of a doc right before it is backed up and deleted) go to the primary.
        MongoCollection<Document> sens = db.getCollection(SensitiveSampleDataDao.instance.getCollName())
                .withReadPreference(ReadPreference.primary());
        MongoCollection<Document> sampleData = db.getCollection(SampleDataDao.instance.getCollName())
                .withReadPreference(ReadPreference.primary());
        MongoCollection<Document> sti = db.getCollection(SingleTypeInfoDao.instance.getCollName())
                .withReadPreference(ReadPreference.primary());
        // The heavy scans that build the model read from a secondary when one is reachable.
        MongoCollection<Document> sensRead = sens.withReadPreference(ReadPreference.secondaryPreferred());
        MongoCollection<Document> sampleDataRead = sampleData.withReadPreference(ReadPreference.secondaryPreferred());
        MongoCollection<Document> stiRead = sti.withReadPreference(ReadPreference.secondaryPreferred());

        Map<Integer, String> hosts = new HashMap<>();
        try (MongoCursor<Document> c = db.getCollection("api_collections").withReadPreference(ReadPreference.secondaryPreferred())
                .find().projection(Projections.include("hostName", "name")).cursor()) {
            while (c.hasNext()) {
                Document d = c.next();
                String h = d.getString("hostName") != null ? d.getString("hostName") : d.getString("name");
                hosts.put(intOf(d.get("_id")), h == null ? "" : h);
            }
        }

        String mode = dryRun ? "DRY_RUN" : "DELETE";
        String verb = dryRun ? "WOULD_DELETE" : "DELETE";
        logDb(runId, "RUN_START account=" + Context.accountId.get() + " mode=" + mode +
                " onlyCollections=" + (onlyCollections == null || onlyCollections.isEmpty() ? "ALL" : onlyCollections) +
                " maxDeletesPerCollection=" + (maxDeletesPerCollection <= 0 ? "none" : maxDeletesPerCollection) +
                " reportDir=" + reportDir + " backups=*" + BACKUP_SUFFIX);

        Reports out = new Reports(reportDir);
        try {
            // 1) All sensitive _ids grouped by collection (ids only; ~100 bytes each).
            Map<Integer, List<Document>> sensByCollection = new HashMap<>();
            Set<String> sensKeys = new HashSet<>();
            Set<String> sensitiveSubTypes = new TreeSet<>();
            try (MongoCursor<Document> c = sensRead.find().projection(Projections.include("_id")).cursor()) {
                while (c.hasNext()) {
                    Document id = c.next().get("_id", Document.class);
                    if (id == null) continue;
                    int coll = intOf(id.get("apiCollectionId"));
                    sensByCollection.computeIfAbsent(coll, k -> new ArrayList<>()).add(id);
                    sensKeys.add(stiKey(coll, id.getString("method"), id.getString("url"), intOf(id.get("responseCode")),
                            Boolean.TRUE.equals(id.getBoolean("isHeader")), id.getString("param"), id.getString("subType")));
                    if (id.getString("subType") != null) sensitiveSubTypes.add(id.getString("subType"));
                }
            }
            Set<Integer> collections = new TreeSet<>(sensByCollection.keySet());
            for (Object id : sampleDataRead.distinct("_id.apiCollectionId", Integer.class)) collections.add((Integer) id);
            if (onlyCollections != null && !onlyCollections.isEmpty()) collections.retainAll(onlyCollections);
            log(runId, "LOADED sensitiveDocs=" + sensKeys.size() + " collections=" + collections.size() +
                    " sensitiveSubTypes=" + sensitiveSubTypes);

            for (int coll : collections) {
                report.collectionsScanned++;
                long collStarted = System.currentTimeMillis();
                String host = hosts.getOrDefault(coll, "");
                try {
                    processCollection(coll, host, dryRun, verb, maxDeletesPerCollection, runId, db, sens, sti, sampleData,
                            sensRead, sampleDataRead, stiRead, sensByCollection.getOrDefault(coll, Collections.emptyList()),
                            sensKeys, sensitiveSubTypes, out, report, collStarted);
                } catch (Exception e) {
                    report.collectionsFailed++;
                    logger.errorAndAddToDb(e, "[mismatch-cleanup] run=" + runId + " COLL_FAILED coll=" + coll + " host=" + host +
                            " error=" + e, LogDb.DASHBOARD);
                }
                if (report.collectionsScanned % 25 == 0) {
                    log(runId, "PROGRESS collections=" + report.collectionsScanned + "/" + collections.size() +
                            " p1Delete=" + report.pass1Verdicts.getOrDefault("DELETE", 0) +
                            " p2Delete=" + report.sampleDataSamplesFlagged + " p3Delete=" + report.orphanStiFlagged +
                            " elapsedSec=" + (System.currentTimeMillis() - started) / 1000);
                }
            }
        } finally {
            out.close();
        }

        report.durationSec = (System.currentTimeMillis() - started) / 1000;
        logDb(runId, "RUN_END account=" + Context.accountId.get() + " mode=" + mode + " durationSec=" + report.durationSec +
                " " + report.toString().replace('\n', ' '));
        return report;
    }

    private static void processCollection(int coll, String host, boolean dryRun, String verb, int maxDeletesPerCollection,
                                          String runId, MongoDatabase db,
                                          MongoCollection<Document> sens, MongoCollection<Document> sti,
                                          MongoCollection<Document> sampleData,
                                          MongoCollection<Document> sensRead, MongoCollection<Document> sampleDataRead,
                                          MongoCollection<Document> stiRead,
                                          List<Document> collSens, Set<String> sensKeys, Set<String> sensitiveSubTypes,
                                          Reports out, Report report, long collStarted) {
                String at = "coll=" + coll + " host=" + host;
                CollectionModel model = loadCollection(sampleDataRead, coll);
                CollectionPlan plan = new CollectionPlan();
                // Pass 1, two phases. Phase A decides on sample_data evidence only and marks suspect
                // (url, param) pairs; phase B lets only NON-suspect sensitive docs on concrete URLs vouch
                // for a wildcard URL (otherwise swapped docs would vouch for each other).
                Map<Document, List<String>> proofCache = new HashMap<>();
                Set<String> suspect = new HashSet<>();
                for (Document idDoc : collSens) {
                    if (quickVerdict(model, idDoc) != null) continue;
                    List<String> proofs = fetchProofs(sensRead, idDoc);
                    proofCache.put(idDoc, proofs);
                    String v = decidePass1(model, idDoc, proofs).verdict;
                    if (v.equals("DELETE") || v.contains("SWAP_LIKELY") || v.equals("REVIEW_BORROWED_CORE")) {
                        suspect.add(idDoc.getString("url") + "\u0000" + idDoc.getString("param"));
                    }
                }
                for (Document idDoc : collSens) {
                    if (intOf(idDoc.get("responseCode")) == -1) continue;
                    if (suspect.contains(idDoc.getString("url") + "\u0000" + idDoc.getString("param"))) continue;
                    model.sensitiveParamsByUrl.computeIfAbsent(idDoc.getString("url"), k -> new HashSet<>())
                            .add(parentPrefix(stripQueryParamSuffix(idDoc.getString("param")).toLowerCase(Locale.ROOT)));
                }

                for (Document idDoc : collSens) {
                    report.sensitiveDocsScanned++;
                    String quick = quickVerdict(model, idDoc);
                    Pass1Decision d;
                    if (quick != null) {
                        d = new Pass1Decision();
                        d.verdict = quick;
                    } else {
                        d = decidePass1(model, idDoc, proofCache.get(idDoc));
                    }
                    report.pass1Verdicts.merge(d.verdict, 1, Integer::sum);
                    if (!d.verdict.startsWith("KEEP_REQUEST") && !d.verdict.equals("KEEP_CONFIRMED")) out.pass1(coll, idDoc, d);
                    if (d.delete() || d.verdict.startsWith("REVIEW")) {
                        boolean del = d.delete();
                        if (del) {
                            plan.pass1Deletes.add(idDoc);
                            report.pass1DeleteBySubType.merge(idDoc.getString("subType"), 1, Integer::sum);
                        } else {
                            plan.pass1Reviews++;
                        }
                        log(runId, "P1 " + (del ? "PLAN " + verb : d.verdict) + " " + at + " " + idDoc.getString("method") +
                                " " + idDoc.getString("url") + " param=" + idDoc.getString("param") +
                                " subType=" + q(idDoc.getString("subType")) + " rc=" + intOf(idDoc.get("responseCode")) +
                                " n=" + d.n + " core=" + d.core + " ownJ=" + (d.ownSimilarity < 0 ? "-" : fmt(d.ownSimilarity)) +
                                " donor=" + q(d.sibling) + " donorHasParam=" + d.siblingHasParam + " reason=" + q(d.reason) +
                                " id=" + idDoc.toJson());
                    }
                }

                // Pass 2
                for (Endpoint ep : model.endpoints.values()) {
                    report.sampleDataEndpointsScanned++;
                    report.sampleDataSamplesScanned += ep.samples.size();
                    if (ep.samples.size() < MIN_SAMPLES_PASS2 || !ep.coreIsMajority()) { report.sampleDataEndpointsSkipped++; continue; }
                    List<Pass2Flag> flags = decidePass2(model, ep);
                    report.sampleDataSamplesFlagged += flags.size();
                    for (Pass2Flag f : flags) {
                        out.pass2(coll, f);
                        log(runId, "P2 PLAN " + verb + " " + at + " " + ep.method + " " + ep.url + " n=" + ep.n() +
                                " core=" + ep.core.size() + " ownJ=" + fmt(f.ownSimilarity) + " donor=" + q(f.donor) +
                                " sampleSha1=" + sha1(f.sample.raw) + " bytes=" + f.sample.raw.length() +
                                " sampleDataId=" + f.sample.docId.toJson());
                    }
                    if (!flags.isEmpty()) {
                        plan.pass2Flags.addAll(flags);
                        plan.pass2ByEndpoint.put(ep, flags);
                    }
                }

                // Pass 3
                Bson stiFilter = Filters.and(Filters.eq("apiCollectionId", coll),
                        Filters.in("subType", new ArrayList<>(sensitiveSubTypes)),
                        Filters.ne("responseCode", -1), Filters.eq("isHeader", false));
                try (MongoCursor<Document> c = stiRead.find(stiFilter).projection(Projections.exclude("values")).cursor()) {
                    while (c.hasNext()) {
                        Document t = c.next();
                        String key = stiKey(coll, t.getString("method"), t.getString("url"), intOf(t.get("responseCode")),
                                Boolean.TRUE.equals(t.getBoolean("isHeader")), t.getString("param"), t.getString("subType"));
                        String stripped = stiKey(coll, t.getString("method"), t.getString("url"), intOf(t.get("responseCode")),
                                Boolean.TRUE.equals(t.getBoolean("isHeader")), stripQueryParamSuffix(t.getString("param")), t.getString("subType"));
                        if (sensKeys.contains(key) || sensKeys.contains(stripped)) continue;
                        report.orphanStiScanned++;
                        StringBuilder why = new StringBuilder();
                        String verdict = decidePass3(model, t, why);
                        report.pass3Verdicts.merge(verdict, 1, Integer::sum);
                        out.pass3(coll, t, verdict, why.toString());
                        if ("DELETE".equals(verdict)) {
                            report.orphanStiFlagged++;
                            report.pass3DeleteBySubType.merge(t.getString("subType"), 1, Integer::sum);
                            plan.pass3Deletes.add(t);
                            log(runId, "P3 PLAN " + verb + " " + at + " " + t.getString("method") + " " + t.getString("url") +
                                    " param=" + t.getString("param") + " subType=" + q(t.getString("subType")) +
                                    " rc=" + intOf(t.get("responseCode")) + " reason=" + q(why) + " stiId=" + t.get("_id"));
                        }
                    }
                }

                // Apply (delete mode only), after the whole collection has been decided.
                String applied = "";
                if (!dryRun && plan.total() > 0) {
                    if (maxDeletesPerCollection > 0 && plan.total() > maxDeletesPerCollection) {
                        report.collectionsSkippedByCap++;
                        logDb(runId, "COLL_SKIPPED_CAP " + at + " planned=" + plan.total() + " cap=" + maxDeletesPerCollection +
                                " (p1=" + plan.pass1Deletes.size() + " p2=" + plan.pass2Flags.size() + " p3=" + plan.pass3Deletes.size() +
                                ") nothing deleted in this collection");
                        applied = " applied=SKIPPED_CAP";
                    } else {
                        int s0 = report.sensitiveDocsDeleted, t0 = report.singleTypeInfoDocsDeleted,
                                p0 = report.sampleDataSamplesDeleted, o0 = report.orphanStiDeleted;
                        if (!plan.pass1Deletes.isEmpty()) applyPass1(db, sens, sti, plan.pass1Deletes, report, runId, at);
                        for (Map.Entry<Endpoint, List<Pass2Flag>> e : plan.pass2ByEndpoint.entrySet()) {
                            report.sampleDataSamplesDeleted += applyPass2(db, sampleData, e.getKey(), e.getValue(), runId, at);
                        }
                        if (!plan.pass3Deletes.isEmpty()) report.orphanStiDeleted += applyPass3(db, sti, plan.pass3Deletes, runId, at);
                        applied = " applied: sensitive=" + (report.sensitiveDocsDeleted - s0) +
                                " pairedSti=" + (report.singleTypeInfoDocsDeleted - t0) +
                                " samples=" + (report.sampleDataSamplesDeleted - p0) +
                                " orphanSti=" + (report.orphanStiDeleted - o0);
                    }
                }

                String summary = "COLL " + at + " endpoints=" + model.endpoints.size() + " sensitiveDocs=" + collSens.size() +
                        " p1Delete=" + plan.pass1Deletes.size() + " p1Review=" + plan.pass1Reviews +
                        " p2Delete=" + plan.pass2Flags.size() + " p3Delete=" + plan.pass3Deletes.size() + applied +
                        " tookMs=" + (System.currentTimeMillis() - collStarted);
                if (plan.total() > 0 || plan.pass1Reviews > 0) logDb(runId, summary); else log(runId, summary);
    }

    /** Verdicts that need neither the proof nor the shape model (saves a point read per doc). */
    private static String quickVerdict(CollectionModel model, Document idDoc) {
        if (intOf(idDoc.get("responseCode")) == -1) return "KEEP_REQUEST_SIDE";
        String url = idDoc.getString("url");
        if (isUrlTemplateSegmentParam(stripQueryParamSuffix(idDoc.getString("param")), url)) return "KEEP_URL_SEGMENT";
        Endpoint ep = model.endpoints.get(endpointKey(idDoc.getString("method"), url));
        if (ep == null || ep.respPaths.isEmpty()) return null; // decidePass1 checks the proof for reporting
        boolean isHeader = Boolean.TRUE.equals(idDoc.getBoolean("isHeader"));
        Set<String> wanted = isHeader
                ? Collections.singleton(stripQueryParamSuffix(idDoc.getString("param")).toLowerCase(Locale.ROOT))
                : paramPaths(idDoc.getString("param"), ep.respIndex.dynamicParents);
        if (containsAny(isHeader ? ep.coreHeaders : ep.corePaths, wanted) && ep.coreIsMajority()
                && ep.respPaths.size() >= MIN_SAMPLES_PASS1) return "KEEP_CONFIRMED";
        return null;
    }

    private static List<String> fetchProofs(MongoCollection<Document> sens, Document idDoc) {
        Document doc = sens.find(Filters.eq("_id", idDoc))
                .projection(Projections.slice("sampleData", PROOF_SAMPLES)).first();
        if (doc == null) return Collections.emptyList();
        @SuppressWarnings("unchecked")
        List<String> s = (List<String>) doc.get("sampleData");
        return s == null ? Collections.<String>emptyList() : s;
    }

    // ------------------------------------------------------------------------------------------
    // Apply (dryRun=false only). Every delete is backed up first.
    // ------------------------------------------------------------------------------------------

    private static void applyPass1(MongoDatabase db, MongoCollection<Document> sens, MongoCollection<Document> sti,
                                   List<Document> ids, Report report, String runId, String at) {
        MongoCollection<Document> sensBackup = db.getCollection(SensitiveSampleDataDao.instance.getCollName() + BACKUP_SUFFIX);
        MongoCollection<Document> stiBackup = db.getCollection(SingleTypeInfoDao.instance.getCollName() + BACKUP_SUFFIX);
        for (Document id : ids) {
            if (intOf(id.get("responseCode")) == -1) continue; // never request-side
            Document full = sens.find(Filters.eq("_id", id)).first();
            long sensDeleted = 0;
            if (full != null) {
                backup(sensBackup, Collections.singletonList(full));
                sensDeleted = sens.deleteOne(Filters.eq("_id", full.get("_id"))).getDeletedCount();
                report.sensitiveDocsDeleted += sensDeleted;
            }

            Bson stiFilter = stiFilterFromRawId(id);
            List<Document> stiDocs = sti.find(stiFilter).into(new ArrayList<>());
            long stiDeleted = 0;
            List<Object> stiIds = new ArrayList<>();
            if (!stiDocs.isEmpty()) {
                backup(stiBackup, stiDocs);
                for (Document t : stiDocs) stiIds.add(t.get("_id"));
                stiDeleted = sti.deleteMany(Filters.in("_id", stiIds)).getDeletedCount();
                report.singleTypeInfoDocsDeleted += stiDeleted;
            }
            log(runId, "P1 " + (full == null ? "GONE (already absent)" : "APPLIED") + " " + at + " sensitiveDeleted=" + sensDeleted +
                    " pairedStiDeleted=" + stiDeleted + " stiIds=" + stiIds + " id=" + id.toJson());
        }
    }

    /**
     * STI rows for a sensitive _id, using the raw subType string (v2 resolved it through the custom
     * data-type map and fell back to GENERIC for unknown custom types -- wrong rows could match).
     */
    static Bson stiFilterFromRawId(Document id) {
        String param = id.getString("param");
        String stripped = stripQueryParamSuffix(param);
        List<String> params = stripped.equals(param) ? Collections.singletonList(param) : Arrays.asList(param, stripped);
        return Filters.and(
                Filters.eq(SingleTypeInfo._URL, id.getString("url")),
                Filters.eq(SingleTypeInfo._METHOD, id.getString("method")),
                Filters.eq(SingleTypeInfo._RESPONSE_CODE, intOf(id.get("responseCode"))),
                Filters.eq(SingleTypeInfo._IS_HEADER, Boolean.TRUE.equals(id.getBoolean("isHeader"))),
                Filters.in(SingleTypeInfo._PARAM, params),
                Filters.eq(SingleTypeInfo.SUB_TYPE, id.getString("subType")),
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, intOf(id.get("apiCollectionId"))));
    }

    private static int applyPass2(MongoDatabase db, MongoCollection<Document> sampleData, Endpoint ep, List<Pass2Flag> flags,
                                  String runId, String at) {
        MongoCollection<Document> backupColl = db.getCollection(SampleDataDao.instance.getCollName() + BACKUP_SUFFIX);
        Set<String> pull = new HashSet<>();
        for (Pass2Flag f : flags) pull.add(f.sample.raw);
        // The same string can sit in several duplicate docs of this endpoint: pull from each exact _id.
        Map<Document, List<String>> byDoc = new LinkedHashMap<>();
        for (StoredSample s : ep.samples) if (pull.contains(s.raw)) byDoc.computeIfAbsent(s.docId, k -> new ArrayList<>()).add(s.raw);
        for (StoredSample s : ep.duplicates) if (pull.contains(s.raw)) byDoc.computeIfAbsent(s.docId, k -> new ArrayList<>()).add(s.raw);
        int pulled = 0;
        for (Map.Entry<Document, List<String>> e : byDoc.entrySet()) {
            backupColl.insertOne(new Document("sampleDataId", e.getKey()).append("samples", e.getValue())
                    .append("ts", Context.now()).append("runId", runId));
            boolean modified = sampleData.updateOne(Filters.eq("_id", e.getKey()), Updates.pullAll("samples", e.getValue()))
                    .getModifiedCount() > 0;
            if (modified) pulled += e.getValue().size();
            List<String> shas = new ArrayList<>();
            for (String s : e.getValue()) shas.add(sha1(s));
            log(runId, "P2 " + (modified ? "APPLIED" : "GONE (already absent)") + " " + at + " " + ep.method + " " + ep.url +
                    " pulled=" + (modified ? e.getValue().size() : 0) + " sampleSha1=" + shas + " sampleDataId=" + e.getKey().toJson());
        }
        return pulled;
    }

    private static int applyPass3(MongoDatabase db, MongoCollection<Document> sti, List<Document> docs, String runId, String at) {
        MongoCollection<Document> stiBackup = db.getCollection(SingleTypeInfoDao.instance.getCollName() + BACKUP_SUFFIX);
        // The scan projected out `values`; back up the full rows.
        List<Object> ids = new ArrayList<>();
        for (Document d : docs) ids.add(d.get("_id"));
        Bson filter = Filters.and(Filters.in("_id", ids), Filters.ne("responseCode", -1));
        backup(stiBackup, sti.find(filter).into(new ArrayList<>()));
        int deleted = (int) sti.deleteMany(filter).getDeletedCount();
        log(runId, "P3 APPLIED " + at + " deleted=" + deleted + "/" + docs.size() + " stiIds=" + ids);
        return deleted;
    }

    private static void backup(MongoCollection<Document> backupColl, List<Document> docs) {
        if (docs.isEmpty()) return;
        try {
            backupColl.insertMany(docs, new InsertManyOptions().ordered(false));
        } catch (MongoBulkWriteException e) {
            // duplicate key = already backed up by a previous run; anything else is fatal
            e.getWriteErrors().forEach(err -> {
                if (err.getCode() != 11000) throw e;
            });
        }
    }

    // ------------------------------------------------------------------------------------------
    // Reports (TSV, one row per non-trivial decision)
    // ------------------------------------------------------------------------------------------

    private static final class Reports {
        private BufferedWriter p1, p2, p3;

        Reports(String dir) {
            if (dir == null) return;
            try {
                Path base = Paths.get(dir);
                Files.createDirectories(base);
                p1 = Files.newBufferedWriter(base.resolve("pass1_sensitive.tsv"), StandardCharsets.UTF_8);
                p2 = Files.newBufferedWriter(base.resolve("pass2_sample_data.tsv"), StandardCharsets.UTF_8);
                p3 = Files.newBufferedWriter(base.resolve("pass3_orphan_sti.tsv"), StandardCharsets.UTF_8);
                p1.write("verdict\tapiCollectionId\tmethod\turl\tparam\tsubType\tresponseCode\tisHeader\tn\tcore\tinAny\townJ\tsiblingApi\tsiblingHasParam\treason\t_id_json\n");
                p2.write("apiCollectionId\tmethod\turl\tn\tcore\townJ\tdonorApi\tsampleSha1\tsampleBytes\tsampleDataId\n");
                p3.write("verdict\tapiCollectionId\tmethod\turl\tparam\tsubType\tresponseCode\tsti_id\treason\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void pass1(int coll, Document id, Pass1Decision d) {
            write(p1, d.verdict, coll, id.getString("method"), id.getString("url"), id.getString("param"), id.getString("subType"),
                    intOf(id.get("responseCode")), id.getBoolean("isHeader"), d.n, d.core, d.inAny,
                    d.ownSimilarity < 0 ? "" : fmt(d.ownSimilarity), d.sibling, d.siblingHasParam, d.reason, id.toJson());
        }

        void pass2(int coll, Pass2Flag f) {
            write(p2, coll, f.endpoint.method, f.endpoint.url, f.endpoint.n(), f.endpoint.core.size(), fmt(f.ownSimilarity),
                    f.donor, sha1(f.sample.raw), f.sample.raw.length(), f.sample.docId.toJson());
        }

        void pass3(int coll, Document t, String verdict, String why) {
            write(p3, verdict, coll, t.getString("method"), t.getString("url"), t.getString("param"), t.getString("subType"),
                    intOf(t.get("responseCode")), String.valueOf(t.get("_id")), why);
        }

        private void write(BufferedWriter w, Object... cols) {
            if (w == null) return;
            try {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cols.length; i++) {
                    if (i > 0) sb.append('\t');
                    sb.append(String.valueOf(cols[i]).replace('\t', ' ').replace('\n', ' '));
                }
                w.write(sb.append('\n').toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void close() {
            for (BufferedWriter w : new BufferedWriter[]{p1, p2, p3}) {
                if (w == null) continue;
                try { w.close(); } catch (IOException ignored) { }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Helpers (path normalization and dynamic parents unchanged from v2)
    // ------------------------------------------------------------------------------------------

    static String stiKey(int coll, String method, String url, int rc, boolean isHeader, String param, String subType) {
        return coll + "\u0000" + method + "\u0000" + url + "\u0000" + rc + "\u0000" + isHeader + "\u0000" + param + "\u0000" + subType;
    }

    static int intOf(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    static String fmt(double d) {
        return String.format(Locale.US, "%.2f", d);
    }

    static String sha1(String s) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int inter = 0;
        Set<String> small = a.size() <= b.size() ? a : b, large = small == a ? b : a;
        for (String s : small) if (large.contains(s)) inter++;
        int union = a.size() + b.size() - inter;
        return union == 0 ? 1.0 : (double) inter / union;
    }

    static boolean isUrlTemplateSegmentParam(String param, String url) {
        if (param == null || url == null || param.isEmpty()) return false;
        for (int i = 0; i < param.length(); i++) if (!Character.isDigit(param.charAt(i))) return false;
        int index;
        try {
            index = Integer.parseInt(param);
        } catch (NumberFormatException e) {
            return false;
        }
        String[] segs = segments(url);
        return index < segs.length && isUrlTemplateToken(segs[index]);
    }

    static boolean isUrlTemplateToken(String segment) {
        try {
            SingleTypeInfo.SuperType.valueOf(segment);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String stripQueryParamSuffix(String param) {
        if (param != null && param.contains(QUERY_PARAM_SUFFIX)) return param.split(QUERY_PARAM_SUFFIX)[0];
        return param;
    }

    static String finalPathSegment(String path) {
        int delimiter = path.lastIndexOf('#');
        return delimiter < 0 ? path : path.substring(delimiter + 1);
    }

    static final class PayloadPathIndex {
        int sampleCount = 0;
        final Set<String> dynamicParents = new HashSet<>();
        final Map<String, ParentStats> parentStats = new HashMap<>();

        void observe(String payload) {
            sampleCount++;
            Map<String, Map<String, String>> sampleParentChildren = new HashMap<>();
            collectPayloadStructure(payload, sampleParentChildren);
            for (Map.Entry<String, Map<String, String>> e : sampleParentChildren.entrySet()) {
                parentStats.computeIfAbsent(e.getKey(), k -> new ParentStats()).observe(e.getValue());
            }
        }

        void finish() {
            for (Map.Entry<String, ParentStats> e : parentStats.entrySet()) {
                if (!e.getKey().isEmpty() && e.getValue().isDynamic(sampleCount)) dynamicParents.add(e.getKey());
            }
            parentStats.clear();
        }
    }

    static final class ParentStats {
        int samplesSeen = 0;
        int childObservations = 0;
        final Set<String> unionChildren = new HashSet<>();
        Set<String> intersectionChildren = null;
        final Map<String, Integer> childSetFrequencies = new HashMap<>();
        final Map<String, Integer> valueShapeFrequencies = new HashMap<>();

        void observe(Map<String, String> childShapes) {
            samplesSeen++;
            Set<String> children = childShapes.keySet();
            unionChildren.addAll(children);
            if (intersectionChildren == null) intersectionChildren = new HashSet<>(children);
            else intersectionChildren.retainAll(children);
            List<String> sorted = new ArrayList<>(children);
            Collections.sort(sorted);
            childSetFrequencies.merge(String.join(",", sorted), 1, Integer::sum);
            for (String shape : childShapes.values()) {
                childObservations++;
                valueShapeFrequencies.merge(shape, 1, Integer::sum);
            }
        }

        boolean isDynamic(int endpointSampleCount) {
            int minimumOccurrences = Math.max(MIN_SAMPLES_FOR_DYNAMIC_PARENT, (int) Math.ceil(endpointSampleCount * 0.5));
            if (samplesSeen < minimumOccurrences || unionChildren.size() < MIN_DYNAMIC_PARENT_CHILDREN) return false;
            int intersectionSize = intersectionChildren == null ? 0 : intersectionChildren.size();
            double churn = 1.0 - ((double) intersectionSize / unionChildren.size());
            double majorityChildSetRatio = (double) maxFrequency(childSetFrequencies) / samplesSeen;
            double dominantValueShapeRatio = childObservations == 0 ? 0.0
                    : (double) maxFrequency(valueShapeFrequencies) / childObservations;
            return churn >= MIN_DYNAMIC_PARENT_CHURN
                    && majorityChildSetRatio <= MAX_DYNAMIC_PARENT_MAJORITY_SHAPE_RATIO
                    && dominantValueShapeRatio >= MIN_DYNAMIC_PARENT_VALUE_SHAPE_RATIO;
        }
    }

    private static int maxFrequency(Map<String, Integer> frequencies) {
        int max = 0;
        for (int f : frequencies.values()) max = Math.max(max, f);
        return max;
    }

    private static String normalizeJsonKey(String key) {
        if (key == null) return null;
        for (int i = 0; i < key.length(); i++) if (Character.isLetter(key.charAt(i))) return key.toLowerCase(Locale.ROOT);
        return "number";
    }

    private static String childPath(String parent, String child) {
        return parent.isEmpty() ? child : parent + "#" + child;
    }

    private static void collectPayloadStructure(String payload, Map<String, Map<String, String>> parentChildren) {
        if (payload == null) return;
        String trimmed = payload.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return;
        String parseable = trimmed.startsWith("[") ? "{\"json\":" + trimmed + "}" : trimmed;
        BasicDBObject root;
        try {
            root = BasicDBObject.parse(parseable);
        } catch (Exception e) {
            return;
        }
        collectPayloadStructure(root, "", parentChildren);
    }

    private static void collectPayloadStructure(Object value, String path, Map<String, Map<String, String>> parentChildren) {
        if (value instanceof Map) {
            Map<?, ?> object = (Map<?, ?>) value;
            if (object.isEmpty()) return;
            Map<String, String> children = parentChildren.computeIfAbsent(path, k -> new HashMap<>());
            for (Object rawKeyObject : object.keySet()) {
                String rawKey = rawKeyObject == null ? null : rawKeyObject.toString();
                String key = normalizeJsonKey(rawKey);
                if (key == null) continue;
                Object child = object.get(rawKey);
                children.put(key, valueShape(child));
                collectPayloadStructure(child, childPath(path, key), parentChildren);
            }
        } else if (value instanceof List) {
            String arrayPath = childPath(path, "$");
            for (Object child : (List<?>) value) collectPayloadStructure(child, arrayPath, parentChildren);
        }
    }

    private static String valueShape(Object value) {
        if (value instanceof Map) {
            List<String> keys = new ArrayList<>();
            for (Object key : ((Map<?, ?>) value).keySet()) {
                String normalized = normalizeJsonKey(key == null ? null : key.toString());
                if (normalized != null) keys.add(normalized);
            }
            Collections.sort(keys);
            return "object:" + String.join(",", keys);
        }
        if (value instanceof List) return "array";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value == null) return "null";
        return "string";
    }

    static String normalizePath(String path, Set<String> dynamicParents) {
        if (path == null) return null;
        String[] segments = path.toLowerCase(Locale.ROOT).split("#", -1);
        String rawParent = "";
        StringBuilder normalized = new StringBuilder();
        for (String segment : segments) {
            if (normalized.length() > 0) normalized.append('#');
            normalized.append(dynamicParents.contains(rawParent) ? "*" : segment);
            rawParent = childPath(rawParent, segment);
        }
        return normalized.toString();
    }
}
