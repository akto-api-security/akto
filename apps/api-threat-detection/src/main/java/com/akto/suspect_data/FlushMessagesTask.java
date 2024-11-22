package com.akto.suspect_data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.akto.proto.threat_protection.consumer_service.v1.ConsumerServiceGrpc;
import com.akto.proto.threat_protection.consumer_service.v1.ConsumerServiceGrpc.ConsumerServiceStub;
import com.akto.proto.threat_protection.consumer_service.v1.MaliciousEvent;
import com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest;
import com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import com.akto.dao.context.Context;
import com.akto.runtime.utils.Utils;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

public class FlushMessagesTask {

  private static final ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();
  private final Consumer<String, String> consumer;
  private final ConsumerServiceStub asyncStub;

  private FlushMessagesTask() {
    String kafkaBrokerUrl = System.getenv("AKTO_KAFKA_BROKER_URL");
    String groupId = "akto-flush-malicious-messages";

    Properties properties = Utils.configProperties(kafkaBrokerUrl, groupId, 100);
    this.consumer = new KafkaConsumer<>(properties);

    String target = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_URL");
    // TODO: Secure this connection
    ManagedChannel channel =
        Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();

    this.asyncStub = ConsumerServiceGrpc.newStub(channel);
  }

  public static FlushMessagesTask instance = new FlushMessagesTask();

  public void init() {
    consumer.subscribe(Collections.singletonList("akto.malicious"));
    pollingExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            while (true) {
              try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                processRecords(records);
              } catch (Exception e) {
                e.printStackTrace();
                consumer.close();
              }
            }
          }
        });
  }

  public void processRecords(ConsumerRecords<String, String> records) {
    Map<String, List<MaliciousEvent>> accWiseMessages = new HashMap<>();
    for (ConsumerRecord<String, String> record : records) {
      try {
        MaliciousEvent.Builder builder = MaliciousEvent.newBuilder();
        JsonFormat.parser().merge(record.value(), builder);
        MaliciousEvent event = builder.build();
        accWiseMessages.computeIfAbsent(record.key(), k -> new ArrayList<>()).add(event);
      } catch (InvalidProtocolBufferException e) {
        e.printStackTrace();
      }
    }

    for (Map.Entry<String, List<MaliciousEvent>> entry : accWiseMessages.entrySet()) {
      int accountId = Integer.parseInt(entry.getKey());
      List<MaliciousEvent> events = entry.getValue();
      Context.accountId.set(accountId);

      this.asyncStub.saveMaliciousEvent(
          SaveMaliciousEventRequest.newBuilder().addAllEvents(events).build(),
          new StreamObserver<SaveMaliciousEventResponse>() {
            @Override
            public void onNext(SaveMaliciousEventResponse value) {
              // Do nothing
            }

            @Override
            public void onError(Throwable t) {
              t.printStackTrace();
            }

            @Override
            public void onCompleted() {
              // Do nothing
              System.out.println(
                  String.format(
                      "Saved malicious events for account: %d. Saved event counts: %d",
                      accountId, events.size()));
            }
          });
    }
  }
}
