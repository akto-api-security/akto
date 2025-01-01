// Generated by the protocol buffer compiler.  DO NOT EDIT!
// NO CHECKED-IN PROTOBUF GENCODE
// source: threat_protection/message/malicious_event/dashboard/v1/message.proto
// Protobuf Java Version: 4.28.3

package com.akto.proto.threat_protection.message.malicious_event.dashboard.v1;

public interface DashboardMaliciousEventMessageOrBuilder extends
    // @@protoc_insertion_point(interface_extends:threat_protection.message.malicious_event.dashboard.v1.DashboardMaliciousEventMessage)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string id = 1 [json_name = "id"];</code>
   * @return The id.
   */
  java.lang.String getId();
  /**
   * <code>string id = 1 [json_name = "id"];</code>
   * @return The bytes for id.
   */
  com.google.protobuf.ByteString
      getIdBytes();

  /**
   * <code>string actor = 2 [json_name = "actor"];</code>
   * @return The actor.
   */
  java.lang.String getActor();
  /**
   * <code>string actor = 2 [json_name = "actor"];</code>
   * @return The bytes for actor.
   */
  com.google.protobuf.ByteString
      getActorBytes();

  /**
   * <code>string filter_id = 3 [json_name = "filterId"];</code>
   * @return The filterId.
   */
  java.lang.String getFilterId();
  /**
   * <code>string filter_id = 3 [json_name = "filterId"];</code>
   * @return The bytes for filterId.
   */
  com.google.protobuf.ByteString
      getFilterIdBytes();

  /**
   * <code>int64 detected_at = 4 [json_name = "detectedAt"];</code>
   * @return The detectedAt.
   */
  long getDetectedAt();

  /**
   * <code>string ip = 5 [json_name = "ip"];</code>
   * @return The ip.
   */
  java.lang.String getIp();
  /**
   * <code>string ip = 5 [json_name = "ip"];</code>
   * @return The bytes for ip.
   */
  com.google.protobuf.ByteString
      getIpBytes();

  /**
   * <code>string endpoint = 6 [json_name = "endpoint"];</code>
   * @return The endpoint.
   */
  java.lang.String getEndpoint();
  /**
   * <code>string endpoint = 6 [json_name = "endpoint"];</code>
   * @return The bytes for endpoint.
   */
  com.google.protobuf.ByteString
      getEndpointBytes();

  /**
   * <code>string method = 7 [json_name = "method"];</code>
   * @return The method.
   */
  java.lang.String getMethod();
  /**
   * <code>string method = 7 [json_name = "method"];</code>
   * @return The bytes for method.
   */
  com.google.protobuf.ByteString
      getMethodBytes();

  /**
   * <code>int32 api_collection_id = 8 [json_name = "apiCollectionId"];</code>
   * @return The apiCollectionId.
   */
  int getApiCollectionId();

  /**
   * <code>string payload = 9 [json_name = "payload"];</code>
   * @return The payload.
   */
  java.lang.String getPayload();
  /**
   * <code>string payload = 9 [json_name = "payload"];</code>
   * @return The bytes for payload.
   */
  com.google.protobuf.ByteString
      getPayloadBytes();

  /**
   * <code>string country = 10 [json_name = "country"];</code>
   * @return The country.
   */
  java.lang.String getCountry();
  /**
   * <code>string country = 10 [json_name = "country"];</code>
   * @return The bytes for country.
   */
  com.google.protobuf.ByteString
      getCountryBytes();

  /**
   * <code>.threat_protection.message.malicious_event.event_type.v1.EventType event_type = 11 [json_name = "eventType"];</code>
   * @return The enum numeric value on the wire for eventType.
   */
  int getEventTypeValue();
  /**
   * <code>.threat_protection.message.malicious_event.event_type.v1.EventType event_type = 11 [json_name = "eventType"];</code>
   * @return The eventType.
   */
  com.akto.proto.threat_protection.message.malicious_event.event_type.v1.EventType getEventType();
}
