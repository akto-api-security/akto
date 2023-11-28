package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Objects;

public class DependencyNode {

    private ObjectId id;

    @BsonIgnore
    private String hexId;

    private String apiCollectionIdResp;
    public static final String API_COLLECTION_ID_RESP = "apiCollectionIdResp";
    private String urlResp;
    public static final String URL_RESP = "urlResp";
    private String methodResp;
    public static final String METHOD_RESP = "methodResp";

    private String apiCollectionIdReq;
    public static final String API_COLLECTION_ID_REQ = "apiCollectionIdReq";
    private String urlReq ;
    public static final String URL_REQ = "urlReq";
    private String methodReq;
    public static final String METHOD_REQ = "methodReq";

    public List<ParamInfo> paramInfos;
    public static final String PARAM_INFOS = "paramInfos";

    public static class ParamInfo {
        private String requestParam;
        public static final String REQUEST_PARAM = "requestParam";
        private String responseParam;
        public static final String RESPONSE_PARAM = "responseParam";
        private int count;
        public static final String COUNT = "count";

        public ParamInfo() {
        }

        public ParamInfo(String requestParam, String responseParam, int count) {
            this.requestParam = requestParam;
            this.responseParam = responseParam;
            this.count = count;
        }

        public String getRequestParam() {
            return requestParam;
        }

        public void setRequestParam(String requestParam) {
            this.requestParam = requestParam;
        }

        public String getResponseParam() {
            return responseParam;
        }

        public void setResponseParam(String responseParam) {
            this.responseParam = responseParam;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }


    public DependencyNode() {
    }


    public DependencyNode(String apiCollectionIdResp, String urlResp, String methodResp, String apiCollectionIdReq, String urlReq, String methodReq, List<ParamInfo> paramInfos) {
        this.apiCollectionIdResp = apiCollectionIdResp;
        this.urlResp = urlResp;
        this.methodResp = methodResp;
        this.apiCollectionIdReq = apiCollectionIdReq;
        this.urlReq = urlReq;
        this.methodReq = methodReq;
        this.paramInfos = paramInfos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyNode that = (DependencyNode) o;
        return apiCollectionIdResp.equals(that.apiCollectionIdResp) && urlResp.equals(that.urlResp) && methodResp.equals(that.methodResp) && apiCollectionIdReq.equals(that.apiCollectionIdReq) && urlReq.equals(that.urlReq) && methodReq.equals(that.methodReq);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiCollectionIdResp, urlResp, methodResp, apiCollectionIdReq, urlReq, methodReq);
    }

    public void updateOrCreateParamInfo(ParamInfo thatParamInfo) {
        if (thatParamInfo == null) return;
        boolean matched = false;
        for (ParamInfo thisParamInfo: paramInfos) {
            if (thisParamInfo.equals(thatParamInfo)) {
                thisParamInfo.setCount(thisParamInfo.getCount() + thatParamInfo.getCount());
            }
        }

        if (!matched) {
            this.paramInfos.add(thatParamInfo);
        }
    }

    public String getApiCollectionIdResp() {
        return apiCollectionIdResp;
    }

    public void setApiCollectionIdResp(String apiCollectionIdResp) {
        this.apiCollectionIdResp = apiCollectionIdResp;
    }

    public String getUrlResp() {
        return urlResp;
    }

    public void setUrlResp(String urlResp) {
        this.urlResp = urlResp;
    }

    public String getMethodResp() {
        return methodResp;
    }

    public void setMethodResp(String methodResp) {
        this.methodResp = methodResp;
    }
    public String getApiCollectionIdReq() {
        return apiCollectionIdReq;
    }

    public void setApiCollectionIdReq(String apiCollectionIdReq) {
        this.apiCollectionIdReq = apiCollectionIdReq;
    }

    public String getUrlReq() {
        return urlReq;
    }

    public void setUrlReq(String urlReq) {
        this.urlReq = urlReq;
    }

    public String getMethodReq() {
        return methodReq;
    }

    public void setMethodReq(String methodReq) {
        this.methodReq = methodReq;
    }
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public List<ParamInfo> getParamInfos() {
        return paramInfos;
    }

    public void setParamInfos(List<ParamInfo> paramInfos) {
        this.paramInfos = paramInfos;
    }


    public String getHexId() {
        return this.id.toHexString();
    }


    public void setHexId(String hexId) {
        this.hexId = hexId;
    }

}
