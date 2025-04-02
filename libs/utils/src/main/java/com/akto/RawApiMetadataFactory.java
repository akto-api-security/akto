package com.akto;

import com.akto.dto.RawApiMetadata;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;


public class RawApiMetadataFactory {
    private IPLookupClient ipLookupClient;

    public RawApiMetadataFactory() throws Exception{
        this.ipLookupClient = new IPLookupClient();
    }
    
    public RawApiMetadata buildFromHttp(OriginalHttpRequest request, OriginalHttpResponse response) {

        String countryCode = this.ipLookupClient.getCountryISOCodeGivenIp(request.getSourceIp()).orElse("");
        RawApiMetadata metadata = new RawApiMetadata(countryCode);
        return metadata;
    }
}
