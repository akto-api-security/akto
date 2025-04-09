package com.akto;

import com.akto.util.LRUCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import java.io.FileOutputStream;
import org.apache.commons.io.IOUtils;

public class IPLookupClient {
  private final DatabaseReader db;

  public IPLookupClient() throws IOException {
    File dbFile = this.getMaxmindFile();
    this.db = new DatabaseReader.Builder(dbFile).withCache(new LRUCache(2048)).build();
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

  private File getMaxmindFile() throws IOException {
    File maxmindTmpFile = File.createTempFile("tmp-geo-country", ".mmdb");
    maxmindTmpFile.deleteOnExit();

    try (FileOutputStream fos = new FileOutputStream(maxmindTmpFile)) {
      IOUtils.copy(
          IPLookupClient.class.getClassLoader().getResourceAsStream("maxmind/Geo-Country.mmdb"), fos);
    }

    return maxmindTmpFile;
  }
}
