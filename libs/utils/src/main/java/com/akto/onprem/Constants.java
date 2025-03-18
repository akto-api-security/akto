package com.akto.onprem;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.util.DashboardMode;
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

    private static final byte[] privateKey, publicKey;

    static {
        KeyPairGenerator kpg;
        KeyPair kp = null;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            kp = kpg.generateKeyPair();

        } catch (NoSuchAlgorithmException e) {
            ;
        }

        privateKey = kp == null ? null : kp.getPrivate().getEncoded();
        publicKey = kp == null ? null : kp.getPublic().getEncoded();
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
    private static Config.SlackAlertCyborgConfig slackAlertCyborgConfig;
    private static int lastSlackAlertCyborgRefreshTs = 0;

    public static Config.SlackAlertUsageConfig getSlackAlertUsageConfig() {
        slackAlertUsageConfig = getConfig(slackAlertUsageConfig, lastSlackAlertUsageRefreshTs,
                Config.SlackAlertUsageConfig.CONFIG_ID,
                Config.SlackAlertUsageConfig.class, Config.SlackAlertUsageConfig::new,
                config -> config.setSlackWebhookUrl(Constants.SLACK_ALERT_USAGE_URL));
        return slackAlertUsageConfig;
    }

    public static Config.SlackAlertCyborgConfig getSlackAlertCyborgConfig() {
        slackAlertCyborgConfig = getConfig(slackAlertCyborgConfig, lastSlackAlertCyborgRefreshTs,
                Config.SlackAlertCyborgConfig.CONFIG_ID,
                Config.SlackAlertCyborgConfig.class, Config.SlackAlertCyborgConfig::new,
                config -> config.setSlackWebhookUrl(""));
        return slackAlertCyborgConfig;
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
