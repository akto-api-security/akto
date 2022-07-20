package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.utils.MongoBasedTest;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class TestAccountSettingsDao extends MongoBasedTest {

    @Test
    public void testUpdateCentralKafkaDetailsInitial() {
        AccountSettingsDao.instance.getMCollection().drop();

        AccountSettings accountSettings = new AccountSettings(
                Context.accountId.get(), new ArrayList<>(), false, AccountSettings.SetupType.PROD
        );

        AccountSettingsDao.instance.updateOne(
                Filters.eq(accountSettings.getId()),
                Updates.set(AccountSettings.SETUP_TYPE, accountSettings.getSetupType())
        );

        String ip1 = "ip1";
        String topic1 = "topic1";
        AccountSettingsDao.instance.updateCentralKafkaDetails(ip1, topic1);

        AccountSettings accountSettingsFromDb = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        assertEquals(ip1, accountSettingsFromDb.getCentralKafkaIp());
        assertEquals(topic1, accountSettingsFromDb.getCentralKafkaTopicName());
        assertEquals(AccountSettings.DEFAULT_CENTRAL_KAFKA_LINGER_MS, accountSettingsFromDb.getCentralKafkaLingerMS());
        assertEquals(AccountSettings.DEFAULT_CENTRAL_KAFKA_BATCH_SIZE, accountSettingsFromDb.getCentralKafkaBatchSize());

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.combine(
                        Updates.set(AccountSettings.CENTRAL_KAFKA_IP, "random"),
                        Updates.set(AccountSettings.CENTRAL_KAFKA_TOPIC_NAME, "something"),
                        Updates.set(AccountSettings.CENTRAL_KAFKA_LINGER_MS, 54321),
                        Updates.set(AccountSettings.CENTRAL_KAFKA_BATCH_SIZE, 12345)
                )
        );

        AccountSettingsDao.instance.updateCentralKafkaDetails(ip1, topic1);

        accountSettingsFromDb = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        assertEquals(ip1, accountSettingsFromDb.getCentralKafkaIp());
        assertEquals("something", accountSettingsFromDb.getCentralKafkaTopicName());
        assertEquals( 54321, accountSettingsFromDb.getCentralKafkaLingerMS());
        assertEquals(12345, accountSettingsFromDb.getCentralKafkaBatchSize());

    }
}
