package com.akto.utils.crons;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.conversions.Bson;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.billing.TokensDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.akto.util.DashboardMode;
import com.akto.util.UsageUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import static com.akto.task.Cluster.callDibs;

public class TokenGeneratorCron {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(SyncCron.class, LogDb.DASHBOARD);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void tokenGeneratorScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        if (!DashboardMode.isOnPremDeployment() && !DashboardMode.isSaasDeployment()) {
                            loggerMaker.debugAndAddToDb("Skipping tokenGeneratorScheduler, deployment type condition not satisfied");
                            return;
                        }

                        boolean dibs = callDibs(Cluster.TOKEN_GENERATOR_CRON, 600, 60);
                        if(!dibs){
                            loggerMaker.debugAndAddToDb("Skipping tokenGeneratorScheduler, lock not acquired");
                            return;
                        }

                        int accountId = Context.accountId.get();
                        Organization organization = OrganizationsDao.instance.findOne(
                            Filters.in(Organization.ACCOUNTS, accountId)
                        );
                        if (organization == null) {
                            loggerMaker.debugAndAddToDb("Skipping token generation for account " +  accountId + "organization not found");
                            return;
                        }

                        BasicDBObject reqBody = new BasicDBObject();
                        reqBody.put(Tokens.ORG_ID, organization.getId());
                        reqBody.put(Tokens.ACCOUNT_ID, accountId);
                        BasicDBObject resp = UsageMetricUtils.fetchFromBillingService("saveToken", reqBody);
                        if (resp == null || resp.get("tokens") == null || !(resp.get("tokens") instanceof BasicDBObject)) {
                            loggerMaker.debugAndAddToDb("Skipping token generation for account " +  accountId + "fetch token call failed");
                            return;
                        }
                        
                        BasicDBObject respToken = (BasicDBObject) resp.get("tokens");
                        if (respToken.get(Tokens.UPDATED_AT) == null) {
                            loggerMaker.debugAndAddToDb("Skipping saving token in dashboard for account " +  accountId + "updated at is null");
                            return;
                        }

                        if (respToken.get(Tokens.TOKEN) == null) {
                            loggerMaker.debugAndAddToDb("Skipping saving token in dashboard for account " +  accountId + "token received is null");
                            return;
                        }
                        Bson filters = Filters.and(
                            Filters.eq(Tokens.ACCOUNT_ID, accountId),
                            Filters.eq(Tokens.ORG_ID, organization.getId())
                        );
                        Tokens tokens = TokensDao.instance.findOne(filters);

                        Bson updates;
                        if (tokens == null) {
                            updates = Updates.combine(
                                Updates.setOnInsert(Tokens.CREATED_AT, Context.now()),
                                Updates.setOnInsert(Tokens.ORG_ID, organization.getId()),
                                Updates.setOnInsert(Tokens.ACCOUNT_ID, accountId),
                                Updates.set(Tokens.UPDATED_AT, respToken.getInt(Tokens.UPDATED_AT))
                            );
                        } else {
                            updates = Updates.combine(
                                Updates.set(Tokens.UPDATED_AT, respToken.getInt(Tokens.UPDATED_AT))
                            );
                        }

                        UsageUtils.saveToken(organization.getId(), accountId, updates, filters, respToken.getString(Tokens.TOKEN));
                    }
                }, "token-generator-cron");
            }
        }, 0, 4, TimeUnit.HOURS);
    }

}
