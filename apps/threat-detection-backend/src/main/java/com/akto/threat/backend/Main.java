package com.akto.threat.backend;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.akto.DaoInit;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.dao.ThreatDetectionDaoInit;
import com.akto.threat.backend.service.ApiDistributionDataService;
import com.akto.threat.backend.dao.ApiDistributionDataDao;
import com.akto.threat.backend.service.MaliciousEventService;
import com.akto.threat.backend.service.ThreatActorService;
import com.akto.threat.backend.service.ThreatApiService;
import com.akto.threat.backend.tasks.FlushMessagesToDB;
import com.akto.threat.backend.cron.PercentilesCron;
import com.akto.threat.backend.cron.ArchiveOldMaliciousEventsCron;
import com.akto.threat.backend.cron.RiskScoreSyncCron;
import com.akto.threat.backend.cron.ConfigRiskSyncCron;
import com.akto.threat.backend.cron.SkillsRiskScoreSyncCron;
import com.akto.threat.backend.cron.CloudflareWafSyncCron;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.util.AccountTask;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import java.util.function.Consumer;

public class Main {


  public static void main(String[] args) throws Exception {

    ConnectionString connectionString =
        new ConnectionString(System.getenv("AKTO_THREAT_PROTECTION_MONGO_CONN"));
    String dashboardMongoString = System.getenv("AKTO_MONGO_CONN");
    System.out.println("connectionString: " + connectionString);
    CodecRegistry pojoCodecRegistry =
        fromProviders(PojoCodecProvider.builder().automatic(true).build());
    CodecRegistry codecRegistry =
        fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
    MongoClientSettings clientSettings =
        MongoClientSettings.builder()
            .readPreference(ReadPreference.secondary())
            .writeConcern(WriteConcern.ACKNOWLEDGED)
            .applyConnectionString(connectionString)
            .codecRegistry(codecRegistry)
            .build();

    MongoClient threatProtectionMongo = MongoClients.create(clientSettings);

    // Initialize legacy DaoInit for AuthenticationInterceptor (ConfigsDao)
    // ConfigsDao uses CommonContextDao which connects to "common" database
    if(dashboardMongoString != null && !dashboardMongoString.isEmpty()) {
        ConnectionString dashboardMongoConnectionString = new ConnectionString(dashboardMongoString);
        DaoInit.init(dashboardMongoConnectionString, ReadPreference.primary(), WriteConcern.W1);
    }else {
        DaoInit.init(connectionString);
    }

    ThreatDetectionDaoInit.init(threatProtectionMongo);

    KafkaConfig internalKafkaConfig =
        KafkaConfig.newBuilder()
            .setBootstrapServers(System.getenv("THREAT_EVENTS_KAFKA_BROKER_URL"))
            .setGroupId("akto.threat_protection.flush_db")
            .setConsumerConfig(
                KafkaConsumerConfig.newBuilder()
                    .setMaxPollRecords(100)
                    .setPollDurationMilli(100)
                    .build())
            .setProducerConfig(
                KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(1000).build())
            .setKeySerializer(Serializer.STRING)
            .setValueSerializer(Serializer.STRING)
            .build();

    new FlushMessagesToDB(internalKafkaConfig, threatProtectionMongo).run();

    MaliciousEventService maliciousEventService =
        new MaliciousEventService(internalKafkaConfig, MaliciousEventDao.instance);

    ThreatActorService threatActorService = new ThreatActorService(threatProtectionMongo, MaliciousEventDao.instance);
    ThreatApiService threatApiService = new ThreatApiService(MaliciousEventDao.instance);
    ApiDistributionDataService apiDistributionDataService = new ApiDistributionDataService(ApiDistributionDataDao.instance);
    com.akto.log.LoggerMaker logger = new com.akto.log.LoggerMaker(Main.class);

     // Start PercentilesCron (single scheduler for all accounts, runs every 2 hours)
    try {
      PercentilesCron percentilesCron = new PercentilesCron(threatProtectionMongo);
      percentilesCron.startCron();
      logger.infoAndAddToDb("Started PercentilesCron scheduler (runs every 2 hours for all accounts)", com.akto.log.LoggerMaker.LogDb.RUNTIME);
    } catch (Exception e) {
      logger.errorAndAddToDb("Error starting PercentilesCron: " + e.getMessage(), com.akto.log.LoggerMaker.LogDb.RUNTIME);
    }

    new BackendVerticle(maliciousEventService, threatActorService, threatApiService, apiDistributionDataService).start();

    ArchiveOldMaliciousEventsCron cron = new ArchiveOldMaliciousEventsCron(threatProtectionMongo);
    cron.cron();

    RiskScoreSyncCron riskScoreSyncCron = new RiskScoreSyncCron();
    riskScoreSyncCron.setUpRiskScoreSyncCronScheduler();

    SkillsRiskScoreSyncCron skillsRiskScoreSyncCron = new SkillsRiskScoreSyncCron();
    skillsRiskScoreSyncCron.setUp();

    // ConfigRiskSyncCron configRiskSyncCron = new ConfigRiskSyncCron();
    // configRiskSyncCron.setUp();

    CloudflareWafSyncCron cloudflareWafSyncCron = new CloudflareWafSyncCron();
    cloudflareWafSyncCron.setUpCloudflareWafSyncCronScheduler();

    // Initialize threat detection for all accounts
    AccountTask.instance.executeTask(new Consumer<Account>() {
      @Override
      public void accept(Account account) {
        Context.accountId.set(account.getId());
        try {
          String accountId = String.valueOf(account.getId());
          logger.infoAndAddToDb("Starting to create indices for account: " + accountId);
          ThreatDetectionDaoInit.createIndices(accountId);
          logger.infoAndAddToDb("Finished creating indices for account: " + accountId);
        } catch (Exception e) {
          logger.errorAndAddToDb("Error initializing threat detection for account " + account.getId() + ": " + e.getMessage());
        } finally {
          Context.resetContextThreadLocals();
        }
      }
    }, "threat-detection-initializer");

  }

}
