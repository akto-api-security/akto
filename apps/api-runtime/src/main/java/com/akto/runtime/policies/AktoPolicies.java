package com.akto.runtime.policies;

import java.util.List;

import com.akto.dto.HttpResponseParams;
import com.akto.runtime.APICatalogSync;

public class AktoPolicies {
    private final AktoPolicyNew aktoPolicyNew;

    public AktoPolicies(boolean fetchAllSTI) {
        this.aktoPolicyNew = new AktoPolicyNew(fetchAllSTI);;
    }

    public AktoPolicyNew getAktoPolicyNew() {
        return this.aktoPolicyNew;
    }

    public void main(List<HttpResponseParams> httpResponseParamsList, APICatalogSync apiCatalogSync, boolean fetchAllSTI) throws Exception {
        this.aktoPolicyNew.main(httpResponseParamsList, apiCatalogSync != null, fetchAllSTI);
    }
}
