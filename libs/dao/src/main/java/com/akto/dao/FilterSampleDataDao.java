package com.akto.dao;

import com.akto.dto.ApiInfo;
import com.akto.dto.FilterSampleData;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;


public class FilterSampleDataDao extends AccountsContextDao<FilterSampleData>{

    public static FilterSampleDataDao instance = new FilterSampleDataDao();

    public List<ApiInfo.ApiInfoKey> getIds() {
        Bson projection = Projections.fields(Projections.include());
        MongoCursor<FilterSampleData> cursor = instance.getMCollection().find().projection(projection).cursor();

        ArrayList<ApiInfo.ApiInfoKey> ret = new ArrayList<>();

        while(cursor.hasNext()) {
            FilterSampleData elem = cursor.next();
            ret.add(elem.getId());
        }

        return ret;
    }

    @Override
    public String getCollName() {
        return "filter_sample_data";
    }

    @Override
    public Class<FilterSampleData> getClassT() {
        return FilterSampleData.class;
    }
}
