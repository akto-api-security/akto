package com.akto.runtime.policies;

import java.util.List;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.runtime.APICatalogSync;

public class AktoPolicies {
    private AktoPolicy aktoPolicy;
    private AktoPolicyNew aktoPolicyNew;

    private static final LoggerMaker loggerMaker = new LoggerMaker(AktoPolicies.class);

    public AktoPolicies(APICatalogSync apiCatalogSync, boolean fetchAllSTI) {
        if (APICatalogSync.mergeAsyncOutside) {
            loggerMaker.infoAndAddToDb("AktoPolicies: mergeAsyncOutside is true, using AktoPolicyNew", LoggerMaker.LogDb.RUNTIME);
            this.aktoPolicyNew = new AktoPolicyNew(fetchAllSTI);;
        } else {
            loggerMaker.infoAndAddToDb("AktoPolicies: mergeAsyncOutside is false, using AktoPolicy", LoggerMaker.LogDb.RUNTIME);
            this.aktoPolicy = new AktoPolicy(apiCatalogSync, fetchAllSTI);
        }
    }    
    

    public AktoPolicy getAktoPolicy() {
        return this.aktoPolicy;
    }

    public AktoPolicyNew getAktoPolicyNew() {
        return this.aktoPolicyNew;
    }

    public void main(List<HttpResponseParams> httpResponseParamsList, APICatalogSync apiCatalogSync, boolean fetchAllSTI) throws Exception {
        if (this.aktoPolicy != null) {
            this.aktoPolicy.main(httpResponseParamsList, apiCatalogSync, fetchAllSTI);
        } 

        if (this.aktoPolicyNew != null) {
            this.aktoPolicyNew.main(httpResponseParamsList, apiCatalogSync != null, fetchAllSTI);
        }
    }
}
