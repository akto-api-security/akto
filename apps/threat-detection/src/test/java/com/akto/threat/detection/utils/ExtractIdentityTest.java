package com.akto.threat.detection.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApiMetadata;
import com.akto.dto.api_protection_parse_layer.Condition.DistinctIdentifier;
import com.akto.dto.api_protection_parse_layer.Condition.ValueSource;

public class ExtractIdentityTest {

    private HttpResponseParams buildParams() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-user-id", Collections.singletonList("u-42"));

        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setHeaders(headers);
        requestParams.setPayload("{\"user\":{\"email\":\"a@b.com\"}}");

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(requestParams);
        responseParams.setPayload("{\"sessionId\":\"s-1\"}");
        return responseParams;
    }

    @Test
    void extractsFromEachSource() {
        HttpResponseParams params = buildParams();

        assertThat(Utils.extractIdentity(params, new ValueSource("request_headers", "x-user-id"))).isEqualTo("u-42");
        assertThat(Utils.extractIdentity(params, new ValueSource("request_payload", "email"))).isEqualTo("a@b.com");
        assertThat(Utils.extractIdentity(params, new ValueSource("response_payload", "sessionId"))).isEqualTo("s-1");
    }

    @Test
    void returnsNullWhenIdentityMissing() {
        HttpResponseParams params = buildParams();

        assertThat(Utils.extractIdentity(params, new ValueSource("request_headers", "x-missing"))).isNull();
        assertThat(Utils.extractIdentity(params, new ValueSource("request_payload", "missing"))).isNull();
        assertThat(Utils.extractIdentity(params, new ValueSource("unknown", "x"))).isNull();
        assertThat(Utils.extractIdentity(params, new ValueSource("request_headers", null))).isNull();
        assertThat(Utils.extractIdentity(params, null)).isNull();
    }

    @Test
    void distinctValueUsesCountryAttributeOrFallsBackToSource() {
        HttpResponseParams params = buildParams();
        RawApiMetadata metadata = new RawApiMetadata("DE");

        DistinctIdentifier country = new DistinctIdentifier();
        country.setAttribute("country_code");
        assertThat(Utils.extractDistinctValue(params, metadata, country)).isEqualTo("DE");
        assertThat(Utils.extractDistinctValue(params, null, country)).isNull();

        DistinctIdentifier email = new DistinctIdentifier(5, "request_payload", "email");
        assertThat(Utils.extractDistinctValue(params, metadata, email)).isEqualTo("a@b.com");
    }
}
