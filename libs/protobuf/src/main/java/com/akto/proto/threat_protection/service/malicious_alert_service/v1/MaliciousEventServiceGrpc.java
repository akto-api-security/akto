package com.akto.proto.threat_protection.service.malicious_alert_service.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.68.1)",
    comments = "Source: threat_protection/service/malicious_alert_service/v1/service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class MaliciousEventServiceGrpc {

  private MaliciousEventServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "threat_protection.service.malicious_alert_service.v1.MaliciousEventService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest,
      com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse> getRecordMaliciousEventMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RecordMaliciousEvent",
      requestType = com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest.class,
      responseType = com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest,
      com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse> getRecordMaliciousEventMethod() {
    io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest, com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse> getRecordMaliciousEventMethod;
    if ((getRecordMaliciousEventMethod = MaliciousEventServiceGrpc.getRecordMaliciousEventMethod) == null) {
      synchronized (MaliciousEventServiceGrpc.class) {
        if ((getRecordMaliciousEventMethod = MaliciousEventServiceGrpc.getRecordMaliciousEventMethod) == null) {
          MaliciousEventServiceGrpc.getRecordMaliciousEventMethod = getRecordMaliciousEventMethod =
              io.grpc.MethodDescriptor.<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest, com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RecordMaliciousEvent"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MaliciousEventServiceMethodDescriptorSupplier("RecordMaliciousEvent"))
              .build();
        }
      }
    }
    return getRecordMaliciousEventMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static MaliciousEventServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MaliciousEventServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MaliciousEventServiceStub>() {
        @java.lang.Override
        public MaliciousEventServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MaliciousEventServiceStub(channel, callOptions);
        }
      };
    return MaliciousEventServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static MaliciousEventServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MaliciousEventServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MaliciousEventServiceBlockingStub>() {
        @java.lang.Override
        public MaliciousEventServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MaliciousEventServiceBlockingStub(channel, callOptions);
        }
      };
    return MaliciousEventServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static MaliciousEventServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MaliciousEventServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MaliciousEventServiceFutureStub>() {
        @java.lang.Override
        public MaliciousEventServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MaliciousEventServiceFutureStub(channel, callOptions);
        }
      };
    return MaliciousEventServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void recordMaliciousEvent(com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRecordMaliciousEventMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service MaliciousEventService.
   */
  public static abstract class MaliciousEventServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return MaliciousEventServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service MaliciousEventService.
   */
  public static final class MaliciousEventServiceStub
      extends io.grpc.stub.AbstractAsyncStub<MaliciousEventServiceStub> {
    private MaliciousEventServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MaliciousEventServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MaliciousEventServiceStub(channel, callOptions);
    }

    /**
     */
    public void recordMaliciousEvent(com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRecordMaliciousEventMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service MaliciousEventService.
   */
  public static final class MaliciousEventServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<MaliciousEventServiceBlockingStub> {
    private MaliciousEventServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MaliciousEventServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MaliciousEventServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse recordMaliciousEvent(com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRecordMaliciousEventMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service MaliciousEventService.
   */
  public static final class MaliciousEventServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<MaliciousEventServiceFutureStub> {
    private MaliciousEventServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MaliciousEventServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MaliciousEventServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse> recordMaliciousEvent(
        com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRecordMaliciousEventMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_RECORD_MALICIOUS_EVENT = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_RECORD_MALICIOUS_EVENT:
          serviceImpl.recordMaliciousEvent((com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest) request,
              (io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRecordMaliciousEventMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest,
              com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse>(
                service, METHODID_RECORD_MALICIOUS_EVENT)))
        .build();
  }

  private static abstract class MaliciousEventServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    MaliciousEventServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.akto.proto.threat_protection.service.malicious_alert_service.v1.ServiceProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("MaliciousEventService");
    }
  }

  private static final class MaliciousEventServiceFileDescriptorSupplier
      extends MaliciousEventServiceBaseDescriptorSupplier {
    MaliciousEventServiceFileDescriptorSupplier() {}
  }

  private static final class MaliciousEventServiceMethodDescriptorSupplier
      extends MaliciousEventServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    MaliciousEventServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (MaliciousEventServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new MaliciousEventServiceFileDescriptorSupplier())
              .addMethod(getRecordMaliciousEventMethod())
              .build();
        }
      }
    }
    return result;
  }
}
