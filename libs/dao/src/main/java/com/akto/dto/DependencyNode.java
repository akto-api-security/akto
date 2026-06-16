package com.akto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.ArrayList;
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

    private int lastUpdated;
    public static final String LAST_UPDATED = "lastUpdated";

    private boolean arrayResponse;
    public static final String IS_ARRAY_RESPONSE = "arrayResponse";

    public static class ParamInfo {
        private String requestParam;
        public static final String REQUEST_PARAM = "requestParam";

        private boolean isUrlParam;
        public static final String IS_URL_PARAM = "isUrlParam";

        private boolean isHeader;
        public static final String IS_HEADER = "isHeader";
        private String responseParam;
        public static final String RESPONSE_PARAM = "responseParam";
        private String responseUrlPath;
        public static final String RESPONSE_URL_PATH = "responseUrlPath";
        private String responseBody;
        public static final String RESPONSE_BODY = "responseBody";
        private int count;
        public static final String COUNT = "count";

        public ParamInfo() {
        }

        public ParamInfo(String requestParam, String responseParam, int count, boolean isUrlParam, boolean isHeader) {
            this.requestParam = requestParam;
            this.responseParam = responseParam;
            this.isUrlParam = isUrlParam;
            this.count = count;
            this.isHeader = isHeader;
            this.responseUrlPath = null;
            this.responseBody = null;
        }

        public ParamInfo(String requestParam, String responseParam, String responseUrlPath, int count, boolean isUrlParam, boolean isHeader) {
            this.requestParam = requestParam;
            this.responseParam = responseParam;
            this.responseUrlPath = responseUrlPath;
            this.isUrlParam = isUrlParam;
            this.count = count;
            this.isHeader = isHeader;
            this.responseBody = null;
        }

        public ParamInfo(String requestParam, String responseParam, String responseUrlPath, String responseBody, int count, boolean isUrlParam, boolean isHeader) {
            this.requestParam = requestParam;
            this.responseParam = responseParam;
            this.responseUrlPath = responseUrlPath;
            this.responseBody = responseBody;
            this.isUrlParam = isUrlParam;
            this.count = count;
            this.isHeader = isHeader;
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

        public boolean isUrlParam() {
            return isUrlParam;
        }

        public boolean getIsUrlParam() {
            return isUrlParam;
        }

        public void setIsUrlParam(boolean urlParam) {
            isUrlParam = urlParam;
        }

        public boolean isHeader() {
            return isHeader;
        }

        public boolean getIsHeader() {
            return isHeader;
        }

        public void setIsHeader(boolean header) {
            isHeader = header;
        }

        public String getResponseUrlPath() {
            return responseUrlPath;
        }

        public void setResponseUrlPath(String responseUrlPath) {
            this.responseUrlPath = responseUrlPath;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public void setResponseBody(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ParamInfo paramInfo = (ParamInfo) o;
            return isUrlParam == paramInfo.isUrlParam && isHeader == paramInfo.isHeader && requestParam.equals(paramInfo.requestParam) && responseParam.equals(paramInfo.responseParam) && Objects.equals(responseUrlPath, paramInfo.responseUrlPath) && Objects.equals(responseBody, paramInfo.responseBody);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestParam, responseParam, responseUrlPath, responseBody, isUrlParam, isHeader);
        }

        public ParamInfo copy() {
            return new ParamInfo(requestParam, responseParam, responseUrlPath, responseBody, count, isUrlParam, isHeader);
        }
    }


    public DependencyNode() {
    }


    public DependencyNode(String apiCollectionIdResp, String urlResp, String methodResp, String apiCollectionIdReq, String urlReq, String methodReq, List<ParamInfo> paramInfos, int lastUpdated) {
        this.apiCollectionIdResp = apiCollectionIdResp;
        this.urlResp = urlResp;
        this.methodResp = methodResp;
        this.apiCollectionIdReq = apiCollectionIdReq;
        this.urlReq = urlReq;
        this.methodReq = methodReq;
        this.paramInfos = paramInfos;
        this.lastUpdated = lastUpdated;
        this.arrayResponse = false;
    }

    public DependencyNode(String apiCollectionIdResp, String urlResp, String methodResp, String apiCollectionIdReq, String urlReq, String methodReq, List<ParamInfo> paramInfos, int lastUpdated, boolean arrayResponse) {
        this.apiCollectionIdResp = apiCollectionIdResp;
        this.urlResp = urlResp;
        this.methodResp = methodResp;
        this.apiCollectionIdReq = apiCollectionIdReq;
        this.urlReq = urlReq;
        this.methodReq = methodReq;
        this.paramInfos = paramInfos;
        this.lastUpdated = lastUpdated;
        this.arrayResponse = arrayResponse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyNode that = (DependencyNode) o;
        return apiCollectionIdResp.equals(that.apiCollectionIdResp) && urlResp.equals(that.urlResp) && methodResp.equals(that.methodResp) && apiCollectionIdReq.equals(that.apiCollectionIdReq) && urlReq.equals(that.urlReq) && methodReq.equals(that.methodReq) && arrayResponse == that.arrayResponse;
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
                matched = true;
            }
        }

        if (!matched) {
            this.paramInfos.add(thatParamInfo.copy());
        }
    }

    public DependencyNode copy() {
        List<ParamInfo> paramInfoList = new ArrayList<>();
        for (ParamInfo paramInfo:this.paramInfos) {
            paramInfoList.add(paramInfo.copy());
        }

        return new DependencyNode(
                this.apiCollectionIdResp, this.urlResp, this.methodResp,
                this.apiCollectionIdReq, this.urlReq, this.methodReq,
                paramInfoList, this.lastUpdated, this.arrayResponse
        );
    }

    public void merge(DependencyNode that) {
        if (!this.equals(that)) return;
        for (DependencyNode.ParamInfo paramInfo: that.paramInfos) {
            this.updateOrCreateParamInfo(paramInfo);
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
        if (this.id == null) {
            return null;
        }
        return this.id.toHexString();
    }


    public int getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(int lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @JsonIgnore
    public boolean isArrayResponse() {
        return arrayResponse;
    }

    @JsonProperty("arrayResponse")
    public boolean getArrayResponse() {
        return arrayResponse;
    }

    @JsonProperty("arrayResponse")
    @JsonAlias("isArrayResponse")
    public void setArrayResponse(boolean arrayResponse) {
        this.arrayResponse = arrayResponse;
    }
}
