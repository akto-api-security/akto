package com.akto.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class VersionUtil {

        private VersionUtil() {}

        public static String getVersion(InputStream in) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
        String imageTag = bufferedReader.readLine();
        String buildTime = bufferedReader.readLine();
        String aktoVersion = bufferedReader.readLine();
        return imageTag + " - " + buildTime + " - " + aktoVersion;
    }

}
