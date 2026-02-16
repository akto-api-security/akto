package com.akto.merging;

import com.akto.dao.ApiCollectionsDao;
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

    private static boolean isDemergedUrl(URLTemplate urlTemplate, int apiCollectionId) {
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


        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSti = new ArrayList<>();
        ArrayList<WriteModel<SampleData>> bulkUpdatesForSampleData = new ArrayList<>();
        ArrayList<WriteModel<ApiInfo>> bulkUpdatesForApiInfo = new ArrayList<>();

        for (URLTemplate urlTemplate: result.templateToStaticURLs.keySet()) {
            Set<String> matchStaticURLs = result.templateToStaticURLs.get(urlTemplate);
            String newTemplateUrl = urlTemplate.getTemplateString();
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

        // Check once at collection level whether STRING merging is allowed
        boolean allowStringMerging = !ApiCollectionsDao.shouldSkipMerging(apiCollection) || !(Context.accountId.get() == 1758525547);

        loggerMaker.infoAndAddToDb("allowStringMerging value for collection " + apiCollectionId + " is " + allowStringMerging, LogDb.DB_ABS);

        Bson filterQ = null;
        if (apiCollection != null && apiCollection.getHostName() == null) {
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
        int limit = mergeUrlsBasic ? 10_000 : 1_000_000;

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


                Set<String> set = staticUrlToSti.get(key);
                if (set == null) {
                    set = new HashSet<>();
                    staticUrlToSti.put(key, set);
                }

                set.add(sti.getResponseCode() + " " + sti.getParam());
            }

            for (String s: templateUrlSet) {
                templateUrls.add(s);
            }

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

            offset += limit;
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

    static class ApiMergerResult {
        public Set<String> deleteStaticUrls = new HashSet<>();
        Map<URLTemplate, Set<String>> templateToStaticURLs = new HashMap<>();

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
