package com.akto.utils;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.mongodb.ConnectionString;
import org.junit.BeforeClass;

public class DaoConnect {

    static String mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";

    @BeforeClass
    public static void setup() {
        ConnectionString connectionString = new ConnectionString(mongoURI);
        DaoInit.init(connectionString);
        Context.accountId.set(1000000000);
    }
}
