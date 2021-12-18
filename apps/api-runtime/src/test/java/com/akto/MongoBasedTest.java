package com.akto;

import com.akto.dao.context.Context;
import com.mongodb.ConnectionString;

import org.junit.After;
import org.junit.Before;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.ImmutableMongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;

public class MongoBasedTest {

    public static final int ACCOUNT_ID = 12389;

    MongodExecutable mongodExe;
    MongodProcess mongod;

    @Before
    public final void beforeEach() throws Exception {
        MongodStarter starter = MongodStarter.getDefaultInstance();
        String bindIp = "localhost";
        ImmutableMongodConfig mongodConfig = ImmutableMongodConfig.builder()
        .version(Version.Main.PRODUCTION)
        .net(new Net(bindIp, 27017, false))
        .build();
        this.mongodExe = starter.prepare(mongodConfig);
        this.mongod = mongodExe.start();
        DaoInit.init(new ConnectionString("mongodb://localhost:27017"));
        Context.accountId.set(ACCOUNT_ID);
    }

    @After
    public void afterEach() throws Exception {
        if (this.mongod != null) {
            this.mongod.stop();
            this.mongodExe.stop();
        }
    }

    

}
