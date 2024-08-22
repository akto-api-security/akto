package com.akto.utils.jobs;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.mongodb.client.model.Filters;

public class CleanInventory {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CleanInventory.class, LogDb.DASHBOARD);
    private static final Logger logger = LoggerFactory.getLogger(CleanInventory.class);

    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void cleanInventoryJobRunner() {

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {

                int now = Context.now();
                logger.info("Starting cleanInventoryJob for all accounts at " + now);

                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            cleanInventoryJob();
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error in cleanInventoryJob");
                        }
                    }
                }, "clean-inventory-job");

                int now2 = Context.now();
                int diffNow = now2-now;
                logger.info(String.format("Completed cleanInventoryJob for all accounts at %d , time taken : %d", now2, diffNow));
            }
        }, 0, 5, TimeUnit.HOURS);

    }

    private static Set<String> methodSet = new HashSet<>();

    private static Set<String> getMethodSet() {

        if (!methodSet.isEmpty()) {
            return methodSet;
        }

        List<String> lowerCaseMethods = Arrays.asList(URLMethods.Method.getValuesArray()).stream()
                .map(s -> s.name().toLowerCase()).collect(Collectors.toList());
        List<String> upperCaseMethods = Arrays.asList(URLMethods.Method.getValuesArray()).stream()
                .map(s -> s.name().toUpperCase()).collect(Collectors.toList());
        methodSet.addAll(upperCaseMethods);
        methodSet.addAll(lowerCaseMethods);
        return methodSet;
    }

    private static void cleanInventoryJob() {

        int now = Context.now();
        SingleTypeInfoDao.instance.deleteAll(Filters.nin(SingleTypeInfo._METHOD, getMethodSet()));
        SensitiveSampleDataDao.instance.deleteAll(Filters.nin("_id.method", getMethodSet()));
        /*
         * The above collections implement method as String, thus cleaning them.
         * Rest of the collections implement method as an ENUM,
         * thus they will not have any non-standard method.
         * Any non-standard method will be in the form of "OTHER". Thus ignoring them.
         */

        int now2 = Context.now();
        int diff = now2 - now;

        if (diff >= 2) {
            loggerMaker.infoAndAddToDb(String.format("cleanInventoryJob finished, time taken: %d ", diff));
        }

    }

}