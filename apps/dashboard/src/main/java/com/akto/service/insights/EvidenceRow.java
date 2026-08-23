package com.akto.service.insights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceRow {
    private List<String> cellsFormatted;  // one per EvidenceTable.columns, display-ready
    private Map<String, Object> cellsRaw; // optional — structured values a CTA can bind params from
}
