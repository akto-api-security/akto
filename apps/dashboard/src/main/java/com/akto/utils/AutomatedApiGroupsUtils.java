package com.akto.utils;

import com.akto.github.GithubFile;

public class AutomatedApiGroupsUtils {

    public static void fetchGroups() {
        System.out.println("Fetching groups...");

        GithubSync githubSync = new GithubSync();
        GithubFile ghf = githubSync.syncFile("akto-api-security/akto", "automated-api-groups/automated-api-groups.csv", null, null);

        System.out.println(ghf.getContent());
    }
}
