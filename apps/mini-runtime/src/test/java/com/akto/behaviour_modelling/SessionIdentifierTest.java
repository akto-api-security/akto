package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.impl.SessionIdentifier;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class SessionIdentifierTest {

    private final SessionIdentifier identifier = new SessionIdentifier(SessionIdentifier.DEFAULT_SOURCES);

    private static HttpResponseParams record(Map<String, List<String>> headers, String sourceIp) {
        HttpRequestParams req = new HttpRequestParams("GET", "/a", "", headers, "", 1);
        HttpResponseParams resp = new HttpResponseParams("", 200, "", new HashMap<>(), "", req, 0, "0",
                false, HttpResponseParams.Source.OTHER, "", sourceIp);
        resp.setSourceIP(sourceIp);
        return resp;
    }

    private static Map<String, List<String>> headers(String... kv) {
        Map<String, List<String>> h = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) h.put(kv[i], Collections.singletonList(kv[i + 1]));
        return h;
    }

    private static String sha16(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
        return sb.toString();
    }

    private static String jwt(String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + ".c2ln";
    }

    private String id(Map<String, List<String>> headers, String ip) {
        return identifier.extractUserId(record(headers, ip));
    }

    @Test
    public void precedenceAndHashing() throws Exception {
        String token = jwt("{\"sid\":\"jwt-sid-1\",\"sub\":\"u\"}");
        Map<String, List<String>> all = headers(
                "cookie", "a=1; IDENTITYSID=ident-1; coulomb_sess=coul==1",
                "cp-session-token", "cp-tok-1",
                "cp-sso-ims-token", token,
                "x-forwarded-for", "9.9.9.9, 10.0.0.1");

        String coulomb = id(all, "1.1.1.1");
        Assertions.assertEquals(sha16("cookie:coulomb_sess:coul==1"), coulomb);
        Assertions.assertEquals(16, coulomb.length());
        Assertions.assertFalse(coulomb.contains("coul"));

        all.put("cookie", Collections.singletonList("IDENTITYSID=ident-1"));
        Assertions.assertEquals(sha16("cookie:IDENTITYSID:ident-1"), id(all, "1.1.1.1"));

        all.remove("cookie");
        Assertions.assertEquals(sha16("header:cp-session-token:cp-tok-1"), id(all, "1.1.1.1"));

        all.remove("cp-session-token");
        Assertions.assertEquals(sha16("jwt:cp-sso-ims-token:sid:jwt-sid-1"), id(all, "1.1.1.1"));

        all.remove("cp-sso-ims-token");
        Assertions.assertEquals(sha16("xff:9.9.9.9"), id(all, "1.1.1.1"));

        all.remove("x-forwarded-for");
        Assertions.assertEquals(sha16("ip:1.1.1.1"), id(all, "1.1.1.1"));

        Assertions.assertNull(id(new HashMap<>(), null));
    }

    @Test
    public void headerNamesCaseInsensitive() throws Exception {
        Assertions.assertEquals(sha16("cookie:coulomb_sess:abc"),
                id(headers("Cookie", "coulomb_sess=abc"), "1.1.1.1"));
        Assertions.assertEquals(sha16("header:cp-session-token:t"),
                id(headers("CP-Session-Token", "t"), "1.1.1.1"));
        Assertions.assertEquals(sha16("xff:9.9.9.9"),
                id(headers("X-Forwarded-For", "9.9.9.9"), "1.1.1.1"));
    }

    @Test
    public void jwtWithoutSidOrMalformedFallsThrough() throws Exception {
        Assertions.assertEquals(sha16("xff:8.8.8.8"),
                id(headers("cp-sso-ims-token", jwt("{\"sub\":\"u\"}"), "x-forwarded-for", "8.8.8.8"), "1.1.1.1"));
        Assertions.assertEquals(sha16("ip:1.1.1.1"),
                id(headers("cp-sso-ims-token", "not-a-jwt"), "1.1.1.1"));
        Assertions.assertEquals(sha16("jwt:cp-sso-ims-token:sid:s1"),
                id(headers("cp-sso-ims-token", "Bearer " + jwt("{\"sid\":\"s1\"}")), "1.1.1.1"));
    }

    @Test
    public void sameSessionAcrossIpsDifferentSessionsSeparate() {
        Assertions.assertEquals(id(headers("cookie", "coulomb_sess=s1"), "1.1.1.1"),
                id(headers("cookie", "coulomb_sess=s1"), "2.2.2.2"));
        Assertions.assertNotEquals(id(headers("cookie", "coulomb_sess=s1"), "1.1.1.1"),
                id(headers("cookie", "coulomb_sess=s2"), "1.1.1.1"));
    }

    @Test
    public void customSourceOrder() throws Exception {
        SessionIdentifier ipFirst = new SessionIdentifier("ip,cookie:coulomb_sess");
        Assertions.assertEquals(sha16("ip:1.1.1.1"),
                ipFirst.extractUserId(record(headers("cookie", "coulomb_sess=s1"), "1.1.1.1")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new SessionIdentifier("bogus"));
    }
}
