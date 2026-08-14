package com.akto.testing;

final class InterruptAwareWait {

    private InterruptAwareWait() {}

    static boolean shouldAbortRetries() {
        return Thread.currentThread().isInterrupted();
    }

    static boolean sleepMs(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
