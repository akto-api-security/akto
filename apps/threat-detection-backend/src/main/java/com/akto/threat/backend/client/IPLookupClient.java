package com.akto.threat.backend.client;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;

public class IPLookupClient {
  private final DatabaseReader db;

  public IPLookupClient(File dbFile) throws IOException {
    this.db = new DatabaseReader.Builder(dbFile).build();
  }

  public Optional<String> getCountryISOCodeGivenIp(String ip) {
    try {
      InetAddress ipAddr = InetAddress.getByName(ip);
      CountryResponse resp = db.country(ipAddr);
      return Optional.of(resp.getCountry().getIsoCode());
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
