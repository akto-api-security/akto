package com.akto.proto.threat_protection.service.malicious_alert_service.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.68.1)",
    comments = "Source: threat_protection/service/malicious_alert_service/v1/consumer_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class MaliciousAlertServiceGrpc {

  private MaliciousAlertServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "threat_protection.service.malicious_alert_service.v1.MaliciousAlertService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest,
      com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse> getRecordAlertMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RecordAlert",
      requestType = com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest.class,
      responseType = com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest,
      com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse> getRecordAlertMethod() {
    io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest, com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse> getRecordAlertMethod;
    if ((getRecordAlertMethod = MaliciousAlertServiceGrpc.getRecordAlertMethod) == null) {
      synchronized (MaliciousAlertServiceGrpc.class) {
        if ((getRecordAlertMethod = MaliciousAlertServiceGrpc.getRecordAlertMethod) == null) {
          MaliciousAlertServiceGrpc.getRecordAlertMethod = getRecordAlertMethod =
              io.grpc.MethodDescriptor.<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest, com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RecordAlert"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MaliciousAlertServiceMethodDescriptorSupplier("RecordAlert"))
              .build();
        }
      }
    }
    return getRecordAlertMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static MaliciousAlertServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MaliciousAlertServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MaliciousAlertServiceStub>() {
        @java.lang.Override
        public MaliciousAlertServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MaliciousAlertServiceStub(channel, callOptions);
        }
      };
    return MaliciousAlertServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static MaliciousAlertServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MaliciousAlertServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MaliciousAlertServiceBlockingStub>() {
        @java.lang.Override
        public MaliciousAlertServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MaliciousAlertServiceBlockingStub(channel, callOptions);
        }
      };
    return MaliciousAlertServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static MaliciousAlertServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MaliciousAlertServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MaliciousAlertServiceFutureStub>() {
        @java.lang.Override
        public MaliciousAlertServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MaliciousAlertServiceFutureStub(channel, callOptions);
        }
      };
    return MaliciousAlertServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void recordAlert(com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRecordAlertMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service MaliciousAlertService.
   */
  public static abstract class MaliciousAlertServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return MaliciousAlertServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service MaliciousAlertService.
   */
  public static final class MaliciousAlertServiceStub
      extends io.grpc.stub.AbstractAsyncStub<MaliciousAlertServiceStub> {
    private MaliciousAlertServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MaliciousAlertServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MaliciousAlertServiceStub(channel, callOptions);
    }

    /**
     */
    public void recordAlert(com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRecordAlertMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service MaliciousAlertService.
   */
  public static final class MaliciousAlertServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<MaliciousAlertServiceBlockingStub> {
    private MaliciousAlertServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MaliciousAlertServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MaliciousAlertServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse recordAlert(com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRecordAlertMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service MaliciousAlertService.
   */
  public static final class MaliciousAlertServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<MaliciousAlertServiceFutureStub> {
    private MaliciousAlertServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MaliciousAlertServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MaliciousAlertServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse> recordAlert(
        com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRecordAlertMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_RECORD_ALERT = 0;

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
        case METHODID_RECORD_ALERT:
          serviceImpl.recordAlert((com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest) request,
              (io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse>) responseObserver);
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
          getRecordAlertMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest,
              com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse>(
                service, METHODID_RECORD_ALERT)))
        .build();
  }

  private static abstract class MaliciousAlertServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    MaliciousAlertServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.akto.proto.threat_protection.service.malicious_alert_service.v1.ConsumerServiceProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("MaliciousAlertService");
    }
  }

  private static final class MaliciousAlertServiceFileDescriptorSupplier
      extends MaliciousAlertServiceBaseDescriptorSupplier {
    MaliciousAlertServiceFileDescriptorSupplier() {}
  }

  private static final class MaliciousAlertServiceMethodDescriptorSupplier
      extends MaliciousAlertServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    MaliciousAlertServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (MaliciousAlertServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new MaliciousAlertServiceFileDescriptorSupplier())
              .addMethod(getRecordAlertMethod())
              .build();
        }
      }
    }
    return result;
  }
}
