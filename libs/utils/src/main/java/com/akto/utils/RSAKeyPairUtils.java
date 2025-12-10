package com.akto.utils;

import java.util.Base64;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;

import static com.akto.dto.Config.CONFIG_SALT;

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

    /**
     * Fetches RSA key pair from database and returns them as byte arrays.
     * Returns null if keys are not found or if an error occurs.
     * This method is called from Constants static block.
     *
     * @return byte[][] array where [0] is private key and [1] is public key, or null if not found
     */
    public static byte[][] fetchKeysFromDb() {
        logger.info("Fetching RSA keys from db");

        try {
            Config.RSAKeyPairConfig rsaKeyPairConfig = (Config.RSAKeyPairConfig) ConfigsDao.instance.findOne(Constants.ID, Config.ConfigType.RSA_KP.name() + CONFIG_SALT);

            if (rsaKeyPairConfig != null) {
                logger.info("Found RSA keys config in db");

                String pemPrivateKey = rsaKeyPairConfig.getPrivateKey();
                String pemPublicKey = rsaKeyPairConfig.getPublicKey();

                if (pemPrivateKey == null || pemPublicKey == null) {
                    logger.error("RSA keys not present in config");
                    return null;
                }

                byte[] privateKey = RSAKeyPairUtils.parsePEMPrivateKey(pemPrivateKey);
                byte[] publicKey = RSAKeyPairUtils.parsePEMPublicKey(pemPublicKey);

                logger.info("Successfully fetched RSA keys from db");
                return new byte[][] { privateKey, publicKey };
            } else {
                logger.info("RSA keys config not found in db");
            }
        } catch (Exception e) {
            logger.error("Error while fetching RSA keys from db", e);
        }

        return null;
    }
}
