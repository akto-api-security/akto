package com.akto.service.insights;

/** Public access to InsightUtil's vendor canonicalization, for services outside this package. */
public final class InsightVendorNames {

    private InsightVendorNames() {}

    public static String canonical(String vendor) {
        return InsightUtil.canonicalVendorName(vendor);
    }
}
