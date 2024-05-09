package com.akto.util.http_util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.util.Util;

import inet.ipaddr.IPAddressString;
import okhttp3.Authenticator;
import okhttp3.Credentials;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class CoreHTTPClient {

    public static OkHttpClient client = new OkHttpClient.Builder().build();
    private static final Logger logger = LoggerFactory.getLogger(CoreHTTPClient.class);

    /*
     * The implementation is based on proxy API and not jvm system properties
     * (Ref: https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html) ,
     * which would have been much cleaner.
     * This is due to the implementation of the net.authentication in the okHttp library.
     * Ref: https://github.com/square/okhttp/issues/4248
     */

    public static final TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[] {};
                }
            }
    };
    public static final SSLContext trustAllSslContext;
    static {
        try {
            trustAllSslContext = SSLContext.getInstance("SSL");
            trustAllSslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }
    public static final SSLSocketFactory trustAllSslSocketFactory = trustAllSslContext.getSocketFactory();
    static {
        initialize();
    }

    private static void initialize() {

        String proxyURI = Util.getEnvironmentVariable("PROXY_URI");
        if (proxyURI == null || proxyURI.isEmpty()) {
            return;
        }
        if (!proxyURI.startsWith("http")) {
            proxyURI = "http://" + proxyURI;
        }
        String host = "";
        int port = -1;
        boolean isTLS = false;
        String userInfo = "";
        try {
            URL url = new URL(proxyURI);
            host = url.getHost();
            if (url.getPort() == -1) {
                port = url.getDefaultPort();
            } else {
                port = url.getPort();
            }
            if (StringUtils.contains(url.getProtocol(), "https")) {
                isTLS = true;
            }
            userInfo = url.getUserInfo();
            String infoMessage = String.format(
                    "Found the following PROXY URI: protocol: %s host: %s port: %d userInfo: %s", url.getProtocol(),
                    host, port, userInfo);
            logger.info(infoMessage);
        } catch (Exception e) {
            logger.error("Unable to parse proxy URI" + e.getMessage());
            return;
        }

        String noProxy = Util.getEnvironmentVariable("NO_PROXY");
        boolean matchAllHosts = matchAllHosts(noProxy);

        if (matchAllHosts) {
            logger.info("NO_PROXY matching all hosts. Adding no proxy for http client.");
            return;
        }

        final String finalHost = host;
        final int finalPort = port;

        List<String> noProxyList = parseNoProxy(noProxy);

        final ProxySelector proxySelector = new ProxySelector() {
            @Override
            public java.util.List<Proxy> select(final URI uri) {
                final List<Proxy> proxyList = new ArrayList<Proxy>(1);

                final String host = uri.getHost();

                if (matchHost(noProxyList, host)) {
                    proxyList.add(Proxy.NO_PROXY);
                } else {
                    proxyList.add(new Proxy(Type.HTTP, new InetSocketAddress(finalHost, finalPort)));
                }
                return proxyList;
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                throw new UnsupportedOperationException(
                        "proxy error -> URI: " + uri.toString()
                                + " SockAddr: " + sa.toString()
                                + " Exception: " + ioe);
            }
        };
        String proxyUser = "";
        String proxyPass = "";

        if (userInfo != null && !userInfo.isEmpty()) {
            String[] infos = userInfo.split(":");
            if (infos.length == 2) {
                proxyUser = infos[0];
                proxyPass = infos[1];
            }
        }

        final String finalProxyUser = proxyUser;
        final String finalProxyPass = proxyPass;

        boolean auth = false;

        if (finalProxyUser != null && !finalProxyUser.isEmpty()
                && finalProxyPass != null && !finalProxyPass.isEmpty()) {
            auth = true;
        }

        client = client.newBuilder().proxySelector(proxySelector).build();
        String infoMessage = String.format("Proxy configured for HTTP client with host: %s port: %d", finalHost,
                finalPort);
        logger.info(infoMessage);

        if (isTLS) {
            /*
             * In case of a TLS proxy, 
             * since we do not have the TLS certificate, 
             * we trust all certificates. 
             * The connection is reset without this.
             */
            client = client.newBuilder()
                    .socketFactory(new DelegatingSocketFactory(trustAllSslSocketFactory))
                    .build();
            infoMessage = "TLS enabled on proxy for HTTP client";
            logger.info(infoMessage);
        }

        if (auth) {
            final Authenticator proxyAuthenticator = new Authenticator() {
                @Override
                public Request authenticate(Route route, Response response) throws IOException {
                    String credential = Credentials.basic(finalProxyUser, finalProxyPass);
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                }
            };
            client = client.newBuilder().proxyAuthenticator(proxyAuthenticator).build();
            infoMessage = String.format("Proxy auth configured for HTTP client with user: %s pass: %s", finalProxyUser,
                    finalProxyPass);
            logger.info(infoMessage);
        }

    }

    private static boolean matchAllHosts(String proxy) {
        if (StringUtils.equals("*", proxy)) {
            return true;
        }
        return false;
    }

    private static List<String> parseNoProxy(String noProxyString) {
        List<String> noProxyList = new ArrayList<>();
        if (noProxyString != null && !noProxyString.isEmpty()) {
            String[] entries = noProxyString.split(",");
            for (String entry : entries) {
                noProxyList.add(entry.trim());
            }
        }
        return noProxyList;
    }

    public static boolean matchHost(List<String> hosts, String host) {
        for (String entry : hosts) {
            if (hostMatch(entry, host) || ipContains(entry, host)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hostMatch(String pattern, String match) {
        /*
         * matches .example.com with all subdomains of example.com
         */
        if ((pattern.startsWith(".") && match.endsWith(pattern))
                || pattern.equals(match)) {
            return true;
        }
        return false;
    }

    public static boolean ipContains(String network, String address) {
        try {
            /*
             * matches ipv4 and ipv6 CIDR and subnet ranges.
             */
            IPAddressString one = new IPAddressString(network);
            IPAddressString two = new IPAddressString(address);
            return one.contains(two);
        } catch (Exception e) {
            return false;
        }
    }
}
