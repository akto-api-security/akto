package com.akto.service.posture;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** A risky combination: the signals that must all hold on one asset, and how the Summary tab words it. */
public enum ArgusInventoryCombo {
    EXPOSED(ArgusInventoryFlag.COMBO_EXPOSED, "critical",
            "Anyone can reach it, and its tools can delete data or read credentials.",
            Arrays.asList("Exposed to the internet", "Dangerous tools"),
            ArgusInventoryFlag.EXPOSED, ArgusInventoryFlag.DANGEROUS),
    SHADOW(ArgusInventoryFlag.COMBO_SHADOW, "critical",
            "Nobody reviewed it, and it handles sensitive data.",
            Arrays.asList("Shadow AI", "Sensitive data"),
            ArgusInventoryFlag.SHADOW, ArgusInventoryFlag.SENSITIVE),
    UNREVIEWED(ArgusInventoryFlag.COMBO_UNREVIEWED, "caution",
            "Nobody approved it, and its tools go beyond read-only access.",
            Arrays.asList("Unreviewed", "Privileged tools"),
            ArgusInventoryFlag.UNREVIEWED, ArgusInventoryFlag.PRIVILEGED),
    DORMANT(ArgusInventoryFlag.COMBO_DORMANT, "caution",
            "Nobody uses it, but its tools can still act on your systems.",
            Arrays.asList("Dormant", "Privileged tools"),
            ArgusInventoryFlag.DORMANT, ArgusInventoryFlag.PRIVILEGED);

    private final ArgusInventoryFlag flag;
    private final String tone;
    private final String why;
    private final List<String> chips;
    private final Set<ArgusInventoryFlag> signals;

    ArgusInventoryCombo(ArgusInventoryFlag flag, String tone, String why, List<String> chips, ArgusInventoryFlag... signals) {
        this.flag = flag;
        this.tone = tone;
        this.why = why;
        this.chips = chips;
        this.signals = EnumSet.copyOf(Arrays.asList(signals));
    }

    public ArgusInventoryFlag getFlag() {
        return flag;
    }

    public String getTone() {
        return tone;
    }

    public String getWhy() {
        return why;
    }

    public List<String> getChips() {
        return chips;
    }

    public boolean matches(Set<ArgusInventoryFlag> flags) {
        return flags.containsAll(signals);
    }
}
