package com.akto.utils;

import com.akto.dao.BlockedTokenDao;
import com.akto.dto.BlockedToken;
import com.akto.log.LoggerMaker;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TokenBlocklistCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TokenBlocklistCron.class, LoggerMaker.LogDb.CYBORG);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static Set<String> blockedTokens = new HashSet<>();

    public static Set<String> getBlockedTokens() {
        return new HashSet<>(blockedTokens);
    }

    public void runCron() {
        scheduler.scheduleAtFixedRate(
            this::loadBlockedTokens,
            0,
            1,
            TimeUnit.HOURS
        );
    }

    private void loadBlockedTokens() {
        try {
            List<BlockedToken> tokens = BlockedTokenDao.instance.getMCollection()
                .find(Filters.exists(BlockedToken.TOKEN))
                .into(new ArrayList<>());

            Set<String> newBlockedTokens = new HashSet<>();
            if (tokens != null) {
                for (BlockedToken token : tokens) {
                    if (token.getToken() != null && !token.getToken().isEmpty()) {
                        newBlockedTokens.add(token.getToken());
                    }
                }
            }

            blockedTokens = newBlockedTokens;
            loggerMaker.infoAndAddToDb("Loaded " + blockedTokens.size() + " blocked tokens");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error loading blocked tokens: " + e.toString());
        }
    }

    public static boolean isTokenBlocked(String token) {
        return token != null && blockedTokens.contains(token);
    }
}
