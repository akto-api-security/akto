package com.akto.dto.redteam_learnings;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Judged conversations and how they split by verdict; critical + attempted + normal = judged. */
@Getter
@Setter
@NoArgsConstructor
public class VerdictCounts {

    private int judged;
    /** Verdict "2". */
    private int critical;
    /** Verdict "1". */
    private int attempted;
    /** Verdict "0". */
    private int normal;
}
