package com.akto.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    public static String unzipString(String zippedBase64Str) {
        if (zippedBase64Str == null || zippedBase64Str.isEmpty()) {
            return zippedBase64Str;
        }

        byte[] decodedBytes = Base64.getDecoder().decode(zippedBase64Str);

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(decodedBytes);
             GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, len);
            }
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Failed to unzip content", e);
        }
    }
}
