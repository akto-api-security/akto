package com.akto;

import com.akto.log.LoggerMaker;
import com.akto.util.LRUCache;
import com.maxmind.db.Reader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;

public class IpAnonymizerLookup {

  private static final LoggerMaker logger = new LoggerMaker(IpAnonymizerLookup.class);
  private static final String DB_RESOURCE = "maxmind/Merged-IP.mmdb";
  private static final int RESULT_CACHE_SIZE = 10_000;
  private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
  private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");

  private static volatile IpAnonymizerLookup instance;

  public interface RecordSource {
    Map<?, ?> get(InetAddress address) throws IOException;
  }

  public static class Info {
    private final boolean vpn;
    private final boolean tor;
    private final boolean hosting;
    private final String asnOrg;

    public Info(boolean vpn, boolean tor, boolean hosting, String asnOrg) {
      this.vpn = vpn;
      this.tor = tor;
      this.hosting = hosting;
      this.asnOrg = asnOrg;
    }

    public boolean isVpn() {
      return vpn;
    }

    public boolean isTor() {
      return tor;
    }

    public boolean isHosting() {
      return hosting;
    }

    public String getAsnOrg() {
      return asnOrg;
    }

    public boolean isVpnOrTor() {
      return vpn || tor;
    }
  }

  private final RecordSource source;
  private final Map<String, Optional<Info>> resultCache =
      Collections.synchronizedMap(
          new LinkedHashMap<String, Optional<Info>>(RESULT_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Optional<Info>> eldest) {
              return size() > RESULT_CACHE_SIZE;
            }
          });

  public IpAnonymizerLookup(RecordSource source) {
    this.source = source;
  }

  public static IpAnonymizerLookup getInstance() {
    if (instance == null) {
      synchronized (IpAnonymizerLookup.class) {
        if (instance == null) {
          instance = new IpAnonymizerLookup(openDefaultSource());
        }
      }
    }
    return instance;
  }

  public Optional<Info> lookup(String ip) {
    if (source == null || ip == null || !isIpLiteral(ip)) {
      return Optional.empty();
    }
    Optional<Info> cached = resultCache.get(ip);
    if (cached != null) {
      return cached;
    }
    Optional<Info> result;
    try {
      Map<?, ?> record = source.get(InetAddress.getByName(ip));
      result = record == null ? Optional.<Info>empty() : Optional.of(fromRecord(record));
    } catch (Exception e) {
      result = Optional.empty();
    }
    resultCache.put(ip, result);
    return result;
  }

  public static Info fromRecord(Map<?, ?> record) {
    Object proxy = record.get("proxy");
    boolean vpn = false;
    boolean tor = false;
    boolean hosting = false;
    if (proxy instanceof Map) {
      Map<?, ?> flags = (Map<?, ?>) proxy;
      vpn = Boolean.TRUE.equals(flags.get("is_vpn"));
      tor = Boolean.TRUE.equals(flags.get("is_tor"));
      hosting = Boolean.TRUE.equals(flags.get("is_hosting"));
    }
    Object asn = record.get("asn");
    String asnOrg = null;
    if (asn instanceof Map) {
      Object org = ((Map<?, ?>) asn).get("autonomous_system_organization");
      asnOrg = org == null ? null : org.toString();
    }
    return new Info(vpn, tor, hosting, asnOrg);
  }

  private static boolean isIpLiteral(String ip) {
    return IPV4.matcher(ip).matches() || IPV6.matcher(ip).matches();
  }

  private static RecordSource openDefaultSource() {
    try {
      File dbFile = resolveDbFile();
      if (dbFile == null) {
        logger.warn("Merged-IP.mmdb not found on classpath, VPN/Tor detection is disabled");
        return null;
      }
      final Reader reader = new Reader(dbFile, new LRUCache(2048));
      return new RecordSource() {
        @Override
        public Map<?, ?> get(InetAddress address) throws IOException {
          return reader.get(address, Map.class);
        }
      };
    } catch (Exception e) {
      logger.error("Error opening Merged-IP.mmdb, VPN/Tor detection is disabled: " + e.getMessage());
      return null;
    }
  }

  private static File resolveDbFile() throws Exception {
    URL url = IpAnonymizerLookup.class.getClassLoader().getResource(DB_RESOURCE);
    if (url == null) {
      return null;
    }
    if ("file".equals(url.getProtocol())) {
      return new File(url.toURI());
    }
    File tmp = File.createTempFile("tmp-merged-ip", ".mmdb");
    tmp.deleteOnExit();
    try (InputStream in = url.openStream();
        FileOutputStream out = new FileOutputStream(tmp)) {
      IOUtils.copy(in, out);
    }
    return tmp;
  }
}
