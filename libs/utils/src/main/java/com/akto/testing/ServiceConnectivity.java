package com.akto.testing;

import com.akto.log.LoggerMaker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

public class ServiceConnectivity {
    private static final LoggerMaker logger = new LoggerMaker(ServiceConnectivity.class, LoggerMaker.LogDb.DASHBOARD);
    public static boolean check(String targetURL, String urlParameters) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(targetURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            connection.setRequestProperty("Content-Length", Integer.toString(urlParameters.getBytes().length));
            connection.setRequestProperty("Content-Language", "en-US");

            connection.setUseCaches(false);
            connection.setDoOutput(true);

            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(urlParameters.getBytes());
            outputStream.close();

            int responseCode = connection.getResponseCode();
            if(responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader inputReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = inputReader.readLine()) != null) {
                    response.append(inputLine);
                }
                inputReader.close();

                logger.infoAndAddToDb("Service is reachable, url: " + targetURL);
            } else {
                logger.infoAndAddToDb("Failed to get a 2XX response code. Response Code: " + responseCode +" url: " + targetURL);
            }
            return true;
        } catch (java.net.SocketTimeoutException e) {
            logger.errorAndAddToDb("Connection/Read Timeout: Unable to connect to the service, url: " + targetURL);
            return false;
        } catch (Exception e) {
            logger.infoAndAddToDb("Exception: Unable to connect to the service, url: " + targetURL + " " + e.getMessage());
            return true;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
