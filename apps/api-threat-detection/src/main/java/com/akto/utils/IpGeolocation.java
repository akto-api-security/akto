package com.akto.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.file.Paths;

import com.akto.log.LoggerMaker;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;

public class IpGeolocation {

    // todo: modify file path
    private static File database = new File("/Users/admin/Downloads/GeoLite2-Country_20241119/GeoLite2-Country.mmdb");
    private static DatabaseReader reader;
    private static final LoggerMaker loggerMaker = new LoggerMaker(IpGeolocation.class);

    public IpGeolocation() {
        try {
            reader = new DatabaseReader.Builder(database).build();   
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("IpGeolocation init failed " + e.getMessage());
        }
    }

    public String fetchLocationByIp(String ip) throws Exception {
        if (reader == null) {
            throw new Exception("IpGeolocation not init properly, unable to fetch location");
        }
        InetAddress ipAddress = InetAddress.getByName(ip);

        CountryResponse response = reader.country(ipAddress);
        String country = response.getCountry().getName();
        return country;
    }

}
