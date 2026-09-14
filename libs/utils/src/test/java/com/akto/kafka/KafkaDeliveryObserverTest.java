package com.akto.kafka;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class KafkaDeliveryObserverTest {
    private MockProducer<String, String> producer() {
        return new MockProducer<>(false, new StringSerializer(), new StringSerializer());
    }

    @Test public void reportsAsyncDeliveryFailure() {
        MockProducer<String, String> producer = producer();
        Kafka kafka = new Kafka(producer, true);
        AtomicReference<Exception> observed = new AtomicReference<>();
        kafka.send("payload", "account-topic", (metadata, error) -> observed.set(error));
        assertNull(observed.get());
        RuntimeException failure = new RuntimeException("broker unavailable");
        producer.errorNext(failure);
        assertSame(failure, observed.get());
    }

    @Test public void reportsNotReadyAndSynchronousFailure() {
        MockProducer<String, String> producer = producer();
        AtomicReference<Exception> observed = new AtomicReference<>();
        new Kafka(producer, false).send("payload", "topic", (metadata, error) -> observed.set(error));
        assertTrue(observed.get() instanceof IllegalStateException);
        producer.close();
        try {
            new Kafka(producer, true).send("payload", "topic", (metadata, error) -> observed.set(error));
            fail("Existing synchronous failure must still propagate");
        } catch (IllegalStateException expected) {
            assertSame(expected, observed.get());
        }
    }

    @Test public void successAndBrokenObserversDoNotAffectSending() {
        MockProducer<String, String> producer = producer();
        Kafka kafka = new Kafka(producer, true);
        kafka.send("payload", "topic", (metadata, error) -> {
            assertNull(error);
            assertNotNull(metadata);
            throw new IllegalStateException("alert sink failed");
        });
        assertTrue(producer.completeNext());
    }
}
