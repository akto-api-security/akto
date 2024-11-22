package com.akto.utils;

import java.io.File;
import java.net.InetAddress;

import com.akto.dto.api_threat_detection.IpGeoLocationResp;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;

public class IpGeolocation {

    // todo: modify file path
    private static File database = new File("/Users/admin/Downloads/GeoLite2-City_20241119/GeoLite2-City.mmdb");
    private static DatabaseReader reader;

    public IpGeolocation() throws Exception {
        reader = new DatabaseReader.Builder(database).build();
    }

    public IpGeoLocationResp fetchLocationByIp(String ip) throws Exception {
        if (reader == null) {
            throw new Exception("IpGeolocation not init properly, unable to fetch location");
        }
        InetAddress ipAddress = InetAddress.getByName(ip);

        CityResponse response = reader.city(ipAddress);
        String country = response.getCountry().getName();
        IpGeoLocationResp resp = new IpGeoLocationResp(country, response.getLocation().getLatitude(), response.getLocation().getLongitude());
        return resp;
    }

}
