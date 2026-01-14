package com.akto.fast_discovery;

/**
 * Parsed representation of Kafka header "collection_details".
 * Format: "host|method|url"
 */
public class CollectionDetails {
    private final String host;
    private final String method;
    private final String url;

    private CollectionDetails(String host, String method, String url) {
        this.host = host;
        this.method = method;
        this.url = url;
    }

    public static CollectionDetails parse(String headerValue) {
        if (headerValue == null || headerValue.isEmpty()) {
            throw new IllegalArgumentException("Header value is null or empty");
        }

        String[] parts = headerValue.split("\\|", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                String.format("Invalid format: expected 'host|method|url', got '%s' (%d parts)",
                    headerValue, parts.length)
            );
        }

        String host = parts[0].trim();
        String method = parts[1].trim();
        String url = parts[2].trim();

        if (host.isEmpty() || method.isEmpty() || url.isEmpty()) {
            throw new IllegalArgumentException("Empty field in header: " + headerValue);
        }

        return new CollectionDetails(host, method, url);
    }

    public String getHost() {
        return host;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return String.format("CollectionDetails{host='%s', method='%s', url='%s'}",
            host, method, url);
    }
}
