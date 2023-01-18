package com.akto.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Intercom {
	
	private static final Logger logger = LoggerFactory.getLogger(Intercom.class);
	
	public static String getUserHash(String email) {
		try {
			String secret = "DPjF2tyEf4BvVejL3DntlxRwIEfHC_1ri9ziFmhb";
			Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
			sha256_HMAC.init(secret_key);

			byte[] hash = (sha256_HMAC.doFinal(email.getBytes()));
			StringBuffer result = new StringBuffer();
			for (byte b : hash) {
				result.append(String.format("%02x", b));
			}

			logger.info("Hash {}", result.toString());
			return result.toString();
		}
		catch (Exception e){
			logger.error("Failed to get hash for user {}", email, e);
		}
		return "";
	}
}
