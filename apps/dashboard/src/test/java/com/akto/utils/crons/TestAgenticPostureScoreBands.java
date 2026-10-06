package com.akto.utils.crons;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.utils.crons.AgenticPostureScoreCron.guardrailSeverityScore;
import static com.akto.utils.crons.AgenticPostureScoreCron.redTeamSeverityScore;
import static com.akto.utils.crons.AgenticPostureScoreCron.worstSliceMean;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestAgenticPostureScoreBands {

    private static final double DELTA = 0.05;

    private static Map<String, Integer> counts(Object... pairs) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], (Integer) pairs[i + 1]);
        return out;
    }

    private static List<Double> scores(double... v) {
        List<Double> out = new ArrayList<>();
        for (double x : v) out.add(x);
        return out;
    }

    @Test
    public void worstSliceIsTheMeanOfTheTopTenPercent() {
        List<Double> hundred = new ArrayList<>();
        for (int i = 1; i <= 100; i++) hundred.add((double) i);
        assertEquals("top 10 of 100 -> mean(100..91)", 95.5, worstSliceMean(hundred), DELTA);

        List<Double> twenty = new ArrayList<>();
        for (int i = 1; i <= 20; i++) twenty.add((double) i);
        assertEquals("10% of 20 is 2, floor of 5 applies -> mean(20..16)", 18.0, worstSliceMean(twenty), DELTA);
    }

    @Test
    public void worstSliceNeverUsesFewerThanFiveSlots() {
        assertEquals("1 bad agent among 9 clean -> diluted over 5 slots",
                (90 + 10 + 10 + 10 + 10) / 5.0,
                worstSliceMean(scores(90, 10, 10, 10, 10, 10, 10, 10, 10, 10)), DELTA);
    }

    @Test
    public void oneBadAgentReadsTheSameWhateverTheFleetSize() {
        double ten = worstSliceMean(scores(90, 10, 10, 10, 10, 10, 10, 10, 10, 10));
        List<Double> fifty = scores(90);
        for (int i = 0; i < 49; i++) fifty.add(10.0);
        assertEquals("1 bad among 9 clean == 1 bad among 49 clean", ten, worstSliceMean(fifty), DELTA);
    }

    @Test
    public void noCliffAtTheSlotBoundary() {
        double ten = worstSliceMean(scores(90, 60, 60, 60, 60, 10, 10, 10, 10, 10));
        double eleven = worstSliceMean(scores(90, 60, 60, 60, 60, 10, 10, 10, 10, 10, 10));
        assertEquals("adding one clean agent must not jump the score", ten, eleven, DELTA);
    }

    @Test
    public void moreBadAgentsRaiseTheScore() {
        double one = worstSliceMean(scores(90, 10, 10, 10, 10, 10, 10, 10, 10, 10));
        double five = worstSliceMean(scores(90, 60, 60, 60, 60, 10, 10, 10, 10, 10));
        assertTrue("five bad agents must score above one", five > one);
    }

    @Test
    public void addingCleanAgentsNeverRaisesTheScore() {
        List<Double> before = scores(90, 60, 60, 60, 60, 10, 10, 10, 10, 10);
        List<Double> after = new ArrayList<>(before);
        for (int i = 0; i < 50; i++) after.add(10.0);
        assertTrue("discovery must not improve the score", worstSliceMean(after) <= worstSliceMean(before));
    }

    @Test
    public void unassessedAgentsAreExcluded() {
        assertEquals("zeros are dropped, not averaged in",
                worstSliceMean(scores(90, 80, 70, 60, 50)),
                worstSliceMean(scores(90, 80, 70, 60, 50, 0, 0, 0, 0, 0)), DELTA);
    }

    @Test
    public void worstSliceOfNothingIsZero() {
        assertEquals(0.0, worstSliceMean(new ArrayList<>()), DELTA);
        assertEquals(0.0, worstSliceMean(scores(0, 0, 0)), DELTA);
    }

    @Test
    public void worstSliceWithFewerAgentsThanSlots() {
        assertEquals("3 agents -> mean of all 3", 60.0, worstSliceMean(scores(90, 60, 30)), DELTA);
        assertEquals("1 agent -> itself", 90.0, worstSliceMean(scores(90)), DELTA);
    }

    @Test
    public void redTeamBandFloorsAndCeilings() {
        assertEquals(96.8, redTeamSeverityScore(counts("CRITICAL", 1)), DELTA);
        assertEquals(100.0, redTeamSeverityScore(counts("CRITICAL", 5)), DELTA);
        assertEquals(79.8, redTeamSeverityScore(counts("HIGH", 1)), DELTA);
        assertEquals(95.0, redTeamSeverityScore(counts("HIGH", 5)), DELTA);
        assertEquals(55.8, redTeamSeverityScore(counts("MEDIUM", 1)), DELTA);
        assertEquals(75.0, redTeamSeverityScore(counts("MEDIUM", 5)), DELTA);
        assertEquals(30.0, redTeamSeverityScore(counts("LOW", 1)), DELTA);
        assertEquals(50.0, redTeamSeverityScore(counts("LOW", 5)), DELTA);
    }

    @Test
    public void redTeamVolumeNeverCrossesABand() {
        assertTrue(redTeamSeverityScore(counts("LOW", 5)) < redTeamSeverityScore(counts("MEDIUM", 1)));
        assertTrue(redTeamSeverityScore(counts("MEDIUM", 5)) < redTeamSeverityScore(counts("HIGH", 1)));
        assertTrue(redTeamSeverityScore(counts("HIGH", 5)) < redTeamSeverityScore(counts("CRITICAL", 1)));
        assertTrue(redTeamSeverityScore(counts("LOW", 10_000)) < redTeamSeverityScore(counts("MEDIUM", 1)));
    }

    @Test
    public void redTeamSaturatesAtFive() {
        assertEquals(redTeamSeverityScore(counts("CRITICAL", 5)),
                redTeamSeverityScore(counts("CRITICAL", 500)), DELTA);
    }

    @Test
    public void redTeamScoresOnWorstSeverityCountOnly() {
        assertEquals(redTeamSeverityScore(counts("CRITICAL", 3)),
                redTeamSeverityScore(counts("CRITICAL", 3, "LOW", 50_000)), DELTA);
        assertEquals(98.4, redTeamSeverityScore(counts("CRITICAL", 3, "LOW", 50_000)), DELTA);
    }

    @Test
    public void guardrailCriticalIsFlatHundred() {
        assertEquals(100.0, guardrailSeverityScore(counts("CRITICAL", 1)), DELTA);
        assertEquals(100.0, guardrailSeverityScore(counts("CRITICAL", 900)), DELTA);
    }

    @Test
    public void guardrailBandFloorsAndCeilings() {
        assertEquals(80.1, guardrailSeverityScore(counts("HIGH", 1)), DELTA);
        assertEquals(90.0, guardrailSeverityScore(counts("HIGH", 10)), DELTA);
        assertEquals(99.0, guardrailSeverityScore(counts("HIGH", 50)), DELTA);
        assertEquals(55.2, guardrailSeverityScore(counts("MEDIUM", 1)), DELTA);
        assertEquals(75.0, guardrailSeverityScore(counts("MEDIUM", 50)), DELTA);
        assertEquals(29.4, guardrailSeverityScore(counts("LOW", 1)), DELTA);
        assertEquals(50.0, guardrailSeverityScore(counts("LOW", 50)), DELTA);
    }

    @Test
    public void guardrailVolumeNeverCrossesABand() {
        assertTrue(guardrailSeverityScore(counts("LOW", 50)) < guardrailSeverityScore(counts("MEDIUM", 1)));
        assertTrue(guardrailSeverityScore(counts("MEDIUM", 50)) < guardrailSeverityScore(counts("HIGH", 1)));
        assertTrue(guardrailSeverityScore(counts("HIGH", 50)) < guardrailSeverityScore(counts("CRITICAL", 1)));
        assertTrue(guardrailSeverityScore(counts("HIGH", 100_000)) < guardrailSeverityScore(counts("CRITICAL", 1)));
    }

    @Test
    public void guardrailSaturatesAtFifty() {
        assertEquals(guardrailSeverityScore(counts("HIGH", 50)),
                guardrailSeverityScore(counts("HIGH", 5_000)), DELTA);
    }

    @Test
    public void noSignalScoresZero() {
        assertEquals(0.0, redTeamSeverityScore(null), DELTA);
        assertEquals(0.0, guardrailSeverityScore(null), DELTA);
        assertEquals(0.0, redTeamSeverityScore(counts()), DELTA);
        assertEquals(0.0, guardrailSeverityScore(counts()), DELTA);
        assertEquals(0.0, redTeamSeverityScore(counts("CRITICAL", 0)), DELTA);
        assertEquals(0.0, guardrailSeverityScore(counts("HIGH", 0)), DELTA);
    }

    @Test
    public void unrecognisedSeverityScoresZero() {
        assertEquals(0.0, redTeamSeverityScore(counts("UNKNOWN", 5)), DELTA);
        assertEquals(0.0, guardrailSeverityScore(counts("UNKNOWN", 5)), DELTA);
    }
}
