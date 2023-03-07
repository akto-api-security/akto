package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.traffic.SampleData;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
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

        MongoCursor<Document> cursor = instance.getMCollection().listIndexes().cursor();
        int counter = 0;
        while (cursor.hasNext()) {
            counter++;
            cursor.next();
        }

        if (counter == 1) {
            String[] fieldNames = {"_id.apiCollectionId", "_id.url", "_id.method"};
            instance.getMCollection().createIndex(Indexes.ascending(fieldNames));
            counter++;
        }

        if (counter == 2) {
            instance.getMCollection().createIndex(Indexes.ascending("_id.apiCollectionId"));
        }

    }

    public List<SampleData> fetchSampleDataPaginated(int apiCollectionId, String lastFetchedUrl,
                                                     String lastFetchedMethod, int limit, int sliceLimit) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("_id.apiCollectionId", apiCollectionId));


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


}
