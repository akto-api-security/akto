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
            Config.RSAKeyPairConfig rsaKeyPairConfig = (Config.RSAKeyPairConfig) ConfigsDao.instance.findOne("_id", Config.ConfigType.RSA_KP.name());

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

    /**
     * Ensures RSA keys are persisted in the database.
     * If keys don't exist in DB, this method will fetch current keys from Constants
     * and save them to the database.
     * This should be called from InitializerListener after DB connection is established.
     */
    public static void initializeKeysFromDb() {
        logger.info("Initializing RSA keys in db");

        try {
            Config.RSAKeyPairConfig rsaKeyPairConfig = (Config.RSAKeyPairConfig) ConfigsDao.instance.findOne("_id", Config.ConfigType.RSA_KP.name());

            if (rsaKeyPairConfig == null) {
                logger.info("RSA keys config not found in db, creating new keys");

                // Get current keys from Constants (which were generated in static block)
                byte[] privateKeyBytes = Constants.getPrivateKey();
                byte[] publicKeyBytes = Constants.getPublicKey();

                if (privateKeyBytes != null && publicKeyBytes != null) {
                    // Convert byte arrays to PEM format
                    String pemPrivateKey = "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getEncoder().encodeToString(privateKeyBytes)
                        + "\n-----END PRIVATE KEY-----";

                    String pemPublicKey = "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getEncoder().encodeToString(publicKeyBytes)
                        + "\n-----END PUBLIC KEY-----";

                    // Create and insert new config
                    rsaKeyPairConfig = new Config.RSAKeyPairConfig(pemPrivateKey, pemPublicKey);
                    ConfigsDao.instance.insertOne(rsaKeyPairConfig);

                    logger.info("Successfully created and saved new RSA keys to db");
                } else {
                    logger.error("Cannot save keys to db: keys are null in Constants");
                }
            } else {
                logger.info("RSA keys already exist in db");
            }
        } catch (Exception e) {
            logger.error("Error while initializing RSA keys in db", e);
        }
    }
}
