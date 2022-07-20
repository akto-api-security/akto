package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;


public class AccountSettingsDao extends AccountsContextDao<AccountSettings> {

    public static Bson generateFilter() {
        return Filters.eq("_id", Context.accountId.get());
    }

    public static final AccountSettingsDao instance = new AccountSettingsDao();

    @Override
    public String getCollName() {
        return "accounts_settings";
    }

    @Override
    public Class<AccountSettings> getClassT() {
        return AccountSettings.class;
    }

    public void updateCentralKafkaDetails(String currentInstanceIp, String topicName) {
        if (currentInstanceIp == null) return;

        List<WriteModel<AccountSettings>> bulkWrites = new ArrayList<>();

        bulkWrites.add(
                new UpdateOneModel<>(
                        AccountSettingsDao.generateFilter(),
                        Updates.set(AccountSettings.CENTRAL_KAFKA_IP, currentInstanceIp)
                )
        );

        bulkWrites.add(
                new UpdateOneModel<>(
                        Filters.and(AccountSettingsDao.generateFilter(), Filters.exists(AccountSettings.CENTRAL_KAFKA_TOPIC_NAME, false)),
                        Updates.set(AccountSettings.CENTRAL_KAFKA_TOPIC_NAME, topicName)
                )
        );

        bulkWrites.add(
                new UpdateOneModel<>(
                        Filters.and(AccountSettingsDao.generateFilter(), Filters.exists(AccountSettings.CENTRAL_KAFKA_BATCH_SIZE, false)),
                        Updates.set(AccountSettings.CENTRAL_KAFKA_BATCH_SIZE, AccountSettings.DEFAULT_CENTRAL_KAFKA_BATCH_SIZE)
                )
        );

        bulkWrites.add(
                new UpdateOneModel<>(
                        Filters.and(AccountSettingsDao.generateFilter(), Filters.exists(AccountSettings.CENTRAL_KAFKA_LINGER_MS, false)),
                        Updates.set(AccountSettings.CENTRAL_KAFKA_LINGER_MS, AccountSettings.DEFAULT_CENTRAL_KAFKA_LINGER_MS)
                )
        );

        AccountSettingsDao.instance.getMCollection().bulkWrite(bulkWrites);

    }
}
