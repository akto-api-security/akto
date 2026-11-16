package com.akto.service.posture;

/** The filter keys an inventory row carries; the page sends one back to open the rows behind a number. */
public enum ArgusInventoryFlag {
    AT_RISK("atRisk"),
    ATTENTION("attention"),
    HEALTHY("healthy"),
    HIGH("high"),
    NO_POLICY("noPolicy"),
    DORMANT("dormant"),
    NEW("new"),
    NO_TEAM("noTeam"),
    UNREVIEWED("unreviewed"),
    SENSITIVE("sensitive"),
    SHADOW("shadow"),
    EXPOSED("exposed"),
    DANGEROUS("dangerous"),
    PRIVILEGED("privileged"),
    MALICIOUS("malicious"),
    COMBO_EXPOSED("comboExposed"),
    COMBO_SHADOW("comboShadow"),
    COMBO_UNREVIEWED("comboUnreviewed"),
    COMBO_DORMANT("comboDormant");

    private final String key;

    ArgusInventoryFlag(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /** The flag with this key, or null when there is none. */
    public static ArgusInventoryFlag of(String key) {
        for (ArgusInventoryFlag flag : values()) {
            if (flag.key.equals(key)) return flag;
        }
        return null;
    }
}
