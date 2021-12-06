package com.akto.dao;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.mongodb.ConnectionString;
import org.junit.BeforeClass;

public class DaoConnect {

    public final static String mongodbURI = "mongodb+srv://write_ops:write_ops@cluster0.svbek.mongodb.net/admin?retryWrites=true&w=majority";

    @BeforeClass
    public static void setup() {
        ConnectionString connectionString = new ConnectionString(mongodbURI);
        DaoInit.init(connectionString);
        Context.accountId.set(1000000000);
    }
}
