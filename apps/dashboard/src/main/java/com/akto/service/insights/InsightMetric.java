package com.akto.service.insights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One number in an InsightResult. The narrative prompt copies {@code formatted} verbatim rather
 * than computing from {@code value}/{@code denominator} itself — see InsightResult's numeric-guard
 * note. {@code key} is stable and is what a generated narrative's factsUsed references back to.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsightMetric {
    private String key;
    private String label;
    private Number value;
    private Number denominator; // nullable — lets prose say "12 of 340" without dividing itself
    private String unit;        // count | percent | tokens | days
    private String formatted;   // "37 devices" — copied verbatim by the model
    private String severity;    // nullable
}
