package com.akto.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public class GzipUtils {

    public static String zipString(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(input.getBytes(StandardCharsets.UTF_8));
            gzipOutputStream.close();
            return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to zip content", e);
        }
    }


    public static String unzipString(String base64String) {
        if(base64String == null || base64String.isEmpty()){
            return base64String;
        }
        String safeString = base64String.replace('-', '+').replace('_', '/');
        safeString = safeString.replaceAll("\\s", "");

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(safeString);
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(decodedBytes);
            GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buffer)) > 0) {
                byteArrayOutputStream.write(buffer, 0, len);
            }
            String decompressedString = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
            return decompressedString;
        } catch (Exception e) {
            System.err.println("Error decompressing and decoding Base64 string: " + e.getMessage());
        }
        return null;
    }

}
