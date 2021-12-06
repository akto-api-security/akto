package com.akto.notifications.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class ImageFetcher {
    public static void saveImage(String imageUrl, String destinationFile) throws IOException {

        InputStream is;
        if (imageUrl.startsWith("file://")) {
            is = new FileInputStream(imageUrl.substring(7, imageUrl.length()));
        } else {
            URL url = new URL(imageUrl);
            HttpURLConnection httpcon = (HttpURLConnection) url.openConnection();
            httpcon.addRequestProperty("User-Agent", "Mozilla/4.76");
            is = httpcon.getInputStream();
        }

        OutputStream os = new FileOutputStream(destinationFile);

        byte[] b = new byte[2048];
        int length;

        while ((length = is.read(b)) != -1) {
            os.write(b, 0, length);
        }

        is.close();
        os.close();
    }
}
