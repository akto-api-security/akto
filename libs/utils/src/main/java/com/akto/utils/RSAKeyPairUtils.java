package com.akto.utils;

import java.util.Base64;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.onprem.Constants;

public class RSAKeyPairUtils {

    private static final LoggerMaker logger = new LoggerMaker(RSAKeyPairUtils.class, LogDb.DASHBOARD);

    public static byte[] parsePEMPrivateKey(String key) {
        String pemKey = key;
        byte[] decodedKey;

        pemKey = pemKey.replace("-----BEGIN PRIVATE KEY-----","");
        pemKey = pemKey.replace("-----END PRIVATE KEY-----","");
        pemKey = pemKey.replace("\n","");

        decodedKey = Base64.getDecoder().decode(pemKey);

        return decodedKey;
    }

    public static byte[] parsePEMPublicKey(String key) {
        String pemKey = key;
        byte[] decodedKey;

        pemKey = pemKey.replace("-----BEGIN PUBLIC KEY-----","");
        pemKey = pemKey.replace("-----END PUBLIC KEY-----","");
        pemKey = pemKey.replace("\n","");  

        decodedKey = Base64.getDecoder().decode(pemKey);
    
        return decodedKey;
    }

    public static void initializeKeysFromDb() {
        logger.info("Fetching RSA keys from db");

        try {
            Config.RSAKeyPairConfig rsaKeyPairConfig = (Config.RSAKeyPairConfig) ConfigsDao.instance.findOne("_id", Config.ConfigType.RSA_KP.name());

            if (rsaKeyPairConfig != null) {
                logger.info("Found RSA keys config in db");

                String pemPrivateKey = rsaKeyPairConfig.getPrivateKey();
                String pemPublicKey = rsaKeyPairConfig.getPublicKey();

                if (pemPrivateKey == null || pemPublicKey == null) {
                    logger.error("RSA keys not present in config");
                    return;
                }

                byte[] privateKey = RSAKeyPairUtils.parsePEMPrivateKey(pemPrivateKey);
                byte[] publicKey = RSAKeyPairUtils.parsePEMPublicKey(pemPublicKey);

                Constants.setPrivateKey(privateKey);
                Constants.setPublicKey(publicKey);

                logger.info("Successfully set RSA keys in Constants");
            } else {
                logger.error("RSA keys config not found in db");
            }
        } catch (Exception e) {
            logger.error("Error while fetching RSA keys from db", e);
        }
    }
}
