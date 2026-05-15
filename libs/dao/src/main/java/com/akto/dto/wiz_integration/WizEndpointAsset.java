package com.akto.dto.wiz_integration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WizEndpointAsset {
    
    public static final String HOST = "host";
    private String host;

    public static final String PROTOCOL = "protocol";
    private String protocol;

    public static final String PORT = "port";
    private int port;
    
    public String toString() {
        return String.format("%s://%s:%d", protocol, host, port);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WizEndpointAsset)) return false;
        WizEndpointAsset that = (WizEndpointAsset) o;
        return port == that.port &&
                Objects.equals(host, that.host) &&
                Objects.equals(protocol, that.protocol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, protocol, port);
    }
}
