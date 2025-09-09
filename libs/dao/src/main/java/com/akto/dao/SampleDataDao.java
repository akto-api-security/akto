package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;


import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SampleDataDao extends AccountsContextDaoWithRbac<SampleData> {

    public static final SampleDataDao instance = new SampleDataDao();

    @Override
    public String getCollName() {
        return "sample_data";
    }

    @Override
    public Class<SampleData> getClassT() {
        return SampleData.class;
    }

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }


        String[] fieldNames = {"_id.apiCollectionId", "_id.url", "_id.method"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{"_id.apiCollectionId"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);


        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { SingleTypeInfo._COLLECTION_IDS, ApiInfo.ID_URL, ApiInfo.ID_METHOD }, true);

    }

    public SampleData fetchSampleDataForApi(int apiCollectionId, String url, URLMethods.Method method) {
        Bson filterQSampleData = filterForSampleData(apiCollectionId, url, method);
        return SampleDataDao.instance.findOne(filterQSampleData);
    }

    public SampleData fetchAllSampleDataForApi(int apiCollectionId, String url, URLMethods.Method method) {
        Bson filterQSampleData = filterForSampleData(apiCollectionId, url, method);
        List<SampleData> list = SampleDataDao.instance.findAll(filterQSampleData);
        SampleData sampleData = new SampleData();
        if (list != null && !list.isEmpty()) {
            sampleData = list.get(0);
            if (list.size() > 1) {
                for (SampleData data : list) {
                    sampleData.getSamples().addAll(data.getSamples());
                }
            }
        }
        return sampleData;
    }

    public static Bson filterForSampleData(int apiCollectionId, String url, URLMethods.Method method) {
        return Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.url", url),
                Filters.eq("_id.method", method.name())
        );
    }


    public static List<Bson> filterForMultipleSampleData(List<Key> sampleList) {
        List<Bson> ret = new ArrayList<>();

        for(Key key: sampleList) {
            Bson f = filterForSampleData(key.getApiCollectionId(), key.getUrl(), key.getMethod());
            ret.add(f);
        }

        return ret;
    }

    public List<SampleData> fetchSampleDataPaginated(int apiCollectionId, String lastFetchedUrl,
                                                     String lastFetchedMethod, int limit, int sliceLimit) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.in(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiCollectionId)));


        if (lastFetchedUrl != null && lastFetchedMethod != null) {
            Bson f1 = Filters.gt("_id.url", lastFetchedUrl);
            Bson f2 = Filters.and(
                    Filters.eq("_id.url", lastFetchedUrl),
                    Filters.gt("_id.method", lastFetchedMethod)
            );

            filters.add(
                    Filters.or(f1, f2)
            );
        }

        Bson sort = Sorts.ascending("_id.url", "_id.method");

        MongoCursor<SampleData> cursor = SampleDataDao.instance.getMCollection()
                .find(Filters.and(filters))
                .projection(Projections.slice("samples", sliceLimit))
                .skip(0)
                .limit(limit)
                .sort(sort)
                .cursor();

        List<SampleData> sampleDataList = new ArrayList<>();

        while (cursor.hasNext()) {
            SampleData sampleData = cursor.next();
            sampleDataList.add(sampleData);
        }

        cursor.close();

        return sampleDataList;
    }

    public List<SampleData> fetchSampleDataPaginated(String lastFetchedUrl,
                                                     String lastFetchedMethod, int limit) {
       Bson filters = Filters.empty();

        if (lastFetchedUrl != null && lastFetchedMethod != null) {
            Bson f1 = Filters.gt("_id.url", lastFetchedUrl);
            Bson f2 = Filters.and(
                    Filters.eq("_id.url", lastFetchedUrl),
                    Filters.gt("_id.method", lastFetchedMethod)
            );

            filters = Filters.or(f1, f2);
        }

        Bson sort = Sorts.ascending("_id.url", "_id.method");

        MongoCursor<SampleData> cursor = SampleDataDao.instance.getMCollection()
                .find(Filters.and(filters))
                .skip(0)
                .limit(limit)
                .sort(sort)
                .cursor();

        List<SampleData> sampleDataList = new ArrayList<>();

        while (cursor.hasNext()) {
            SampleData sampleData = cursor.next();
            sampleDataList.add(sampleData);
        }

        cursor.close();

        return sampleDataList;
    }

    // use the same method in SensitiveSampleDataDao to backfill isQueryParam in SingleTypeInfo for accounts having non-redacted data
    // for redaction on accounts, we don't have sensitive sample data, so use this instead
    public void backFillIsQueryParamInSingleTypeInfo(int apiCollectionId) {
        List<String> subTypes = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();
        Bson baseFilter = Filters.and(
            Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
            Filters.eq(SingleTypeInfo._IS_HEADER, false),
            Filters.in(SingleTypeInfo.SUB_TYPE, subTypes),
            Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId)
        );
        ObjectId lastProcessedId = null;
        while (true) {
            
            if (lastProcessedId != null) {
                baseFilter = Filters.and(
                    baseFilter,
                    Filters.gt("_id", lastProcessedId)
                );
            }
            ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSingleTypeInfo = new ArrayList<>();
            List<SingleTypeInfo> stis = SingleTypeInfoDao.instance.findAll(baseFilter, 0, 1000, Sorts.ascending(Constants.ID));
            if (stis.isEmpty()) {
                break;
            }
            lastProcessedId = stis.get(stis.size() - 1).getId();
            String currentMethod = "";
            String currentUrl = "";
            List<String> samples = new ArrayList<>();

            for (SingleTypeInfo sti : stis) {
                if(!currentMethod.equals(sti.getMethod()) && !currentUrl.equals(sti.getUrl())) {
                    currentMethod = sti.getMethod();
                    currentUrl = sti.getUrl();
                    SampleData sampleData = SampleDataDao.instance.findOne(
                        ApiInfoDao.getFilter(currentUrl, currentMethod, apiCollectionId)
                    );
                    samples = sampleData != null ? sampleData.getSamples() : new ArrayList<>();
                }

                for (String sample : samples) {
                    if (SensitiveSampleDataDao.hasAnyQueryParam(sample, sti.getParam())) {
                        System.out.println("Found matching query param for SingleTypeInfo: " + sti.getParam() + " in URL: " + currentUrl + ", Method: " + currentMethod);
                        bulkUpdatesForSingleTypeInfo.add(new UpdateOneModel<>(
                            Filters.and(
                                Filters.eq(SingleTypeInfo._URL, currentUrl),
                                Filters.eq(SingleTypeInfo._METHOD, currentMethod),
                                Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
                                Filters.eq(SingleTypeInfo._IS_HEADER, false),
                                Filters.eq(SingleTypeInfo._PARAM, sti.getParam()),
                                Filters.eq(SingleTypeInfo.SUB_TYPE, sti.getSubType()),
                                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId)
                            ),
                            Updates.set("isQueryParam", true)
                        ));
                    }
                }
            }
            if (!bulkUpdatesForSingleTypeInfo.isEmpty()) {
                System.out.println("Bulk updating SingleTypeInfo documents..., size=" + bulkUpdatesForSingleTypeInfo.size() + " for API Collection ID: " + apiCollectionId);
                SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForSingleTypeInfo);
            }
        }
    }


    @Override
    public String getFilterKeyString() {
        return TestingEndpoints.getFilterPrefix(ApiCollectionUsers.CollectionType.Id_ApiCollectionId) + ApiInfo.ApiInfoKey.API_COLLECTION_ID;
    }
}
