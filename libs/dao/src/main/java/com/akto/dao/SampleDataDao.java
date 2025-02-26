package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SampleDataDao extends AccountsContextDao<SampleData> {

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

        // index on sampleDataIds.

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


}
