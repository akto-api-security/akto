package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestAccountSettingsDao extends MongoBasedTest {


    @Test
    public void testUpdateOnboardingFlag() {
        int id = 1_000_000;
        Context.accountId.set(id);
        AccountSettingsDao.instance.getMCollection().drop();

        AccountSettings accountSettings = new AccountSettings(id, new ArrayList<>(), false, AccountSettings.SetupType.PROD);
        AccountSettingsDao.instance.insertOne(accountSettings);

        accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        assertFalse(accountSettings.isShowOnboarding()); // by default show onboarding should be false.

        AccountSettingsDao.instance.updateOnboardingFlag(true);
        accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        assertTrue(accountSettings.isShowOnboarding());

        AccountSettingsDao.instance.getMCollection().drop();

        AccountSettingsDao.instance.updateOnboardingFlag(false);
        accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        assertFalse(accountSettings.isShowOnboarding());


    }
}
