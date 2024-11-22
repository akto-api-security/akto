package com.akto.utils;

import java.io.File;
import java.net.InetAddress;

import com.akto.log.LoggerMaker;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;

public class IpGeolocation {

    // todo: modify file path
    private static File database = new File("/Users/admin/Downloads/GeoLite2-Country_20241119/GeoLite2-Country.mmdb");
    private static DatabaseReader reader;
    private static final LoggerMaker loggerMaker = new LoggerMaker(IpGeolocation.class);

    public IpGeolocation() throws Exception {
        reader = new DatabaseReader.Builder(database).build();
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
