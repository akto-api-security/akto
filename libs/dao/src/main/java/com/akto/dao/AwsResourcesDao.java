package com.akto.dao;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dto.AwsResources;
import com.mongodb.client.model.Filters;

public class AwsResourcesDao extends AccountsContextDao<AwsResources> {

    @Override
    public String getCollName() {
        return "aws_resources";
    }

    @Override
    public Class<AwsResources> getClassT() {
        return AwsResources.class;
    }

    public static final AwsResourcesDao instance = new AwsResourcesDao();

    public static Bson generateFilter() {
        return generateFilter(Context.accountId.get());
    }

    public static Bson generateFilter(int accountId) {
        return Filters.eq("_id", accountId);
    }
}
