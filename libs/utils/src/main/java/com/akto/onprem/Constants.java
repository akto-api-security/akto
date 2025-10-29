package com.akto.onprem;

import com.akto.DaoInit;
import com.akto.dao.ConfigsDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.util.DashboardMode;
import com.akto.utils.RSAKeyPairUtils;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import com.akto.dto.billing.Organization;

public class Constants {
    public static final DashboardMode DEFAULT_DASHBOARD_MODE = DashboardMode.LOCAL_DEPLOY;
    public static final String SENDGRID_TOKEN_KEY = "";

    public static final String INTERCOM_HASH_KEY = "";

    private static final String JWT_PRIVATE_KEY =  "";

    private final static String JWT_PUBLIC_KEY = "";//new String(keyBytes);

    public static final String SLACK_ALERT_USAGE_URL = "";

    private static final byte[] privateKey;
    private static final byte[] publicKey;

    static {
        byte[] tempPrivateKey = null;
        byte[] tempPublicKey = null;

        // Try to initialize DB connection and fetch keys from database
        try {
            // Check if DB client is not already initialized
            if (!MCollection.checkConnection()) {
                String mongoURI = System.getenv("AKTO_MONGO_CONN");
                if (mongoURI != null && !mongoURI.isEmpty()) {
                    DaoInit.init(new ConnectionString(mongoURI));
                }
            }

            // Now try to fetch keys if DB is connected
            if (MCollection.checkConnection()) {
                byte[][] keys = RSAKeyPairUtils.fetchKeysFromDb();
                if (keys != null && keys.length == 2) {
                    tempPrivateKey = keys[0];
                    tempPublicKey = keys[1];
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // If keys weren't found in database, generate new ones and save them
        if (tempPrivateKey == null || tempPublicKey == null) {
            KeyPairGenerator kpg;
            KeyPair kp = null;
            try {
                kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                kp = kpg.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                ;
            }

            tempPrivateKey = kp == null ? null : kp.getPrivate().getEncoded();
            tempPublicKey = kp == null ? null : kp.getPublic().getEncoded();

            // Save generated keys to database
            if (tempPrivateKey != null && tempPublicKey != null && MCollection.checkConnection()) {
                try {
                    String pemPrivateKey = "-----BEGIN PRIVATE KEY-----\n" +
                            Base64.getEncoder().encodeToString(tempPrivateKey) + "\n" +
                            "-----END PRIVATE KEY-----";

                    String pemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                            Base64.getEncoder().encodeToString(tempPublicKey) + "\n" +
                            "-----END PUBLIC KEY-----";

                    int now = Context.now();
                    Config.RSAKeyPairConfig newConfig = new Config.RSAKeyPairConfig(pemPrivateKey, pemPublicKey, now);
                    ConfigsDao.instance.insertOne(newConfig);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        privateKey = tempPrivateKey;
        publicKey = tempPublicKey;
    }

    public static byte[] getPrivateKey() {
        if (!DashboardMode.isOnPremDeployment()) {
            return privateKey;
        }

        String rsaPrivateKey = JWT_PRIVATE_KEY;
        rsaPrivateKey = rsaPrivateKey.replace("-----BEGIN PRIVATE KEY-----","");
        rsaPrivateKey = rsaPrivateKey.replace("-----END PRIVATE KEY-----","");
        rsaPrivateKey = rsaPrivateKey.replace("\n","");

        return Base64.getDecoder().decode(rsaPrivateKey);
    }

    public static byte[] getPublicKey() {
        if (!DashboardMode.isOnPremDeployment()) {
            return publicKey;
        }

        String rsaPublicKey= JWT_PUBLIC_KEY;
        rsaPublicKey = rsaPublicKey.replace("-----BEGIN PUBLIC KEY-----","");
        rsaPublicKey = rsaPublicKey.replace("-----END PUBLIC KEY-----","");
        rsaPublicKey = rsaPublicKey.replace("\n","");

        return Base64.getDecoder().decode(rsaPublicKey);
    }

    private static Config.SendgridConfig sendgridConfig;
    private static int lastSendgridRefreshTs = 0;

    public static Config.SendgridConfig getSendgridConfig() {
        int now = Context.now();
        if (sendgridConfig == null || now - lastSendgridRefreshTs > 60) {
            synchronized (Constants.class) {
                if (sendgridConfig == null) {
                    Config config = ConfigsDao.instance.findOne(Filters.eq(ConfigsDao.ID, Config.SendgridConfig.CONFIG_ID));
                    if (config == null) {
                        config = new Config.SendgridConfig();
                        //todo: shivam this change only for mono
                        ((Config.SendgridConfig) config).setSendgridSecretKey(Constants.SENDGRID_TOKEN_KEY);
                    }
                    sendgridConfig = (Config.SendgridConfig) config;
                    lastSendgridRefreshTs = now;
                }
            }
        }
        return sendgridConfig;
    }

    public static void sendTestResults(ObjectId summaryId, Organization organization) {

    }

    private static Config.SlackAlertUsageConfig slackAlertUsageConfig;
    private static int lastSlackAlertUsageRefreshTs = 0;

    public static Config.SlackAlertUsageConfig getSlackAlertUsageConfig() {
        slackAlertUsageConfig = getConfig(slackAlertUsageConfig, lastSlackAlertUsageRefreshTs,
                Config.SlackAlertUsageConfig.CONFIG_ID,
                Config.SlackAlertUsageConfig.class, Config.SlackAlertUsageConfig::new,
                config -> config.setSlackWebhookUrl(Constants.SLACK_ALERT_USAGE_URL));
        return slackAlertUsageConfig;
    }

    private static <T extends Config> T getConfig(T config, int lastRefreshTs, String configId, Class<T> type,
            Supplier<T> defaultConfigSupplier, Consumer<T> additionalConfigSetup) {
        int now = Context.now();
        if (config == null || now - lastRefreshTs > 60) {
            synchronized (Constants.class) {
                if (config == null) {
                    Config fetchedConfig = ConfigsDao.instance.findOne(Filters.eq(ConfigsDao.ID, configId));
                    if (fetchedConfig == null) {
                        fetchedConfig = defaultConfigSupplier.get();
                        additionalConfigSetup.accept(type.cast(fetchedConfig));
                    }
                    if (type.isInstance(fetchedConfig)) {
                        config = type.cast(fetchedConfig);
                    } else {
                        throw new IllegalStateException(
                                "Fetched config is not of the expected type: " + type.getName());
                    }
                    lastRefreshTs = now;
                }
            }
        }
        return config;
    }
}
