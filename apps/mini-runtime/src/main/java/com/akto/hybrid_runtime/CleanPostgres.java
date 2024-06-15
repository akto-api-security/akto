package com.akto.hybrid_runtime;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.util.AccountTask;

public class CleanPostgres {

    final static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public static void cleanPostgresJob() {

        executorService.schedule(new Runnable() {

            @Override
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        cleanPostgresJobForAccount();
                    }
                }, "clean-postgres");
            }

        }, 0, TimeUnit.SECONDS);

    }

    public static void cleanPostgresJobForAccount(){

        int accountId = Context.accountId.get();

        // while(res.size == limit) {
        //     // run limit offset query, uuid projection
        //     // api call, to send uuid
        //     // res invalid uuid
        //     // run bulk delete query
        // }
        


    }

}