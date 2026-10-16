package com.akto.kafka;

import com.akto.detection.ClassificationWorker;
import com.akto.detection.DetectionCorrector;
import com.akto.detection.KafkaCandidatePublisher;
import com.akto.detection.ParamVerdictCache;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    // Detection corrector clients apply the env Kafka auth; with auth disabled they are built as before.
    // Neither connects: producer/consumer construction is lazy and the worker is stopped before it polls.
    @Test
    public void testDetectionCorrectorPublisherBuildsWithEnvAuth() {
        new KafkaCandidatePublisher("localhost:1", "akto.detection.candidates").close();
    }

    @Test
    public void testDetectionCorrectorWorkerBuildsWithEnvAuth() {
        ClassificationWorker worker = new ClassificationWorker("localhost:1", "akto.detection.candidates",
                "test-group", DetectionCorrector.NO_OP, new ParamVerdictCache(1, 1));
        worker.stop();
        worker.run();
    }
}
