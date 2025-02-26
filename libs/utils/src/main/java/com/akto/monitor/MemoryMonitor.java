package com.akto.monitor;

public class MemoryMonitor {

    // Runnable task for monitoring memory
    public static class MemoryMonitorTask implements Runnable {
        @Override
        public void run() {
            Runtime runtime = Runtime.getRuntime();

            // Loop to print memory usage every 1 second
            while (true) {
                // Calculate memory statistics
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                long usedMemory = totalMemory - freeMemory;

                // Print memory statistics
                System.out.print("Used Memory: " + (usedMemory / 1024 / 1024) + " MB ");
                System.out.print("Free Memory: " + (freeMemory / 1024 / 1024) + " MB ");
                System.out.print("Total Memory: " + (totalMemory / 1024 / 1024) + " MB ");
                System.out.print("Available Memory: " + ((runtime.maxMemory() - usedMemory) / 1024 / 1024) + " MB ");
                System.out.println("-------------------------");

                // Pause for 1 second
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    System.err.println("Memory monitor thread interrupted: " + e.getMessage());
                    break; // Exit the loop if thread is interrupted
                }
            }
        }
    }

}
