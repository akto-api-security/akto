package com.akto.dto.api_threat_detection;

public class IpGeoLocationResp {
    private String country;
    private double lat;
    private double lon;

    public IpGeoLocationResp() {
    }

    public IpGeoLocationResp(String country, double lat, double lon) {
        this.country = country;
        this.lat = lat;
        this.lon = lon;
    }

    public String getCountry() {
        return country;
    }
    
    public void setCountry(String country) {
        this.country = country;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

}
