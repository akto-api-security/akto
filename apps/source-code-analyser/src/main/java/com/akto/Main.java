package com.akto;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.CodeAnalysisRepo;
import com.akto.log.LoggerMaker;

import java.util.List;


public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, LoggerMaker.LogDb.RUNTIME);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final int SLEEP_TIME = 10 * 1000;

    private static List<CodeAnalysisRepo> fetchReposToSync() {
        List<CodeAnalysisRepo> repos = dataActor.findReposToRun();
        if (repos!= null && !repos.isEmpty()) {
            return repos;
        }
        return null;
    }

    public static void main(String[] args) throws InterruptedException {
        while (true) {
            if (!BitbucketRepo.doesEnvVariablesExists() && !GithubRepo.doesEnvVariablesExists()) {
                loggerMaker.infoAndAddToDb("No tokens found");
                Thread.sleep(SLEEP_TIME);
                continue;
            }
            List<CodeAnalysisRepo> repos = fetchReposToSync();
            if (repos == null) {
                loggerMaker.infoAndAddToDb("No repos to run, skipping");
                Thread.sleep(SLEEP_TIME);
                continue;
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

            Thread.sleep(SLEEP_TIME);
        }

    }

}