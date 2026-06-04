package com.akto.merging;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.merging.MergeAuditLogDao;
import com.akto.dto.merging.MergeAuditLog;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.filter.MergedUrlsDao;
import com.akto.dto.filter.MergedUrls;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.util.filter.DictionaryFilter;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.*;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.types.CappedSet;
import com.mongodb.client.model.*;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.regex.Pattern;

import static com.akto.dto.type.KeyTypes.patternToSubType;
import static com.akto.runtime.RuntimeUtil.isAlphanumericString;
import static com.akto.runtime.RuntimeUtil.isValidVersionToken;
import static com.akto.runtime.RuntimeUtil.isValidLocaleToken;

public class MergingLogic {

    public static final int STRING_MERGING_THRESHOLD = 10;
    private static final String AKTO_MCP_SERVER_TAG = "mcp-server";
    private static final LoggerMaker loggerMaker = new LoggerMaker(MergingLogic.class, LogDb.DB_ABS);
    private static final int OPTIMIZED_MERGING_ACCOUNT_ID = 1736798101;

    private static final boolean shouldUseOptimizedMerging = "true".equalsIgnoreCase(System.getenv("USE_OPTIMIZED_MERGING"));
    private static final int OPTIMIZED_MERGING_LIMIT = parseEnvInt("OPTIMIZED_MERGING_LIMIT", 150_000);

    private static int parseEnvInt(String key, int defaultValue) {
        String val = System.getenv(key);
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static Set<MergedUrls> mergedUrls = new HashSet<>();

    private static void loadMergedUrls() {
        try {
            mergedUrls = MergedUrlsDao.instance.getMergedUrls();
            loggerMaker.infoAndAddToDb("Loaded " + mergedUrls.size() + " demerged URLs", LogDb.DB_ABS);
        } catch (Exception e) {
            loggerMaker.warnAndAddToDb("Error loading merged URLs: " + e.getMessage(), LogDb.DB_ABS);
            mergedUrls = new HashSet<>();
        }
    }

    static boolean isDemergedUrl(URLTemplate urlTemplate, int apiCollectionId) {
        try {
            for (MergedUrls mergedUrl : mergedUrls) {
                if (mergedUrl.getApiCollectionId() == apiCollectionId &&
                    mergedUrl.getUrl().equals(urlTemplate.getTemplateString()) &&
                    mergedUrl.getMethod().equals(urlTemplate.getMethod().name())) {
                    return true;
                }
            }
        } catch (Exception e) {
            loggerMaker.warnAndAddToDb("Error checking demerged URL: " + e.getMessage(), LogDb.DB_ABS);
        }
        return false;
    }

    public static void mergeUrlsAndSave(int apiCollectionId, boolean mergeUrlsBasic, boolean allowMergingOnVersions) {
        // Load demerged URLs to prevent re-merging them
        loadMergedUrls();

        ApiMergerResult result = tryMergeURLsInCollection(apiCollectionId, mergeUrlsBasic, allowMergingOnVersions);

        String deletedStaticUrlsString = "";
        int counter = 0;
        if (result.deleteStaticUrls != null) {
            for (String dUrl: result.deleteStaticUrls) {
                if (counter >= 50) break;
                if (dUrl == null) continue;
                deletedStaticUrlsString += dUrl + ", ";
                counter++;
            }
        }
        loggerMaker.infoAndAddToDb("build deletedStaticUrlsString for collection " + apiCollectionId);
        loggerMaker.debugInfoAddToDb("deleteStaticUrls: " + deletedStaticUrlsString);

        loggerMaker.debugInfoAddToDb("merged URLs: ");
        if (result.templateToStaticURLs != null) {
            for (URLTemplate urlTemplate: result.templateToStaticURLs.keySet()) {
                String tempUrl = urlTemplate.getTemplateString() + " : ";
                counter = 0;
                if (result.templateToStaticURLs == null) continue;
                for (String url: result.templateToStaticURLs.get(urlTemplate)) {
                    if (counter >= 5) break;
                    if (url == null) continue;
                    tempUrl += url + ", ";
                    counter++;
                }

                loggerMaker.debugInfoAddToDb( tempUrl);
            }
        }


        // Optimized account: write audit logs and skip actual deletions
        if (isOptimizedMergingAccount()) {
            saveAuditLogs(result, apiCollectionId);
            return;
        }

        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSti = new ArrayList<>();
        ArrayList<WriteModel<SampleData>> bulkUpdatesForSampleData = new ArrayList<>();
        ArrayList<WriteModel<ApiInfo>> bulkUpdatesForApiInfo = new ArrayList<>();

        for (URLTemplate urlTemplate: result.templateToStaticURLs.keySet()) {
            Set<String> matchStaticURLs = result.templateToStaticURLs.get(urlTemplate);
            String newTemplateUrl = urlTemplate.getTemplateString();
            if (!newTemplateUrl.startsWith("/") && !newTemplateUrl.startsWith("http")) newTemplateUrl = "/" + newTemplateUrl;
            if (!APICatalog.isTemplateUrl(newTemplateUrl)) continue;

            boolean isFirst = true;
            for (String matchedURL: matchStaticURLs) {
                if (matchedURL == null || matchedURL.isEmpty() || !matchedURL.contains(" ")) {
                    loggerMaker.warnAndAddToDb("Invalid matchedURL: '" + matchedURL + "'");
                    continue;
                }
                String[] parts = matchedURL.split(" ", 2);
                if (parts.length < 2) {
                    loggerMaker.warnAndAddToDb("matchedURL missing space: '" + matchedURL + "'");
                    continue;
                }
                URLMethods.Method delMethod = URLMethods.Method.fromString(parts[0]);
                String delEndpoint = parts[1];
                Bson filterQ = Filters.and(
                        Filters.eq("apiCollectionId", apiCollectionId),
                        Filters.eq("method", delMethod.name()),
                        Filters.eq("url", delEndpoint)
                );

                Bson filterQSampleData = Filters.and(
                        Filters.eq("_id.apiCollectionId", apiCollectionId),
                        Filters.eq("_id.method", delMethod.name()),
                        Filters.eq("_id.url", delEndpoint)
                );

                if (isFirst) {

                    for (int i = 0; i < urlTemplate.getTypes().length; i++) {
                        SingleTypeInfo.SuperType superType = urlTemplate.getTypes()[i];
                        if (superType == null) continue;

                        SingleTypeInfo.ParamId stiId = new SingleTypeInfo.ParamId(newTemplateUrl, delMethod.name(), -1, false, i+"", SingleTypeInfo.GENERIC, apiCollectionId, true);
                        SingleTypeInfo.SubType subType = KeyTypes.findSubType(i, i+"",stiId);
                        stiId.setSubType(subType);
                        SingleTypeInfo sti = new SingleTypeInfo(
                                stiId, new HashSet<>(), new HashSet<>(), 0, Context.now(), 0, CappedSet.create(i+""),
                                SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MIN_VALUE, SingleTypeInfo.ACCEPTED_MAX_VALUE);


                        // SingleTypeInfoDao.instance.insertOne(sti);
                        bulkUpdatesForSti.add(new InsertOneModel<>(sti));
                    }

                    // SingleTypeInfoDao.instance.getMCollection().updateMany(filterQ, Updates.set("url", newTemplateUrl));

                    List<Bson> updates = new ArrayList<>();
                    updates.add(Updates.set("url", newTemplateUrl));
                    updates.add(Updates.set("timestamp", Context.now()));

                    bulkUpdatesForSti.add(new UpdateManyModel<>(filterQ, Updates.combine(updates), new UpdateOptions()));


                    SampleData sd = SampleDataDao.instance.findOne(filterQSampleData);
                    if (sd != null) {
                        sd.getId().url = newTemplateUrl;
                        // SampleDataDao.instance.insertOne(sd);
                        bulkUpdatesForSampleData.add(new InsertOneModel<>(sd));
                    }


                    ApiInfo apiInfo = ApiInfoDao.instance.findOne(filterQSampleData);
                    if (apiInfo != null) {
                        apiInfo.getId().url = newTemplateUrl;
                        // ApiInfoDao.instance.insertOne(apiInfo);
                        bulkUpdatesForApiInfo.add(new InsertOneModel<>(apiInfo));
                    }

                    isFirst = false;
                } else {
                    bulkUpdatesForSti.add(new DeleteManyModel<>(filterQ));
                    // SingleTypeInfoDao.instance.deleteAll(filterQ);

                }

                bulkUpdatesForSampleData.add(new DeleteManyModel<>(filterQSampleData));
                bulkUpdatesForApiInfo.add(new DeleteManyModel<>(filterQSampleData));
                // SampleDataDao.instance.deleteAll(filterQSampleData);
                // ApiInfoDao.instance.deleteAll(filterQSampleData);
            }
        }

        for (String deleteStaticUrl: result.deleteStaticUrls) {
            if (deleteStaticUrl == null || deleteStaticUrl.isEmpty() || !deleteStaticUrl.contains(" ")) {
                loggerMaker.warnAndAddToDb("Invalid deleteStaticUrl: '" + deleteStaticUrl + "'");
                continue;
            }
            String[] parts = deleteStaticUrl.split(" ", 2);
            if (parts.length < 2) {
                loggerMaker.warnAndAddToDb("deleteStaticUrl missing space: '" + deleteStaticUrl + "'");
                continue;
            }
            URLMethods.Method delMethod = URLMethods.Method.fromString(parts[0]);
            String delEndpoint = parts[1];
            Bson filterQ = Filters.and(
                    Filters.eq("apiCollectionId", apiCollectionId),
                    Filters.eq("method", delMethod.name()),
                    Filters.eq("url", delEndpoint)
            );

            Bson filterQSampleData = Filters.and(
                    Filters.eq("_id.apiCollectionId", apiCollectionId),
                    Filters.eq("_id.method", delMethod.name()),
                    Filters.eq("_id.url", delEndpoint)
            );

            bulkUpdatesForSti.add(new DeleteManyModel<>(filterQ));
            bulkUpdatesForSampleData.add(new DeleteManyModel<>(filterQSampleData));
            bulkUpdatesForApiInfo.add(new DeleteManyModel<>(filterQSampleData)); // FIX: Delete from api_info too
            // SingleTypeInfoDao.instance.deleteAll(filterQ);
            // SampleDataDao.instance.deleteAll(filterQSampleData);
        }

        if (bulkUpdatesForSti.size() > 0) {
            try {
                SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForSti, new BulkWriteOptions().ordered(false));
            } catch (Exception e) {
                loggerMaker.warnAndAddToDb("STI bulkWrite error: " + e.getMessage());
            }
        }

        if (bulkUpdatesForSampleData.size() > 0) {
            try {
                SampleDataDao.instance.getMCollection().bulkWrite(bulkUpdatesForSampleData, new BulkWriteOptions().ordered(false));
            } catch (Exception e) {
                loggerMaker.warnAndAddToDb("SampleData bulkWrite error: " + e.getMessage());
            }
        }

        if (bulkUpdatesForApiInfo.size() > 0) {
            try {
                ApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiInfo, new BulkWriteOptions().ordered(false));
            } catch (Exception e) {
                loggerMaker.warnAndAddToDb("ApiInfo bulkWrite error: " + e.getMessage());
            }
        }
    }

    public static ApiMergerResult tryMergeURLsInCollection(int apiCollectionId, boolean mergeUrlsBasic, boolean allowMergingOnVersions) {
        ApiCollection apiCollection = ApiCollectionsDao.instance.getMeta(apiCollectionId);

        if (apiCollection != null && !CollectionUtils.isEmpty(apiCollection.getTagsList())) {
            loggerMaker.infoAndAddToDb(
                "Found tags for API collection " + apiCollectionId + ": " + apiCollection.getTagsList().stream().map(
                    CollectionTags::getKeyName).collect(Collectors.joining(",")), LogDb.DB_ABS);
            if (apiCollection.getTagsList().stream()
                .anyMatch(t -> AKTO_MCP_SERVER_TAG.equals(t.getKeyName()))) {
                loggerMaker.infoAndAddToDb(
                    "Skipping merging for API collection " + apiCollectionId + " as it is an MCP server",
                    LogDb.DB_ABS);
                return new ApiMergerResult(new HashMap<>());
            }
        }

        boolean optimized = useOptimizedMerging(apiCollectionId);

        // Check once at collection level whether STRING merging is allowed
        boolean allowStringMerging = !mergeUrlsBasic && (!ApiCollectionsDao.shouldSkipMerging(apiCollection) || !(Context.accountId.get() == 1758525547));

        loggerMaker.infoAndAddToDb("allowStringMerging value for collection " + apiCollectionId + " is " + allowStringMerging
                + " optimized=" + optimized, LogDb.DB_ABS);

        Bson filterQ = null;
        if (optimized) {
            // Optimized path: only fetch host header STIs for URL discovery
            filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        } else if (apiCollection != null && apiCollection.getHostName() == null) {
            filterQ = Filters.eq("apiCollectionId", apiCollectionId);
        } else {
            Bson hostFilter = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
            Bson normalFilter = Filters.and(
                    Filters.eq("apiCollectionId", apiCollectionId),
                    Filters.or(Filters.eq("isHeader", false), Filters.eq("param", "host"))
            );
            filterQ = mergeUrlsBasic ? hostFilter : normalFilter;
        }

        int offset = 0;
        int limit = optimized ? OPTIMIZED_MERGING_LIMIT : (mergeUrlsBasic ? 10_000 : 50_000);

        // Load already-audited static URLs so we skip them (optimized dry-run only)
        Set<String> alreadyAudited = optimized ? loadAuditedUrls(apiCollectionId) : Collections.emptySet();

        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
        ApiMergerResult finalResult = new ApiMergerResult(new HashMap<>());
        do {
            singleTypeInfos = SingleTypeInfoDao.instance.findAll(filterQ, offset, limit, null, Projections.exclude("values"));
            loggerMaker.infoAndAddToDb("sti count " + singleTypeInfos.size() + " for collection " + apiCollectionId, LogDb.DB_ABS);
            System.out.println(singleTypeInfos.size());

            Map<String, Set<String>> staticUrlToSti = new HashMap<>();
            Set<String> templateUrlSet = new HashSet<>();
            List<String> templateUrls = new ArrayList<>();
            for(SingleTypeInfo sti: singleTypeInfos) {
                if (sti.getMethod() == null || sti.getUrl() == null || sti.getUrl().isEmpty()) {
                    loggerMaker.warnAndAddToDb("STI with null/empty method or url: method=" + sti.getMethod() + " url=" + sti.getUrl(), LogDb.DB_ABS);
                    continue;
                }
                String key = sti.getMethod() + " " + sti.getUrl();
                if (key.contains("INTEGER") || key.contains("STRING") || key.contains("UUID") || key.contains("OBJECT_ID") || key.contains("FLOAT") || key.contains("VERSIONED") || key.contains("LOCALE")) {
                    templateUrlSet.add(key);
                    continue;
                };

                if (sti.getIsUrlParam()) continue;
                if (sti.getIsHeader()) {
                    staticUrlToSti.putIfAbsent(key, new HashSet<>());
                    continue;
                }

                if (!optimized) {
                    Set<String> set = staticUrlToSti.get(key);
                    if (set == null) {
                        set = new HashSet<>();
                        staticUrlToSti.put(key, set);
                    }
                    set.add(sti.getResponseCode() + " " + sti.getParam());
                }
            }

            for (String s: templateUrlSet) {
                templateUrls.add(s);
            }

            if (optimized) {
                // Skip URLs already audited in a previous run
                if (!alreadyAudited.isEmpty()) {
                    staticUrlToSti.keySet().removeAll(alreadyAudited);
                }

                // Template-to-static: indexed matching against existing templates
                optimizedTemplateToStaticMatch(templateUrls, staticUrlToSti, apiCollectionId, finalResult);

                // Remove matched URLs before static-to-static
                for (String m : finalResult.deleteStaticUrls) staticUrlToSti.remove(m);

                // Static-to-static: discover new templates (Tier 1 + Tier 2)
                ApiMergerResult staticResult = OptimizedMerger.mergeStaticUrls(
                        staticUrlToSti.keySet(), allowStringMerging, allowMergingOnVersions, apiCollectionId);
                finalResult.templateToStaticURLs.putAll(staticResult.templateToStaticURLs);

                loggerMaker.infoAndAddToDb("optimized merging: template-to-static=" + finalResult.deleteStaticUrls.size()
                        + " new-templates=" + staticResult.templateToStaticURLs.size()
                        + " for collection " + apiCollectionId, LogDb.DB_ABS);
            } else {
                // Original: brute force template-to-static + static-to-static
                Iterator<String> iterator = staticUrlToSti.keySet().iterator();
                while (iterator.hasNext()) {
                    String staticURL = iterator.next();
                    if (staticURL == null || staticURL.isEmpty() || !staticURL.contains(" ")) {
                        loggerMaker.warnAndAddToDb("Invalid staticURL: '" + staticURL + "'");
                        iterator.remove();
                        continue;
                    }
                    String[] staticParts = staticURL.split(" ", 2);
                    if (staticParts.length < 2) {
                        loggerMaker.warnAndAddToDb("staticURL missing space: '" + staticURL + "'");
                        iterator.remove();
                        continue;
                    }
                    URLMethods.Method staticMethod = URLMethods.Method.fromString(staticParts[0]);
                    String staticEndpoint = staticParts[1];
                    if (staticEndpoint.contains("//") || staticEndpoint.isEmpty()) {
                        loggerMaker.warnAndAddToDb("staticEndpoint has empty tokens: '" + staticEndpoint + "'");
                        iterator.remove();
                        continue;
                    }

                    for (String templateURL: templateUrls) {
                        if (templateURL == null || templateURL.isEmpty() || !templateURL.contains(" ")) {
                            loggerMaker.warnAndAddToDb("Invalid templateURL: '" + templateURL + "'");
                            continue;
                        }
                        String[] templateParts = templateURL.split(" ", 2);
                        if (templateParts.length < 2) {
                            loggerMaker.warnAndAddToDb("templateURL missing space: '" + templateURL + "'");
                            continue;
                        }
                        URLMethods.Method templateMethod = URLMethods.Method.fromString(templateParts[0]);
                        String templateEndpoint = templateParts[1];
                        if (templateEndpoint.contains("//") || templateEndpoint.isEmpty()) {
                            loggerMaker.warnAndAddToDb("templateEndpoint has empty tokens: '" + templateEndpoint + "'");
                            continue;
                        }

                        URLTemplate urlTemplate = createUrlTemplate(templateEndpoint, templateMethod);
                        if (urlTemplate.match(staticEndpoint, staticMethod)) {
                            // Don't delete static URLs if the matching template was previously demerged
                            if (!isDemergedUrl(urlTemplate, apiCollectionId)) {
                                finalResult.deleteStaticUrls.add(staticURL);
                                iterator.remove();
                            }
                            break;
                        }
                    }
                }

                Map<Integer, Map<String, Set<String>>> sizeToUrlToSti = groupByTokenSize(staticUrlToSti);

                sizeToUrlToSti.remove(1);
                sizeToUrlToSti.remove(0);


                for(int size: sizeToUrlToSti.keySet()) {
                    ApiMergerResult result = tryMergingWithKnownStrictURLs(sizeToUrlToSti.get(size), !mergeUrlsBasic, allowMergingOnVersions, allowStringMerging, apiCollectionId);
                    finalResult.templateToStaticURLs.putAll(result.templateToStaticURLs);
                }
            }

            offset += limit;
            if (isOptimizedMergingAccount()) break;
        } while (!singleTypeInfos.isEmpty());

        loggerMaker.infoAndAddToDb("done with tryMergeUrlsInCollection for collection" + apiCollectionId, LogDb.DB_ABS);

        return finalResult;
    }

    private static Map<Integer, Map<String, Set<String>>> groupByTokenSize(Map<String, Set<String>> catalog) {
        Map<Integer, Map<String, Set<String>>> sizeToURL = new HashMap<>();
        for(String rawURLPlusMethod: catalog.keySet()) {
            if (rawURLPlusMethod == null || rawURLPlusMethod.isEmpty()) {
                loggerMaker.warnAndAddToDb("Empty rawURLPlusMethod in groupByTokenSize");
                continue;
            }
            String[] rawUrlPlusMethodSplit = rawURLPlusMethod.split(" ");
            if (rawUrlPlusMethodSplit.length == 0) {
                loggerMaker.warnAndAddToDb("Split resulted in empty array for: '" + rawURLPlusMethod + "'");
                continue;
            }
            String rawURL = rawUrlPlusMethodSplit.length > 1 ? rawUrlPlusMethodSplit[1] : rawUrlPlusMethodSplit[0];
            if (rawURL == null || rawURL.isEmpty()) {
                loggerMaker.warnAndAddToDb("Empty rawURL for rawURLPlusMethod: '" + rawURLPlusMethod + "'");
                continue;
            }
            Set<String> reqTemplate = catalog.get(rawURLPlusMethod);
            String url = trim(rawURL);
            String[] tokens = url.split("/");
            Map<String, Set<String>> urlSet = sizeToURL.get(tokens.length);
            urlSet = sizeToURL.get(tokens.length);
            if (urlSet == null) {
                urlSet = new HashMap<>();
                sizeToURL.put(tokens.length, urlSet);
            }

            urlSet.put(rawURLPlusMethod, reqTemplate);
        }

        return sizeToURL;
    }

    private static ApiMergerResult tryMergingWithKnownStrictURLs(Map<String, Set<String>> pendingRequests, boolean doBodyMatch, boolean allowMergingOnVersions, boolean allowStringMerging, int apiCollectionId) {
        Map<URLTemplate, Set<String>> templateToStaticURLs = new HashMap<>();

        Iterator<Map.Entry<String, Set<String>>> iterator = pendingRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> entry = iterator.next();
            iterator.remove();

            String newUrl = entry.getKey();
            if (newUrl == null || newUrl.isEmpty() || !newUrl.contains(" ")) {
                loggerMaker.warnAndAddToDb("Invalid newUrl: '" + newUrl + "'");
                continue;
            }
            String[] newUrlParts = newUrl.split(" ", 2);
            if (newUrlParts.length < 2) {
                loggerMaker.warnAndAddToDb("newUrl missing space: '" + newUrl + "'");
                continue;
            }
            Set<String> newTemplate = entry.getValue();
            URLMethods.Method newMethod = URLMethods.Method.fromString(newUrlParts[0]);
            String newEndpoint = newUrlParts[1];

            boolean matchedInDeltaTemplate = false;
            for(URLTemplate urlTemplate: templateToStaticURLs.keySet()){
                if (urlTemplate.match(newEndpoint, newMethod)) {
                    matchedInDeltaTemplate = true;
                    templateToStaticURLs.get(urlTemplate).add(newUrl);
                    break;
                }
            }

            if (matchedInDeltaTemplate) {
                continue;
            }

            int countSimilarURLs = 0;
            Map<URLTemplate, Map<String, Set<String>>> potentialMerges = new HashMap<>();
            for(String aUrl: pendingRequests.keySet()) {
                if (aUrl == null || aUrl.isEmpty() || !aUrl.contains(" ")) {
                    loggerMaker.warnAndAddToDb("Invalid aUrl: '" + aUrl + "'");
                    continue;
                }
                String[] aUrlParts = aUrl.split(" ", 2);
                if (aUrlParts.length < 2) {
                    loggerMaker.warnAndAddToDb("aUrl missing space: '" + aUrl + "'");
                    continue;
                }
                Set<String> aTemplate = pendingRequests.get(aUrl);
                URLMethods.Method aMethod = URLMethods.Method.fromString(aUrlParts[0]);
                String aEndpoint = aUrlParts[1];
                URLStatic aStatic = new URLStatic(aEndpoint, aMethod);
                URLStatic newStatic = new URLStatic(newEndpoint, newMethod);
                URLTemplate mergedTemplate = tryMergeUrls(aStatic, newStatic, allowMergingOnVersions, allowStringMerging);
                if (mergedTemplate == null) {
                    continue;
                }

                boolean compareKeys = doBodyMatch && Context.accountId.get() != 1758525547 && RequestTemplate.compareKeys(aTemplate, newTemplate, mergedTemplate);
                if (areBothMatchingUrls(newStatic,aStatic,mergedTemplate) || areBothUuidUrls(newStatic,aStatic,mergedTemplate) || compareKeys || (allowMergingOnVersions && areBothVersionUrls(newStatic, aStatic, mergedTemplate)) || areBothLocaleUrls(newStatic, aStatic, mergedTemplate)) {
                    Map<String, Set<String>> similarTemplates = potentialMerges.get(mergedTemplate);
                    if (similarTemplates == null) {
                        similarTemplates = new HashMap<>();
                        potentialMerges.put(mergedTemplate, similarTemplates);
                    }
                    similarTemplates.put(aUrl, aTemplate);

                    if (!RequestTemplate.isMergedOnStr(mergedTemplate) || areBothUuidUrls(newStatic,aStatic,mergedTemplate) || areBothMatchingUrls(newStatic,aStatic,mergedTemplate) || areBothLocaleUrls(newStatic, aStatic, mergedTemplate)) {
                        countSimilarURLs = STRING_MERGING_THRESHOLD;
                    }

                    countSimilarURLs++;
                }
            }

            if (countSimilarURLs >= STRING_MERGING_THRESHOLD) {
                URLTemplate mergedTemplate = potentialMerges.keySet().iterator().next();

                // Skip merging if this URL was previously demerged
                if (isDemergedUrl(mergedTemplate, apiCollectionId)) {
                    loggerMaker.infoAndAddToDb("Skipping merge for demerged URL: " + mergedTemplate.getTemplateString() +
                        " " + mergedTemplate.getMethod() + " in collection " + apiCollectionId, LogDb.DB_ABS);
                    continue;
                }

                Set<String> matchedStaticURLs = templateToStaticURLs.get(mergedTemplate);

                if (matchedStaticURLs == null) {
                    matchedStaticURLs = new HashSet<>();
                    templateToStaticURLs.put(mergedTemplate, matchedStaticURLs);
                }

                matchedStaticURLs.add(newUrl);

                for (Map.Entry<String, Set<String>> rt: potentialMerges.getOrDefault(mergedTemplate, new HashMap<>()).entrySet()) {
                    matchedStaticURLs.add(rt.getKey());
                }
            }
        }

        return new ApiMergerResult(templateToStaticURLs);
    }

    public static boolean areBothUuidUrls(URLStatic newUrl, URLStatic deltaUrl, URLTemplate mergedTemplate) {
        Pattern pattern = patternToSubType.get(SingleTypeInfo.UUID);

        String[] n = tokenize(newUrl.getUrl());
        String[] o = tokenize(deltaUrl.getUrl());
        SingleTypeInfo.SuperType[] b = mergedTemplate.getTypes();
        for (int idx =0 ; idx < b.length; idx++) {
            SingleTypeInfo.SuperType c = b[idx];
            if (Objects.equals(c, SingleTypeInfo.SuperType.STRING) && o.length > idx) {
                String val = n[idx];
                if(!pattern.matcher(val).matches() || !pattern.matcher(o[idx]).matches()) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean areBothVersionUrls(URLStatic newUrl, URLStatic deltaUrl, URLTemplate mergedTemplate) {
        String[] n = tokenize(newUrl.getUrl());
        String[] o = tokenize(deltaUrl.getUrl());
        SingleTypeInfo.SuperType[] b = mergedTemplate.getTypes();
        for (int idx =0 ; idx < b.length; idx++) {
            SingleTypeInfo.SuperType c = b[idx];
            if (Objects.equals(c, SingleTypeInfo.SuperType.VERSIONED) && o.length > idx) {
                String val = n[idx];
                if(!isValidVersionToken(val) || !isValidVersionToken(o[idx])) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean areBothLocaleUrls(URLStatic newUrl, URLStatic deltaUrl, URLTemplate mergedTemplate) {
        String[] n = tokenize(newUrl.getUrl());
        String[] o = tokenize(deltaUrl.getUrl());
        SingleTypeInfo.SuperType[] b = mergedTemplate.getTypes();
        for (int idx =0 ; idx < b.length; idx++) {
            SingleTypeInfo.SuperType c = b[idx];
            if (Objects.equals(c, SingleTypeInfo.SuperType.LOCALE) && o.length > idx) {
                String val = n[idx];
                if(!isValidLocaleToken(val) || !isValidLocaleToken(o[idx])) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean areBothMatchingUrls(URLStatic newUrl, URLStatic deltaUrl, URLTemplate mergedTemplate) {

        String[] n = tokenize(newUrl.getUrl());
        String[] o = tokenize(deltaUrl.getUrl());
        SingleTypeInfo.SuperType[] b = mergedTemplate.getTypes();
        for (int idx =0 ; idx < b.length; idx++) {
            SingleTypeInfo.SuperType c = b[idx];
            if (Objects.equals(c, SingleTypeInfo.SuperType.STRING) && o.length > idx) {
                String val = n[idx];
                if(!isAlphanumericString(val) || !isAlphanumericString(o[idx])) {
                    return false;
                }
            }
        }

        return true;
    }

    public static String trim(String url) {
        // if (mergeAsyncOutside) {
        //     if ( !(url.startsWith("/") ) && !( url.startsWith("http") || url.startsWith("ftp")) ){
        //         url = "/" + url;
        //     }
        // } else {
        if (url.startsWith("/")) url = url.substring(1, url.length());
        // }

        if (url.endsWith("/")) url = url.substring(0, url.length()-1);
        return url;
    }

    public static String[] tokenize(String url) {
        return trim(url).split("/");
    }


    public static URLTemplate tryMergeUrls(URLStatic dbUrl, URLStatic newUrl, boolean allowMergingOnVersions, boolean allowStringMerging) {
        if (dbUrl.getMethod() != newUrl.getMethod()) {
            return null;
        }
        String[] dbTokens = tokenize(dbUrl.getUrl());
        String[] newTokens = tokenize(newUrl.getUrl());

        if (dbTokens.length != newTokens.length) {
            return null;
        }

        Pattern pattern = patternToSubType.get(SingleTypeInfo.UUID);

        SingleTypeInfo.SuperType[] newTypes = new SingleTypeInfo.SuperType[newTokens.length];
        int templatizedStrTokens = 0;
        for(int i = 0; i < newTokens.length; i ++) {
            String tempToken = newTokens[i];
            String dbToken = dbTokens[i];

            // Never merge English words - they must remain static path segments
            if (DictionaryFilter.isEnglishWord(tempToken) || DictionaryFilter.isEnglishWord(dbToken)) {
                if (!tempToken.equalsIgnoreCase(dbToken)) {
                    // Two different English words at the same position = no merge possible
                    return null;
                }
                continue;
            }

            if (tempToken.equalsIgnoreCase(dbToken)) {
                continue;
            }

            if (NumberUtils.isParsable(tempToken) && NumberUtils.isParsable(dbToken)) {
                newTypes[i] = SingleTypeInfo.SuperType.INTEGER;
                newTokens[i] = null;
            } else if(pattern.matcher(tempToken).matches() && pattern.matcher(dbToken).matches()){
                // Skip STRING merging if not allowed for this collection
                if (!allowStringMerging) {
                    return null;
                }
                newTypes[i] = SingleTypeInfo.SuperType.STRING;
                newTokens[i] = null;
            }else if(allowMergingOnVersions && isValidVersionToken(tempToken) && isValidVersionToken(dbToken)) {
                newTypes[i] = SingleTypeInfo.SuperType.VERSIONED;
                newTokens[i] = null;
            }else if(isValidLocaleToken(tempToken) && isValidLocaleToken(dbToken)) {
                newTypes[i] = SingleTypeInfo.SuperType.LOCALE;
                newTokens[i] = null;
            }else {
                // Skip STRING merging if not allowed for this collection
                if (!allowStringMerging) {
                    return null;
                }
                newTypes[i] = SingleTypeInfo.SuperType.STRING;
                newTokens[i] = null;
                templatizedStrTokens++;
            }
        }

        if (templatizedStrTokens <= 1) {
            return new URLTemplate(newTokens, newTypes, newUrl.getMethod());
        }

        return null;

    }

    public static URLTemplate createUrlTemplate(String url, URLMethods.Method method) {
        String[] tokens = trimAndSplit(url);
        SingleTypeInfo.SuperType[] types = new SingleTypeInfo.SuperType[tokens.length];
        for(int i = 0; i < tokens.length; i ++ ) {
            String token = tokens[i];

            if (token.equals("STRING")) {
                tokens[i] = null;
                types[i] = SingleTypeInfo.SuperType.STRING;
            } else if (token.equals("INTEGER")) {
                tokens[i] = null;
                types[i] = SingleTypeInfo.SuperType.INTEGER;
            } else if (token.equals("LOCALE")) {
                tokens[i] = null;
                types[i] = SingleTypeInfo.SuperType.LOCALE;
            } else {
                types[i] = null;
            }

        }

        URLTemplate urlTemplate = new URLTemplate(tokens, types, method);

        return urlTemplate;
    }

    public static String[] trimAndSplit(String url) {
        return trim(url).split("/");
    }

    // --- Optimized template-to-static matching ---

    public static boolean isOptimizedMergingAccount() {
        return shouldUseOptimizedMerging
                && Context.accountId.get() == OPTIMIZED_MERGING_ACCOUNT_ID;
    }

    private static boolean useOptimizedMerging(int apiCollectionId) {
        return isOptimizedMergingAccount();
    }

    /**
     * Build a lookup key from method, tokenCount, and tokens array, skipping given positions.
     */
    static String buildKeyDirect(String method, int tokenCount, String[] tokens, int skip1, int skip2) {
        StringBuilder sb = new StringBuilder(method.length() + tokenCount * 10);
        sb.append(method).append('|').append(tokenCount);
        for (int i = 0; i < tokenCount; i++) {
            if (i == skip1 || i == skip2) continue;
            sb.append('|').append(i).append(':').append(tokens[i]);
        }
        return sb.toString();
    }

    /** Encodes wildcard positions as a single long: pos1 in lower 32 bits, pos2 in upper 32 (or -1). */
    private static long encodeWildcardPair(int pos1, int pos2) {
        return ((long) pos2 << 32) | (pos1 & 0xFFFFFFFFL);
    }

    /**
     * Result of buildTemplateIndex: the key→template index plus a secondary index
     * of method|tokenCount → set of actual wildcard position combos from templates.
     */
    static class TemplateIndexResult {
        final Map<String, List<URLTemplate>> index;
        // method|tokenCount → set of encoded wildcard position pairs
        final Map<String, Set<Long>> wildcardPositions;

        TemplateIndexResult(Map<String, List<URLTemplate>> index, Map<String, Set<Long>> wildcardPositions) {
            this.index = index;
            this.wildcardPositions = wildcardPositions;
        }
    }

    /**
     * Index templates by their fixed token positions for O(1) lookup.
     * Also builds a secondary index of which wildcard positions exist per method|tokenCount.
     */
    static TemplateIndexResult buildTemplateIndex(List<String> templateUrls) {
        Map<String, List<URLTemplate>> index = new HashMap<>();
        Map<String, Set<Long>> wildcardPositions = new HashMap<>();
        Set<String> seen = new HashSet<>();

        for (String templateURL : templateUrls) {
            if (templateURL == null || templateURL.isEmpty() || !templateURL.contains(" ")) continue;
            String[] parts = templateURL.split(" ", 2);
            if (parts.length < 2) continue;
            URLMethods.Method method = URLMethods.Method.fromString(parts[0]);
            String endpoint = parts[1];
            if (endpoint.contains("//") || endpoint.isEmpty()) continue;

            URLTemplate urlTemplate = createUrlTemplate(endpoint, method);

            // Deduplicate: "/v1/foo/STRING" and "v1/foo/STRING" produce the same URLTemplate
            if (!seen.add(method.name() + " " + urlTemplate.getTemplateString())) continue;

            String[] tokens = urlTemplate.getTokens();

            // Skip templates where all tokens are wildcards (e.g. STRING/STRING)
            boolean hasLiteral = false;
            for (String t : tokens) { if (t != null) { hasLiteral = true; break; } }
            if (!hasLiteral) continue;

            // Build key using only fixed (non-null) token positions
            StringBuilder sb = new StringBuilder(method.name().length() + tokens.length * 10);
            sb.append(method.name()).append('|').append(tokens.length);
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i] != null) {
                    sb.append('|').append(i).append(':').append(tokens[i]);
                }
            }

            index.computeIfAbsent(sb.toString(), k -> new ArrayList<>()).add(urlTemplate);

            // Record wildcard positions for this method|tokenCount
            String bucketKey = method.name() + "|" + tokens.length;
            int w1 = -1, w2 = -1;
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i] == null) {
                    if (w1 == -1) w1 = i;
                    else if (w2 == -1) w2 = i;
                }
            }
            wildcardPositions.computeIfAbsent(bucketKey, k -> new HashSet<>())
                    .add(encodeWildcardPair(w1, w2));
        }

        return new TemplateIndexResult(index, wildcardPositions);
    }

    /**
     * Optimized template-to-static matching using indexed lookup.
     * Only generates candidate keys for wildcard positions that actually exist in templates.
     * Populates result.deleteStaticUrls and result.existingTemplateMatches.
     */
    static void optimizedTemplateToStaticMatch(
            List<String> templateUrls,
            Map<String, Set<String>> staticUrlToSti,
            int apiCollectionId,
            ApiMergerResult result) {

        TemplateIndexResult tir = buildTemplateIndex(templateUrls);
        Map<String, List<URLTemplate>> index = tir.index;
        Map<String, Set<Long>> wildcardPositions = tir.wildcardPositions;

        for (String staticURL : staticUrlToSti.keySet()) {
            if (staticURL == null || staticURL.isEmpty() || !staticURL.contains(" ")) continue;
            String[] parts = staticURL.split(" ", 2);
            if (parts.length < 2) continue;
            String method = parts[0];
            String endpoint = parts[1];
            if (endpoint.contains("//") || endpoint.isEmpty()) continue;

            // Normalize to match URLTemplate behavior
            String normalized = endpoint;
            if (normalized.startsWith("/")) normalized = normalized.substring(1);
            if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
            String[] tokens = normalized.split("/");

            String bucketKey = method + "|" + tokens.length;
            Set<Long> positions = wildcardPositions.get(bucketKey);
            if (positions == null) continue; // no template with this method|tokenCount

            boolean found = false;
            for (long encoded : positions) {
                int skip1 = (int) (encoded & 0xFFFFFFFFL);
                int skip2 = (int) (encoded >>> 32);
                String key = buildKeyDirect(method, tokens.length, tokens, skip1, skip2);
                List<URLTemplate> candidates = index.get(key);
                if (candidates == null) continue;
                for (URLTemplate tmpl : candidates) {
                    if (tmpl.match(endpoint, URLMethods.Method.fromString(method))) {
                        if (!isDemergedUrl(tmpl, apiCollectionId)) {
                            result.deleteStaticUrls.add(staticURL);
                            String tplKey = tmpl.getMethod().name() + " " + tmpl.getTemplateString();
                            result.existingTemplateMatches
                                    .computeIfAbsent(tplKey, k -> new HashSet<>())
                                    .add(staticURL);
                        }
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
        }
    }

    private static Set<String> loadAuditedUrls(int apiCollectionId) {
        try {
            Set<String> audited = MergeAuditLogDao.instance.getAuditedStaticUrls(apiCollectionId);
            loggerMaker.infoAndAddToDb("Loaded " + audited.size() + " already-audited URLs for collection "
                    + apiCollectionId, LogDb.DB_ABS);
            return audited;
        } catch (Exception e) {
            loggerMaker.warnAndAddToDb("Error loading audited URLs: " + e.getMessage(), LogDb.DB_ABS);
            return Collections.emptySet();
        }
    }

    private static void saveAuditLogs(ApiMergerResult result, int apiCollectionId) {
        int now = Context.now();
        List<MergeAuditLog> logs = new ArrayList<>();

        // Template-to-static: existing templates that absorbed static URLs
        for (Map.Entry<String, Set<String>> entry : result.existingTemplateMatches.entrySet()) {
            String[] parts = entry.getKey().split(" ", 2);
            logs.add(new MergeAuditLog(apiCollectionId, "TEMPLATE_TO_STATIC",
                    parts.length > 1 ? parts[1] : parts[0],
                    parts[0],
                    new ArrayList<>(entry.getValue()), now));
        }

        // Static-to-static: newly discovered templates
        for (Map.Entry<URLTemplate, Set<String>> entry : result.templateToStaticURLs.entrySet()) {
            URLTemplate tpl = entry.getKey();
            String tplUrl = tpl.getTemplateString();
            if (!tplUrl.startsWith("/") && !tplUrl.startsWith("http")) tplUrl = "/" + tplUrl;
            logs.add(new MergeAuditLog(apiCollectionId, "STATIC_TO_STATIC",
                    tplUrl,
                    tpl.getMethod().name(),
                    new ArrayList<>(entry.getValue()), now));
        }

        if (!logs.isEmpty()) {
            try {
                MergeAuditLogDao.instance.insertMany(logs);
                loggerMaker.infoAndAddToDb("Saved " + logs.size() + " merge audit logs for collection "
                        + apiCollectionId, LogDb.DB_ABS);
            } catch (Exception e) {
                loggerMaker.warnAndAddToDb("Error saving merge audit logs: " + e.getMessage(), LogDb.DB_ABS);
            }
        }
    }

    static class ApiMergerResult {
        public Set<String> deleteStaticUrls = new HashSet<>();
        Map<URLTemplate, Set<String>> templateToStaticURLs = new HashMap<>();
        /** Existing template URL string → set of matched static URLs (populated only in optimized path). */
        Map<String, Set<String>> existingTemplateMatches = new HashMap<>();

        public ApiMergerResult(Map<URLTemplate, Set<String>> templateToSti) {
            this.templateToStaticURLs = templateToSti;
        }

        public String toString() {
            String ret = ("templateToSti======================================================: \n");
            for(URLTemplate urlTemplate: templateToStaticURLs.keySet()) {
                ret += (urlTemplate.getTemplateString()) + "\n";
                for(String str: templateToStaticURLs.get(urlTemplate)) {
                    ret += ("\t " + str + "\n");
                }
            }

            return ret;
        }
    }

}
