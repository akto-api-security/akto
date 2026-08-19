package com.akto.testing.temporal;

/** Tally of test outcomes. Thread-safe increments (tests run on an internal pool). */
public class TestOutcomes {
    public int passed, failed, errored, timedOut, skipped;

    public TestOutcomes() {}

    public synchronized void record(String outcome) {
        switch (outcome) {
            case "passed": passed++; break;
            case "failed": failed++; break;
            case "errored": errored++; break;
            case "timedOut": timedOut++; break;
            default: skipped++; break;
        }
    }

    public synchronized void add(TestOutcomes o) {
        passed += o.passed; failed += o.failed; errored += o.errored;
        timedOut += o.timedOut; skipped += o.skipped;
    }

    public int total() { return passed + failed + errored + timedOut + skipped; }

    @Override public String toString() {
        return "{passed=" + passed + ", failed=" + failed + ", errored=" + errored +
               ", timedOut=" + timedOut + ", skipped=" + skipped + "}";
    }
}
