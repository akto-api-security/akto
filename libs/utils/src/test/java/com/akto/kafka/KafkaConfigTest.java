package com.akto.kafka;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class KafkaConfigTest {

    @Test
    public void testMskIamProperties() {
        Properties props = new Properties();
        KafkaConfig.addMskIamAuthenticationProperties(props);

        assertEquals("SASL_SSL", props.get(KafkaConfig.SECURITY_PROTOCOL));
        assertEquals("AWS_MSK_IAM", props.get(KafkaConfig.SASL_MECHANISM));
        assertEquals("software.amazon.msk.auth.iam.IAMClientCallbackHandler",
                props.get(KafkaConfig.SASL_CLIENT_CALLBACK_HANDLER_CLASS));
        assertEquals("software.amazon.msk.auth.iam.IAMLoginModule required;", props.get(KafkaConfig.SASL_JAAS_CONFIG));
        assertEquals(1000, props.get("reconnect.backoff.ms"));
        // MSK certificates are publicly trusted, so hostname verification must not be disabled
        assertNull(props.get("ssl.endpoint.identification.algorithm"));
    }

    @Test
    public void testMskIamKeepsExplicitReconnectBackoff() {
        Properties props = new Properties();
        props.put("reconnect.backoff.ms", 5000);
        KafkaConfig.addMskIamAuthenticationProperties(props);

        assertEquals(5000, props.get("reconnect.backoff.ms"));
    }

    @Test
    public void testUsernamePasswordAuthUnchanged() {
        // AKTO_KAFKA_SASL_MECHANISM is unset in tests, so the default PLAIN mechanism is used
        Properties props = new Properties();
        KafkaConfig.addAuthenticationProperties(props, "user", "pass", "SASL_SSL");

        assertEquals("SASL_SSL", props.get(KafkaConfig.SECURITY_PROTOCOL));
        assertEquals("PLAIN", props.get(KafkaConfig.SASL_MECHANISM));
        assertEquals("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"user\" password=\"pass\";",
                props.get(KafkaConfig.SASL_JAAS_CONFIG));
        assertEquals("", props.get("ssl.endpoint.identification.algorithm"));
        assertNull(props.get(KafkaConfig.SASL_CLIENT_CALLBACK_HANDLER_CLASS));
    }

    @Test
    public void testMissingCredentialsStillRejectedWithoutMskIam() {
        Properties props = new Properties();
        assertFalse(KafkaConfig.addValidatedAuthenticationProperties(props, null, null));
        assertFalse(KafkaConfig.isMskIamAuthentication());
    }
}
