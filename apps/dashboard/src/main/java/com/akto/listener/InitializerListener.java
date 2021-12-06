package com.akto.listener;

import com.akto.DaoInit;
import com.akto.dao.MarkovDao;
import com.akto.dao.UsersDao;
import com.akto.dto.Markov;
import com.akto.dto.User;
import com.akto.dto.messaging.Message;
import com.mongodb.ConnectionString;
import com.mongodb.client.result.InsertOneResult;
import com.akto.dao.context.Context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletContextListener;

public class InitializerListener implements ServletContextListener {

    private static String domain = null;
    public static String getDomain() {
        if(domain == null) {
            if (true) {
                domain = "https://staging.akto.io:8443";
            } else {
                domain = "http://localhost:8080";
            }
        }

        return domain;
    }

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

        System.out.println("context initialized");

        // String mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        System.out.println("MONGO URI " + mongoURI);


        DaoInit.init(new ConnectionString(mongoURI));
    }
}
