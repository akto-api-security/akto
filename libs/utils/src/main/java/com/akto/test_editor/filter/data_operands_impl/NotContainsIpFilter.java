package com.akto.test_editor.filter.data_operands_impl;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

public class NotContainsIpFilter extends NotContainsFilter {

    @Override
    public Boolean evaluateOnStringQuerySet(String data, String query) {
        
        IpAddressMatcher ipAddressMatcher = new IpAddressMatcher(query);
        return !ipAddressMatcher.matches(data);
    }
}
