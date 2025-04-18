package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.onprem.Constants;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


public class Intercom {
	
	private static final LoggerMaker logger = new LoggerMaker(Intercom.class, LogDb.DASHBOARD);;
	
	public static String getUserHash(String email) {
		try {
			String secret = Constants.INTERCOM_HASH_KEY;
			Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
			sha256_HMAC.init(secret_key);

			byte[] hash = (sha256_HMAC.doFinal(email.getBytes()));
			StringBuffer result = new StringBuffer();
			for (byte b : hash) {
				result.append(String.format("%02x", b));
			}

			logger.debug("Hash {}", result.toString());
			return result.toString();
		}
		catch (Exception e){
			logger.error("Failed to get hash for user {}", email, e);
		}
		return "";
	}
}
