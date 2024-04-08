package com.akto.onprem;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.util.DashboardMode;
import com.mongodb.client.model.Filters;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

public class Constants {
    public static final DashboardMode DEFAULT_DASHBOARD_MODE = DashboardMode.LOCAL_DEPLOY;
    public static final String SENDGRID_TOKEN_KEY = "";

    public static final String INTERCOM_HASH_KEY = "";

    private static final String JWT_PRIVATE_KEY =  "";

    private final static String JWT_PUBLIC_KEY = "";//new String(keyBytes);


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
}
