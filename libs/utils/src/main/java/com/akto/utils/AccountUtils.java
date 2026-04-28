package com.akto.utils;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;
import com.akto.dto.Account;

public class AccountUtils {

    private static final Logger logger = LoggerFactory.getLogger(AccountUtils.class);

    private static final ConcurrentHashMap<Integer, Account> accountCache = new ConcurrentHashMap<>();

    /**
     * Returns the Account for the given accountId.
     * Serves from cache if already fetched; otherwise queries Context.getAccount() and caches the result.
     * Returns null if not found or on error.
     */
    public static Account fetchAccount(int accountId) {
        Account cached = accountCache.get(accountId);
        if (cached != null) {
            return cached;
        }
        try {
            Account account = Context.getAccount();
            if (account != null) {
                accountCache.put(accountId, account);
            }
            return account;
        } catch (Exception e) {
            logger.error("Failed to fetch account for accountId=" + accountId);
            return null;
        }
    }

}
