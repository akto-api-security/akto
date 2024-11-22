// Generated by the protocol buffer compiler.  DO NOT EDIT!
// NO CHECKED-IN PROTOBUF GENCODE
// source: threat_protection/consumer_service/v1/consumer_service.proto
// Protobuf Java Version: 4.28.3

package com.akto.proto.threat_protection.consumer_service.v1;

public final class ConsumerServiceProto {
  private ConsumerServiceProto() {}
  static {
    com.google.protobuf.RuntimeVersion.validateProtobufGencodeVersion(
      com.google.protobuf.RuntimeVersion.RuntimeDomain.PUBLIC,
      /* major= */ 4,
      /* minor= */ 28,
      /* patch= */ 3,
      /* suffix= */ "",
      ConsumerServiceProto.class.getName());
  }
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_threat_protection_consumer_service_v1_SaveMaliciousEventResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_threat_protection_consumer_service_v1_SaveMaliciousEventResponse_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_threat_protection_consumer_service_v1_SaveSmartEventResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_threat_protection_consumer_service_v1_SaveSmartEventResponse_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_threat_protection_consumer_service_v1_SmartEvent_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_threat_protection_consumer_service_v1_SmartEvent_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_threat_protection_consumer_service_v1_MaliciousEvent_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_threat_protection_consumer_service_v1_MaliciousEvent_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_threat_protection_consumer_service_v1_SaveMaliciousEventRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_threat_protection_consumer_service_v1_SaveMaliciousEventRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_threat_protection_consumer_service_v1_SaveSmartEventRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_threat_protection_consumer_service_v1_SaveSmartEventRequest_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n<threat_protection/consumer_service/v1/" +
      "consumer_service.proto\022%threat_protectio" +
      "n.consumer_service.v1\"\034\n\032SaveMaliciousEv" +
      "entResponse\"\030\n\026SaveSmartEventResponse\"~\n" +
      "\nSmartEvent\022\031\n\010actor_id\030\001 \001(\tR\007actorId\022\033" +
      "\n\tfilter_id\030\002 \001(\tR\010filterId\022\037\n\013detected_" +
      "at\030\003 \001(\003R\ndetectedAt\022\027\n\007rule_id\030\004 \001(\tR\006r" +
      "uleId\"\346\001\n\016MaliciousEvent\022\031\n\010actor_id\030\001 \001" +
      "(\tR\007actorId\022\033\n\tfilter_id\030\002 \001(\tR\010filterId" +
      "\022\016\n\002ip\030\003 \001(\tR\002ip\022\034\n\ttimestamp\030\004 \001(\003R\ttim" +
      "estamp\022\020\n\003url\030\005 \001(\tR\003url\022\026\n\006method\030\006 \001(\t" +
      "R\006method\022*\n\021api_collection_id\030\007 \001(\005R\017api" +
      "CollectionId\022\030\n\007payload\030\010 \001(\tR\007payload\"j" +
      "\n\031SaveMaliciousEventRequest\022M\n\006events\030\002 " +
      "\003(\01325.threat_protection.consumer_service" +
      ".v1.MaliciousEventR\006events\"`\n\025SaveSmartE" +
      "ventRequest\022G\n\005event\030\002 \001(\01321.threat_prot" +
      "ection.consumer_service.v1.SmartEventR\005e" +
      "vent2\301\002\n\017ConsumerService\022\233\001\n\022SaveMalicio" +
      "usEvent\022@.threat_protection.consumer_ser" +
      "vice.v1.SaveMaliciousEventRequest\032A.thre" +
      "at_protection.consumer_service.v1.SaveMa" +
      "liciousEventResponse\"\000\022\217\001\n\016SaveSmartEven" +
      "t\022<.threat_protection.consumer_service.v" +
      "1.SaveSmartEventRequest\032=.threat_protect" +
      "ion.consumer_service.v1.SaveSmartEventRe" +
      "sponse\"\000B\372\001\n4com.akto.proto.threat_prote" +
      "ction.consumer_service.v1B\024ConsumerServi" +
      "ceProtoP\001\242\002\003TCX\252\002#ThreatProtection.Consu" +
      "merService.V1\312\002#ThreatProtection\\Consume" +
      "rService\\V1\342\002/ThreatProtection\\ConsumerS" +
      "ervice\\V1\\GPBMetadata\352\002%ThreatProtection" +
      "::ConsumerService::V1b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_threat_protection_consumer_service_v1_SaveMaliciousEventResponse_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_threat_protection_consumer_service_v1_SaveMaliciousEventResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_threat_protection_consumer_service_v1_SaveMaliciousEventResponse_descriptor,
        new java.lang.String[] { });
    internal_static_threat_protection_consumer_service_v1_SaveSmartEventResponse_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_threat_protection_consumer_service_v1_SaveSmartEventResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_threat_protection_consumer_service_v1_SaveSmartEventResponse_descriptor,
        new java.lang.String[] { });
    internal_static_threat_protection_consumer_service_v1_SmartEvent_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_threat_protection_consumer_service_v1_SmartEvent_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_threat_protection_consumer_service_v1_SmartEvent_descriptor,
        new java.lang.String[] { "ActorId", "FilterId", "DetectedAt", "RuleId", });
    internal_static_threat_protection_consumer_service_v1_MaliciousEvent_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_threat_protection_consumer_service_v1_MaliciousEvent_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_threat_protection_consumer_service_v1_MaliciousEvent_descriptor,
        new java.lang.String[] { "ActorId", "FilterId", "Ip", "Timestamp", "Url", "Method", "ApiCollectionId", "Payload", });
    internal_static_threat_protection_consumer_service_v1_SaveMaliciousEventRequest_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_threat_protection_consumer_service_v1_SaveMaliciousEventRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_threat_protection_consumer_service_v1_SaveMaliciousEventRequest_descriptor,
        new java.lang.String[] { "Events", });
    internal_static_threat_protection_consumer_service_v1_SaveSmartEventRequest_descriptor =
      getDescriptor().getMessageTypes().get(5);
    internal_static_threat_protection_consumer_service_v1_SaveSmartEventRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_threat_protection_consumer_service_v1_SaveSmartEventRequest_descriptor,
        new java.lang.String[] { "Event", });
    descriptor.resolveAllFeaturesImmutable();
  }

  // @@protoc_insertion_point(outer_class_scope)
}
