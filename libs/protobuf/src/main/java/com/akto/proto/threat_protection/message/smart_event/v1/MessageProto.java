// Generated by the protocol buffer compiler.  DO NOT EDIT!
// NO CHECKED-IN PROTOBUF GENCODE
// source: threat_protection/message/smart_event/v1/message.proto
// Protobuf Java Version: 4.28.3

package com.akto.proto.threat_protection.message.smart_event.v1;

public final class MessageProto {
  private MessageProto() {}
  static {
    com.google.protobuf.RuntimeVersion.validateProtobufGencodeVersion(
      com.google.protobuf.RuntimeVersion.RuntimeDomain.PUBLIC,
      /* major= */ 4,
      /* minor= */ 28,
      /* patch= */ 3,
      /* suffix= */ "",
      MessageProto.class.getName());
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
    internal_static_threat_protection_message_smart_event_v1_SmartEvent_descriptor;
  static final 
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_threat_protection_message_smart_event_v1_SmartEvent_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n6threat_protection/message/smart_event/" +
      "v1/message.proto\022(threat_protection.mess" +
      "age.smart_event.v1\"y\n\nSmartEvent\022\024\n\005acto" +
      "r\030\001 \001(\tR\005actor\022\033\n\tfilter_id\030\002 \001(\tR\010filte" +
      "rId\022\037\n\013detected_at\030\003 \001(\003R\ndetectedAt\022\027\n\007" +
      "rule_id\030\004 \001(\tR\006ruleIdB\202\002\n7com.akto.proto" +
      ".threat_protection.message.smart_event.v" +
      "1B\014MessageProtoP\001\242\002\003TMS\252\002&ThreatProtecti" +
      "on.Message.SmartEvent.V1\312\002&ThreatProtect" +
      "ion\\Message\\SmartEvent\\V1\342\0022ThreatProtec" +
      "tion\\Message\\SmartEvent\\V1\\GPBMetadata\352\002" +
      ")ThreatProtection::Message::SmartEvent::" +
      "V1b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_threat_protection_message_smart_event_v1_SmartEvent_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_threat_protection_message_smart_event_v1_SmartEvent_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_threat_protection_message_smart_event_v1_SmartEvent_descriptor,
        new java.lang.String[] { "Actor", "FilterId", "DetectedAt", "RuleId", });
    descriptor.resolveAllFeaturesImmutable();
  }

  // @@protoc_insertion_point(outer_class_scope)
}
