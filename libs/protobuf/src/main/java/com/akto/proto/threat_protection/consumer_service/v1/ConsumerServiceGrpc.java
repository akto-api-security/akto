package com.akto.proto.threat_protection.consumer_service.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.68.1)",
    comments = "Source: threat_protection/consumer_service/v1/consumer_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ConsumerServiceGrpc {

  private ConsumerServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "threat_protection.consumer_service.v1.ConsumerService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest,
      com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse> getSaveMaliciousEventMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SaveMaliciousEvent",
      requestType = com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest.class,
      responseType = com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest,
      com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse> getSaveMaliciousEventMethod() {
    io.grpc.MethodDescriptor<com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest, com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse> getSaveMaliciousEventMethod;
    if ((getSaveMaliciousEventMethod = ConsumerServiceGrpc.getSaveMaliciousEventMethod) == null) {
      synchronized (ConsumerServiceGrpc.class) {
        if ((getSaveMaliciousEventMethod = ConsumerServiceGrpc.getSaveMaliciousEventMethod) == null) {
          ConsumerServiceGrpc.getSaveMaliciousEventMethod = getSaveMaliciousEventMethod =
              io.grpc.MethodDescriptor.<com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest, com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SaveMaliciousEvent"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ConsumerServiceMethodDescriptorSupplier("SaveMaliciousEvent"))
              .build();
        }
      }
    }
    return getSaveMaliciousEventMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest,
      com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse> getSaveSmartEventMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SaveSmartEvent",
      requestType = com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest.class,
      responseType = com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest,
      com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse> getSaveSmartEventMethod() {
    io.grpc.MethodDescriptor<com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest, com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse> getSaveSmartEventMethod;
    if ((getSaveSmartEventMethod = ConsumerServiceGrpc.getSaveSmartEventMethod) == null) {
      synchronized (ConsumerServiceGrpc.class) {
        if ((getSaveSmartEventMethod = ConsumerServiceGrpc.getSaveSmartEventMethod) == null) {
          ConsumerServiceGrpc.getSaveSmartEventMethod = getSaveSmartEventMethod =
              io.grpc.MethodDescriptor.<com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest, com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SaveSmartEvent"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ConsumerServiceMethodDescriptorSupplier("SaveSmartEvent"))
              .build();
        }
      }
    }
    return getSaveSmartEventMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ConsumerServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceStub>() {
        @java.lang.Override
        public ConsumerServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ConsumerServiceStub(channel, callOptions);
        }
      };
    return ConsumerServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ConsumerServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceBlockingStub>() {
        @java.lang.Override
        public ConsumerServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ConsumerServiceBlockingStub(channel, callOptions);
        }
      };
    return ConsumerServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ConsumerServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceFutureStub>() {
        @java.lang.Override
        public ConsumerServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ConsumerServiceFutureStub(channel, callOptions);
        }
      };
    return ConsumerServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void saveMaliciousEvent(com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSaveMaliciousEventMethod(), responseObserver);
    }

    /**
     */
    default void saveSmartEvent(com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSaveSmartEventMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ConsumerService.
   */
  public static abstract class ConsumerServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ConsumerServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ConsumerService.
   */
  public static final class ConsumerServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ConsumerServiceStub> {
    private ConsumerServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ConsumerServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ConsumerServiceStub(channel, callOptions);
    }

    /**
     */
    public void saveMaliciousEvent(com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSaveMaliciousEventMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void saveSmartEvent(com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSaveSmartEventMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ConsumerService.
   */
  public static final class ConsumerServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ConsumerServiceBlockingStub> {
    private ConsumerServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ConsumerServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ConsumerServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse saveMaliciousEvent(com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSaveMaliciousEventMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse saveSmartEvent(com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSaveSmartEventMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ConsumerService.
   */
  public static final class ConsumerServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ConsumerServiceFutureStub> {
    private ConsumerServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ConsumerServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ConsumerServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse> saveMaliciousEvent(
        com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSaveMaliciousEventMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse> saveSmartEvent(
        com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSaveSmartEventMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SAVE_MALICIOUS_EVENT = 0;
  private static final int METHODID_SAVE_SMART_EVENT = 1;

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
        case METHODID_SAVE_MALICIOUS_EVENT:
          serviceImpl.saveMaliciousEvent((com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest) request,
              (io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse>) responseObserver);
          break;
        case METHODID_SAVE_SMART_EVENT:
          serviceImpl.saveSmartEvent((com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest) request,
              (io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse>) responseObserver);
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
          getSaveMaliciousEventMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest,
              com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse>(
                service, METHODID_SAVE_MALICIOUS_EVENT)))
        .addMethod(
          getSaveSmartEventMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest,
              com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse>(
                service, METHODID_SAVE_SMART_EVENT)))
        .build();
  }

  private static abstract class ConsumerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ConsumerServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.akto.proto.threat_protection.consumer_service.v1.ConsumerServiceProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ConsumerService");
    }
  }

  private static final class ConsumerServiceFileDescriptorSupplier
      extends ConsumerServiceBaseDescriptorSupplier {
    ConsumerServiceFileDescriptorSupplier() {}
  }

  private static final class ConsumerServiceMethodDescriptorSupplier
      extends ConsumerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ConsumerServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ConsumerServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ConsumerServiceFileDescriptorSupplier())
              .addMethod(getSaveMaliciousEventMethod())
              .addMethod(getSaveSmartEventMethod())
              .build();
        }
      }
    }
    return result;
  }
}
