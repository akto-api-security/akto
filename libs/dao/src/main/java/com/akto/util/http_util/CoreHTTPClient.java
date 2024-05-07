package com.akto.util.http_util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    static {
        initialize();
    }

    private static void initialize() {

        String proxyURI = System.getenv("PROXY_URI");
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

        String noProxy = System.getenv("NO_PROXY");
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
            client = client.newBuilder()
                    .socketFactory(new DelegatingSocketFactory(SSLSocketFactory.getDefault()))
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
            client = client.newBuilder().authenticator(proxyAuthenticator).build();
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

    private static boolean matchHost(List<String> hosts, String host) {
        for (String entry : hosts) {
            /*
             * This matches .example.com with all subdomains of example.com
             */
            if (host.endsWith(entry)) {
                return true;
            }
            // TODO: match for subnet range as well.
        }
        return false;
    }

}
