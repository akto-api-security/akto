package com.akto.runtime.policies;

import java.util.List;

import com.akto.dto.HttpResponseParams;
import com.akto.runtime.APICatalogSync;

public class AktoPolicies {
    private AktoPolicy aktoPolicy;
    private AktoPolicyNew aktoPolicyNew;

    public AktoPolicies(APICatalogSync apiCatalogSync, boolean fetchAllSTI) {
        if (APICatalogSync.mergeAsyncOutside) {
            this.aktoPolicyNew = new AktoPolicyNew(fetchAllSTI);;
        } else {
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
