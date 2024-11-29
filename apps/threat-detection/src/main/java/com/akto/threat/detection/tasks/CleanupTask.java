package com.akto.threat.detection.tasks;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.threat.detection.db.malicious_event.MaliciousEventDao;

public class CleanupTask implements Task {

    private final MaliciousEventDao maliciousEventDao;
    private final ScheduledExecutorService cronExecutorService = Executors.newScheduledThreadPool(1);

    public CleanupTask(Connection conn) {
        this.maliciousEventDao = new MaliciousEventDao(conn);
    }

    @Override
    public void run() {
        this.cronExecutorService.scheduleAtFixedRate(this::cleanup, 5, 10 * 60, TimeUnit.SECONDS);
    }

    private void cleanup() {
        // Delete all records older than 7 days
        try {
            this.maliciousEventDao.deleteEventsBefore(LocalDate.now(ZoneOffset.UTC).minusDays(7));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
