package com.akto.service.ask;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * The AI layer over the overlay's computed tiles: which tiles to lead with, in order, and the
 * question each fires. Never carries numbers of its own — the frontend keeps rendering each tile's
 * computed value and only reorders tiles and swaps their prompt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AskTileCuration {

    public static final String STATUS_OK = "OK";
    /** Too few tiles worth ranking — nothing to curate, no model call made. */
    public static final String STATUS_SKIPPED = "SKIPPED";
    /** The model call failed or returned nothing usable; keep the computed order and prompts. */
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    private String status;
    private List<Pick> picks = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pick {
        /** Same id scheme the frontend builds tiles with — see AskTileCurationService.tileId. */
        private String tileId;
        private String prompt;
    }
}
