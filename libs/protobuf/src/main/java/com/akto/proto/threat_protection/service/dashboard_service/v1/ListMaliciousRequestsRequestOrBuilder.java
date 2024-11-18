// Generated by the protocol buffer compiler.  DO NOT EDIT!
// NO CHECKED-IN PROTOBUF GENCODE
// source: threat_protection/service/dashboard_service/v1/service.proto
// Protobuf Java Version: 4.28.3

package com.akto.proto.threat_protection.service.dashboard_service.v1;

public interface ListMaliciousRequestsRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * The number of alerts to return
   * </pre>
   *
   * <code>int32 limit = 3 [json_name = "limit"];</code>
   * @return The limit.
   */
  int getLimit();

  /**
   * <code>optional int32 page = 4 [json_name = "page"];</code>
   * @return Whether the page field is set.
   */
  boolean hasPage();
  /**
   * <code>optional int32 page = 4 [json_name = "page"];</code>
   * @return The page.
   */
  int getPage();
}
