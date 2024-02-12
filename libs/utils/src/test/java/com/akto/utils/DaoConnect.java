package com.akto.utils;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.mongodb.ConnectionString;
import org.junit.BeforeClass;

public class DaoConnect {

    @BeforeClass
    public static void setup() {
        Context.accountId.set(1000000000);
    }
}
