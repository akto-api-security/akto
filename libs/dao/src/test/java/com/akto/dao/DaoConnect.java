package com.akto.dao;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.mongodb.ConnectionString;
import org.junit.BeforeClass;

public class DaoConnect {

    public final static String mongodbURI = "mongodb://localhost:27017";

    @BeforeClass
    public static void setup() {
        ConnectionString connectionString = new ConnectionString(mongodbURI);
        DaoInit.init(connectionString);
        Context.accountId.set(222222);

    }
}
