package com.akto.utils;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.mongodb.ConnectionString;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.ImmutableMongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class MongoBasedTest {

    public static final int ACCOUNT_ID = 12389;

    public static MongodExecutable mongodExe;
    public static MongodProcess mongod;


    @BeforeClass
    public static void beforeClass() throws Exception {
        MongodStarter starter = MongodStarter.getDefaultInstance();
        String bindIp = "localhost";
        ImmutableMongodConfig mongodConfig = ImmutableMongodConfig.builder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(bindIp, 27019, false))
                .build();
        mongodExe = starter.prepare(mongodConfig);
        mongod = mongodExe.start();
        DaoInit.init(new ConnectionString("mongodb://localhost:27019"));
        Context.accountId.set(ACCOUNT_ID);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (mongod != null) {
            mongod.stop();
            mongodExe.stop();
        }
    }



}
