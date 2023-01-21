package com.akto.utils;

import java.util.Objects;

public class HttpUtils {

	private static final String https = System.getenv("AKTO_HTTPS_FLAG");

	public static boolean isHttpsEnabled(){
		return Objects.equals(https, "true");
	}
	
}
