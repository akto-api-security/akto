package com.akto.utils;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.GithubConfig;

public class GithubLogin {

    public static final int PROBE_PERIOD_IN_SECS = 60;
    private static GithubLogin instance = null;
    private GithubConfig githubConfig = null;
    private int lastProbeTs = 0;

    public static GithubLogin getInstance() {
        boolean shouldProbeAgain = true;
        if (instance != null) {
            shouldProbeAgain = Context.now() - instance.lastProbeTs >= PROBE_PERIOD_IN_SECS;
        }

        if (shouldProbeAgain) {
            GithubConfig ghConfig = (Config.GithubConfig) ConfigsDao.instance.findOne("_id", "GITHUB-ankush");
            if (instance == null) {
                instance = new GithubLogin();
            }

            instance.githubConfig = ghConfig;
            instance.lastProbeTs = Context.now();
        }

        return instance;
    }

    public static String getClientId() {
        if (getInstance() == null) return null;

        GithubConfig ghConfig = getInstance().getGithubConfig();
        if (ghConfig == null) return null;

        return ghConfig.getClientId();
    }

    private GithubLogin() {
    }

    public GithubConfig getGithubConfig() {
        return this.githubConfig;
    }

    public int getLastProbeTs() {
        return lastProbeTs;
    }

}
