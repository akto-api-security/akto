package com.akto.test_editor.filter.data_operands_impl;

import org.apache.commons.validator.routines.InetAddressValidator;
import inet.ipaddr.IPAddressString;

public class ContainsEitherIpFilter extends ContainsEitherFilter {

    @Override
    public Boolean evaluateOnStringQuerySet(String data, String query) {
        InetAddressValidator ipAddressValidator = InetAddressValidator.getInstance();
        
        // Validate that data is a valid IP address
        if (!ipAddressValidator.isValid(data)) {
            return false;
        }
        
        // Check if query is a valid CIDR range and if data IP is contained in it
        try {
            IPAddressString cidrAddress = new IPAddressString(query);
            IPAddressString ipAddress = new IPAddressString(data);
            return cidrAddress.contains(ipAddress);
        } catch (Exception e) {
            return false;
        }
    }
}
