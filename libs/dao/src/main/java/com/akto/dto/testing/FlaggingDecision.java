package com.akto.dto.testing;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Structured explanation of why a red-team turn was marked vulnerable.
 * <p>
 * Optional agent {@code /chat} JSON (alongside {@code validation}, {@code validationMessage}):
 * <ul>
 *   <li>Flat: {@code flaggingSummary}, {@code flaggingEvidence} (array of
 *       {@code source}, {@code excerpt}, optional {@code start}/{@code end}),
 *       {@code flaggingCriteria} (array of {@code id}, {@code name}, {@code met})</li>
 *   <li>Nested: {@code flaggingDecision: { summary, evidence, criteria }}</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlaggingDecision {

    private String summary;
    private List<FlaggingEvidenceItem> evidence;
    private List<FlaggingCriterion> criteria;
}
