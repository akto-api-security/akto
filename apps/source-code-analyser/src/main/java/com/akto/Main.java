package com.akto;

import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.CodeAnalysisRepo;
import com.akto.log.LoggerMaker;
import com.akto.util.AccountTask;
import com.mongodb.ConnectionString;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, LoggerMaker.LogDb.RUNTIME);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final int SLEEP_TIME = 10 * 1000;
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static boolean connectToMongo() {

        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        if (StringUtils.isEmpty(mongoURI)) {
            return false;
        }
        boolean connectedToMongo = false;
        boolean calledOnce = false;
        do {
            try {

                if (!calledOnce) {
                    DaoInit.init(new ConnectionString(mongoURI));
                    calledOnce = true;
                }
                AccountsDao.instance.getStats();
                connectedToMongo = true;

                logger.info("connected to mongo");
            } catch (Exception e) {
                logger.error("error connecting to mongo", e);
            } finally {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
        } while (!connectedToMongo);
        return connectedToMongo;
    }


    private static List<CodeAnalysisRepo> fetchReposToSync() {
        List<CodeAnalysisRepo> repos = dataActor.findReposToRun();
        if (repos!= null && !repos.isEmpty()) {
            return repos;
        }
        return null;
    }

    private static void runForRepo(List<CodeAnalysisRepo> repos) {
        if (repos == null) {
            loggerMaker.infoAndAddToDb("No repos to run, skipping");
            return;
        }
        if (BitbucketRepo.doesEnvVariablesExists()) {
            BitbucketRepo.fetchAllProjectKeys();
        }
        for (CodeAnalysisRepo repo : repos) {
            SourceCodeAnalyserRepo sourceCodeAnalyserRepo;
            if (repo.getSourceCodeType() == CodeAnalysisRepo.SourceCodeType.BITBUCKET) {
                sourceCodeAnalyserRepo = new BitbucketRepo(repo);
            } else {
                sourceCodeAnalyserRepo = new GithubRepo(repo);
            }
            try {
                sourceCodeAnalyserRepo.fetchEndpointsUsingAnalyser();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while fetching endpoints:" + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        boolean isConnectedToMongo = connectToMongo();//When mongo connection, fetch for all accounts

        while (true) {
//            if (!BitbucketRepo.doesEnvVariablesExists() && !GithubRepo.doesEnvVariablesExists()) {
//                loggerMaker.infoAndAddToDb("No tokens found");
//                Thread.sleep(SLEEP_TIME);
//                continue;
//            }
            if (isConnectedToMongo) {
                Context.accountId.set(1662497829);
                loggerMaker.infoAndAddToDb("Fetching repos for 1662497829 account id");
                List<CodeAnalysisRepo> repos = fetchReposToSync();
                runForRepo(repos);
//                AccountTask.instance.executeTask(t -> {
//                    List<CodeAnalysisRepo> repos = fetchReposToSync();
//                    runForRepo(repos);
//                }, "initialize-runtime-task");
            } else {
                List<CodeAnalysisRepo> repos = fetchReposToSync();
                runForRepo(repos);
            }
            Thread.sleep(SLEEP_TIME);
        }
    }
}