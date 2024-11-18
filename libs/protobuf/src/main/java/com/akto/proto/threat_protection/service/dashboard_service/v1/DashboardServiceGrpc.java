package com.akto.proto.threat_protection.service.dashboard_service.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.68.1)",
    comments = "Source: threat_protection/service/dashboard_service/v1/service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class DashboardServiceGrpc {

  private DashboardServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "threat_protection.service.dashboard_service.v1.DashboardService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest,
      com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse> getListMaliciousRequestsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListMaliciousRequests",
      requestType = com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest.class,
      responseType = com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest,
      com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse> getListMaliciousRequestsMethod() {
    io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest, com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse> getListMaliciousRequestsMethod;
    if ((getListMaliciousRequestsMethod = DashboardServiceGrpc.getListMaliciousRequestsMethod) == null) {
      synchronized (DashboardServiceGrpc.class) {
        if ((getListMaliciousRequestsMethod = DashboardServiceGrpc.getListMaliciousRequestsMethod) == null) {
          DashboardServiceGrpc.getListMaliciousRequestsMethod = getListMaliciousRequestsMethod =
              io.grpc.MethodDescriptor.<com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest, com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListMaliciousRequests"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DashboardServiceMethodDescriptorSupplier("ListMaliciousRequests"))
              .build();
        }
      }
    }
    return getListMaliciousRequestsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest,
      com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse> getFetchAlertFiltersMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "FetchAlertFilters",
      requestType = com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest.class,
      responseType = com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest,
      com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse> getFetchAlertFiltersMethod() {
    io.grpc.MethodDescriptor<com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest, com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse> getFetchAlertFiltersMethod;
    if ((getFetchAlertFiltersMethod = DashboardServiceGrpc.getFetchAlertFiltersMethod) == null) {
      synchronized (DashboardServiceGrpc.class) {
        if ((getFetchAlertFiltersMethod = DashboardServiceGrpc.getFetchAlertFiltersMethod) == null) {
          DashboardServiceGrpc.getFetchAlertFiltersMethod = getFetchAlertFiltersMethod =
              io.grpc.MethodDescriptor.<com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest, com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "FetchAlertFilters"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DashboardServiceMethodDescriptorSupplier("FetchAlertFilters"))
              .build();
        }
      }
    }
    return getFetchAlertFiltersMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static DashboardServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DashboardServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DashboardServiceStub>() {
        @java.lang.Override
        public DashboardServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DashboardServiceStub(channel, callOptions);
        }
      };
    return DashboardServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static DashboardServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DashboardServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DashboardServiceBlockingStub>() {
        @java.lang.Override
        public DashboardServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DashboardServiceBlockingStub(channel, callOptions);
        }
      };
    return DashboardServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static DashboardServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DashboardServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DashboardServiceFutureStub>() {
        @java.lang.Override
        public DashboardServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DashboardServiceFutureStub(channel, callOptions);
        }
      };
    return DashboardServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void listMaliciousRequests(com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListMaliciousRequestsMethod(), responseObserver);
    }

    /**
     */
    default void fetchAlertFilters(com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getFetchAlertFiltersMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service DashboardService.
   */
  public static abstract class DashboardServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return DashboardServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service DashboardService.
   */
  public static final class DashboardServiceStub
      extends io.grpc.stub.AbstractAsyncStub<DashboardServiceStub> {
    private DashboardServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DashboardServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DashboardServiceStub(channel, callOptions);
    }

    /**
     */
    public void listMaliciousRequests(com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListMaliciousRequestsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void fetchAlertFilters(com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest request,
        io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getFetchAlertFiltersMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service DashboardService.
   */
  public static final class DashboardServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<DashboardServiceBlockingStub> {
    private DashboardServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DashboardServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DashboardServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse listMaliciousRequests(com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListMaliciousRequestsMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse fetchAlertFilters(com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getFetchAlertFiltersMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service DashboardService.
   */
  public static final class DashboardServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<DashboardServiceFutureStub> {
    private DashboardServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DashboardServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DashboardServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse> listMaliciousRequests(
        com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListMaliciousRequestsMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse> fetchAlertFilters(
        com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getFetchAlertFiltersMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_LIST_MALICIOUS_REQUESTS = 0;
  private static final int METHODID_FETCH_ALERT_FILTERS = 1;

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
        case METHODID_LIST_MALICIOUS_REQUESTS:
          serviceImpl.listMaliciousRequests((com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest) request,
              (io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse>) responseObserver);
          break;
        case METHODID_FETCH_ALERT_FILTERS:
          serviceImpl.fetchAlertFilters((com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest) request,
              (io.grpc.stub.StreamObserver<com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse>) responseObserver);
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
          getListMaliciousRequestsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest,
              com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse>(
                service, METHODID_LIST_MALICIOUS_REQUESTS)))
        .addMethod(
          getFetchAlertFiltersMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest,
              com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse>(
                service, METHODID_FETCH_ALERT_FILTERS)))
        .build();
  }

  private static abstract class DashboardServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    DashboardServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.akto.proto.threat_protection.service.dashboard_service.v1.ServiceProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("DashboardService");
    }
  }

  private static final class DashboardServiceFileDescriptorSupplier
      extends DashboardServiceBaseDescriptorSupplier {
    DashboardServiceFileDescriptorSupplier() {}
  }

  private static final class DashboardServiceMethodDescriptorSupplier
      extends DashboardServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    DashboardServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (DashboardServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new DashboardServiceFileDescriptorSupplier())
              .addMethod(getListMaliciousRequestsMethod())
              .addMethod(getFetchAlertFiltersMethod())
              .build();
        }
      }
    }
    return result;
  }
}
