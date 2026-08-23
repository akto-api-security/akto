package com.akto.service.insights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** A bounded (<= EVIDENCE_ROW_CAP-ish) sample table backing an insight's claims. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceTable {
    private String id;
    private String title;
    private List<String> columns;
    private List<EvidenceRow> rows; // capped — see the provider for its own row cap
    private int totalRowCount;      // true count before capping, so "showing 20 of 61" is honest
}
