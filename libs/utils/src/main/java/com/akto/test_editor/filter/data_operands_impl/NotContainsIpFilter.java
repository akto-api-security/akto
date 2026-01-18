package com.akto.test_editor.filter.data_operands_impl;

import org.apache.commons.validator.routines.InetAddressValidator;
import com.akto.util.http_util.CoreHTTPClient;

public class NotContainsIpFilter extends NotContainsFilter {

    @Override
    public Boolean evaluateOnStringQuerySet(String data, String query) {
        InetAddressValidator ipAddressValidator = InetAddressValidator.getInstance();
        
        // Validate that data is a valid IP address
        if (!ipAddressValidator.isValid(data)) {
            return true; // If data is not a valid IP, it's not in any CIDR range
        }
        
        // Check if query is a valid CIDR range and if data IP is NOT contained in it
        try {
            return !CoreHTTPClient.ipContains(query, data);
        } catch (Exception e) {
            return false; // If query is not a valid CIDR, consider it as not containing the IP
        }
    }
}
