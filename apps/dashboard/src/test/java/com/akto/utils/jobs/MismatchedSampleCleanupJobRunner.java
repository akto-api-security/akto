package com.akto.utils.jobs;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.mongodb.ConnectionString;

/**
 * One-off manual runner for {@link MismatchedSampleCleanupJob}. Not a JUnit test on purpose (main
 * method, not @Test) so it is never picked up by the normal test suite; invoke it directly.
 *
 * Usage:
 *   java -cp <test-classpath> com.akto.utils.jobs.MismatchedSampleCleanupJobRunner <mongoUri> <accountId> <dryRun> [reportDir] [collectionId,collectionId,...]
 *
 * reportDir receives pass1_sensitive.tsv, pass2_sample_data.tsv, pass3_orphan_sti.tsv for review.
 */
public class MismatchedSampleCleanupJobRunner {
    public static void main(String[] args) throws Exception {
        String mongoUri = args.length > 0 ? args[0] : "mongodb://127.0.0.1:27017";
        if (args.length < 2) throw new IllegalArgumentException("usage: <mongoUri> <accountId> [dryRun] [reportDir] [collectionIds]");
        int accountId = Integer.parseInt(args[1]);
        boolean dryRun = args.length > 2 ? Boolean.parseBoolean(args[2]) : true;
        String reportDir = args.length > 3 ? args[3] : "mismatch-report-" + accountId;
        java.util.Set<Integer> only = new java.util.HashSet<>();
        if (args.length > 4 && !args[4].isEmpty()) {
            for (String c : args[4].split(",")) only.add(Integer.parseInt(c.trim()));
        }

        DaoInit.init(new ConnectionString(mongoUri));
        Context.accountId.set(accountId);

        System.out.println("Running MismatchedSampleCleanupJob for account " + accountId + " dryRun=" + dryRun + " reportDir=" + reportDir);
        MismatchedSampleCleanupJob.Report report = MismatchedSampleCleanupJob.run(dryRun, reportDir, only);
        System.out.println(report);
        System.out.flush();
        // Force exit: DaoInit / LoggerMaker leave behind non-daemon scheduled executor threads
        // that would otherwise keep this one-off runner's JVM alive indefinitely.
        System.exit(0);
    }
}
