package com.akto.utils;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class OperationalAlertsTest {
    @Test public void suppressesDuplicatesButNotOtherAccountsAndResumesAfterCooldown() {
        AtomicLong clock = new AtomicLong(1000);
        List<String> messages = new ArrayList<>();
        OperationalAlerts alerts = new OperationalAlerts(Runnable::run, messages::add, clock::get, 300);
        alerts.submit("account-a:topic", "first");
        alerts.submit("account-a:topic", "duplicate");
        alerts.submit("account-b:topic", "other account");
        assertEquals(2, messages.size());
        clock.addAndGet(300);
        alerts.submit("account-a:topic", "reminder");
        assertEquals(3, messages.size());
    }

    @Test public void defersNetworkWorkAndContainsSenderFailures() {
        List<Runnable> tasks = new ArrayList<>();
        OperationalAlerts alerts = new OperationalAlerts(tasks::add,
                message -> { throw new IllegalStateException("unavailable"); }, () -> 1000, 300);
        alerts.submit("key", "message");
        assertEquals(1, tasks.size());
        tasks.get(0).run(); // Delivery failure must not escape.
    }

    @Test public void queueSaturationDoesNotEscapeToRequestThread() {
        OperationalAlerts alerts = new OperationalAlerts(task -> { throw new RejectedExecutionException(); },
                message -> fail("Must not send"), () -> 1000, 300);
        alerts.submit("key", "message");
    }
}
